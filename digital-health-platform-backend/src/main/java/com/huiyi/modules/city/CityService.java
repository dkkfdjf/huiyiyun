package com.huiyi.modules.city;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.modules.system.entity.City;
import com.huiyi.modules.system.entity.MedicalInstitution;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.entity.SalesRecord;
import com.huiyi.modules.system.mapper.CityMapper;
import com.huiyi.modules.system.mapper.MedicalInstitutionMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import com.huiyi.modules.system.mapper.SalesRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 城市基础数据管理(全局,无行级隔离)。机构 / 网点以城市为外键。
 */
@Service
@RequiredArgsConstructor
public class CityService {

    private final CityMapper cityMapper;
    private final MedicalInstitutionMapper instMapper;
    private final SalesLocationMapper locationMapper;
    private final SalesRecordMapper salesMapper;

    /** 全部城市(下拉用),按 id 升序。 */
    public List<City> list() {
        return cityMapper.selectList(new LambdaQueryWrapper<City>().orderByAsc(City::getId));
    }

    /**
     * 城市覆盖看板:每城市的 网点数 / 机构数 / 覆盖药企数 / 累计销售额。
     * sales_record 无城市字段,经 sales_location.city_id 桥接汇总。
     * 全量内存聚合(基础数据量小);不缓存,保证看板始终最新。
     */
    public List<CityCoverageVO> coverage() {
        List<City> cities = cityMapper.selectList(new LambdaQueryWrapper<City>().orderByAsc(City::getId));
        List<SalesLocation> locs = locationMapper.selectList(null);
        List<MedicalInstitution> insts = instMapper.selectList(null);
        List<SalesRecord> sales = salesMapper.selectList(null);

        Map<Long, Long> locCnt = locs.stream()
                .collect(Collectors.groupingBy(SalesLocation::getCityId, Collectors.counting()));
        Map<Long, Long> instCnt = insts.stream()
                .collect(Collectors.groupingBy(MedicalInstitution::getCityId, Collectors.counting()));
        Map<Long, Set<Long>> compByCity = locs.stream()
                .collect(Collectors.groupingBy(SalesLocation::getCityId,
                        Collectors.mapping(SalesLocation::getCompanyId, Collectors.toSet())));
        Map<Long, Long> locToCity = locs.stream()
                .collect(Collectors.toMap(SalesLocation::getId, SalesLocation::getCityId));
        Map<Long, BigDecimal> amtByCity = new HashMap<>();
        for (SalesRecord s : sales) {
            Long city = locToCity.get(s.getLocationId());
            if (city != null && s.getAmount() != null)
                amtByCity.merge(city, s.getAmount(), BigDecimal::add);
        }

        List<CityCoverageVO> out = new ArrayList<>();
        for (City c : cities) {
            out.add(new CityCoverageVO(c.getId(), c.getName(), c.getProvince(), c.getRegionCode(),
                    locCnt.getOrDefault(c.getId(), 0L),
                    instCnt.getOrDefault(c.getId(), 0L),
                    compByCity.getOrDefault(c.getId(), Set.of()).size(),
                    amtByCity.getOrDefault(c.getId(), BigDecimal.ZERO)));
        }
        return out;
    }

    public Long save(CitySaveDTO dto) {
        City c = new City();
        applyFields(c, dto);
        cityMapper.insert(c);
        return c.getId();
    }

    public void update(Long id, CitySaveDTO dto) {
        City c = requireExists(id);
        applyFields(c, dto);
        cityMapper.updateById(c);
    }

    /** 删除:被机构或网点引用时阻止(防孤儿数据)。 */
    public void delete(Long id) {
        requireExists(id);
        long refInst = instMapper.selectCount(new LambdaQueryWrapper<MedicalInstitution>().eq(MedicalInstitution::getCityId, id));
        long refLoc = locationMapper.selectCount(new LambdaQueryWrapper<SalesLocation>().eq(SalesLocation::getCityId, id));
        if (refInst + refLoc > 0)
            throw new BusinessException(ResultCode.REFERENCE_CONFLICT.getCode(), "该城市下存在机构或网点,无法删除");
        cityMapper.deleteById(id);
    }

    private void applyFields(City c, CitySaveDTO dto) {
        c.setName(dto.getName().trim());
        c.setProvince(dto.getProvince().trim());
        c.setRegionCode(dto.getRegionCode());
    }

    private City requireExists(Long id) {
        City c = cityMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return c;
    }
}
