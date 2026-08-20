package com.huiyi.modules.location;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.City;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.mapper.CityMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 销售网点(M6):完整 CRUD + 分页,复用既有只读 list() 供铺货下拉与地图。
 * 行级隔离:管理员(role==0)全局,其余仅本公司网点。
 * companyId(schema NOT NULL):company 用户固定本公司;admin 必须显式传 companyId。
 * 城市:由网点录入自动派生(resolveCityId:优先 cityId,否则按 cityName 查/建)。
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final SalesLocationMapper locationMapper;
    private final CityMapper cityMapper;

    /** 本企业/全局网点列表(铺货下拉与地图复用)。 */
    public List<SalesLocation> list() {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<SalesLocation> w = new LambdaQueryWrapper<>();
        // 游客(无 companyId)与管理员看全部网点;仅药企按本公司过滤——否则游客 companyId=null 会让 eq 命中 0 行,网点页空白
        if (u.getRole() != RoleConstants.ADMIN && u.getRole() != RoleConstants.GUEST) w.eq(SalesLocation::getCompanyId, u.getCompanyId());
        w.orderByDesc(SalesLocation::getUpdateTime);
        return locationMapper.selectList(w);
    }

    /** 分页查询:支持名称模糊与城市筛选。 */
    public PageResult<SalesLocation> page(String name, Long cityId, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<SalesLocation> w = new LambdaQueryWrapper<>();
        // 游客(无 companyId)与管理员看全部网点;仅药企按本公司过滤——否则游客 companyId=null 会让 eq 命中 0 行,网点页空白
        if (u.getRole() != RoleConstants.ADMIN && u.getRole() != RoleConstants.GUEST) w.eq(SalesLocation::getCompanyId, u.getCompanyId());
        if (name != null && !name.isBlank()) w.like(SalesLocation::getName, name.trim());
        if (cityId != null) w.eq(SalesLocation::getCityId, cityId);
        w.orderByDesc(SalesLocation::getUpdateTime);
        Page<SalesLocation> p = locationMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 新增:company 固定本公司,admin 必须传 companyId。 */
    public Long save(LocationSaveDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        Long companyId = u.getRole() == RoleConstants.ADMIN ? dto.getCompanyId() : u.getCompanyId();
        if (companyId == null) throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "请选择所属企业");

        SalesLocation l = new SalesLocation();
        l.setName(dto.getName());
        l.setAddress(dto.getAddress());
        l.setCityId(resolveCityId(dto));
        l.setCompanyId(companyId);
        l.setLongitude(dto.getLongitude());
        l.setLatitude(dto.getLatitude());
        l.setContactPerson(dto.getContactPerson());
        l.setContactPhone(dto.getContactPhone());
        locationMapper.insert(l);
        return l.getId();
    }

    /** 修改:归属校验后覆写字段。 */
    public void update(Long id, LocationSaveDTO dto) {
        SalesLocation l = requireOwned(id);
        l.setName(dto.getName());
        l.setAddress(dto.getAddress());
        l.setCityId(resolveCityId(dto));
        l.setLongitude(dto.getLongitude());
        l.setLatitude(dto.getLatitude());
        l.setContactPerson(dto.getContactPerson());
        l.setContactPhone(dto.getContactPhone());
        locationMapper.updateById(l);
    }

    /** 软删(@TableLogic)。 */
    public void delete(Long id) {
        requireOwned(id);
        locationMapper.deleteById(id);
    }

    /**
     * 解析城市 id:优先用 dto.cityId(已知/编辑场景);否则按 cityName 查询,不存在则自动建档。
     * 新城市建档:province 必填(前端网点表单已带省份下拉),杜绝管理员城市页省份为空(#164);
     * 已存在但 province 缺失的历史城市:本次录入若带 province 则自愈补全。城市/省份仍可由管理员在城市管理页维护。
     */
    private Long resolveCityId(LocationSaveDTO dto) {
        if (dto.getCityId() != null) return dto.getCityId();
        String name = dto.getCityName() == null ? "" : dto.getCityName().trim();
        if (name.isEmpty()) throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "请选择或输入城市");
        City c = cityMapper.selectOne(new LambdaQueryWrapper<City>().eq(City::getName, name).last("limit 1"));
        if (c == null) {
            String province = dto.getProvince() == null ? "" : dto.getProvince().trim();
            if (province.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "新城市请选择省份");
            }
            c = new City();
            c.setName(name);
            c.setProvince(province);
            cityMapper.insert(c);
        } else if ((c.getProvince() == null || c.getProvince().isBlank())
                && dto.getProvince() != null && !dto.getProvince().trim().isEmpty()) {
            // 数据自愈:历史城市建档时省份缺失,本次录入补上 → 管理员城市页省份不再为空
            c.setProvince(dto.getProvince().trim());
            cityMapper.updateById(c);
        }
        return c.getId();
    }

    /** 取出并校验归属:管理员放行,公司仅限本公司;不存在→404,越权→4004。 */
    private SalesLocation requireOwned(Long id) {
        SalesLocation l = locationMapper.selectById(id);
        if (l == null) throw new BusinessException(ResultCode.NOT_FOUND);
        CurrentUser u = SecurityContextHolder.get();
        if (u.getRole() != RoleConstants.ADMIN && !u.getCompanyId().equals(l.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        return l;
    }
}
