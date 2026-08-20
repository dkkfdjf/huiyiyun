package com.huiyi.modules.drug;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.DrugStock;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.DrugStockMapper;
import com.huiyi.modules.system.entity.ReplenishmentOrder;
import com.huiyi.modules.system.mapper.ReplenishmentOrderMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.policy.CompanyPolicySaveDTO;
import com.huiyi.modules.policy.CompanyPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrugStockService {

    private final DrugStockMapper stockMapper;
    private final DrugMapper drugMapper;
    private final SalesLocationMapper locationMapper;
    private final ReplenishmentOrderMapper replenishOrderMapper;
    private final PharmaCompanyMapper companyMapper;
    private final NotificationService notificationService;
    private final CompanyPolicyService companyPolicyService;   // 价格变动 → 自动发布"价格"类药企公告

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 铺货初始化(UC-A04-1):校验药品归属+上架、地点归属、drug×location 防重。 */
    public Long init(StockInitDTO dto) {
        CurrentUser u = SecurityContextHolder.get();

        Drug drug = drugMapper.selectById(dto.getDrugId());
        if (drug == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (u.getRole() != RoleConstants.ADMIN && !u.getCompanyId().equals(drug.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        if (drug.getStatus() == null || drug.getStatus() != 1)
            throw new BusinessException(ResultCode.DRUG_OFF_SHELF);

        SalesLocation loc = locationMapper.selectById(dto.getLocationId());
        if (loc == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (u.getRole() != RoleConstants.ADMIN && !u.getCompanyId().equals(loc.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);

        Long exist = stockMapper.selectCount(new LambdaQueryWrapper<DrugStock>()
                .eq(DrugStock::getDrugId, dto.getDrugId())
                .eq(DrugStock::getLocationId, dto.getLocationId()));
        if (exist > 0) throw new BusinessException(ResultCode.DRUG_STOCK_DUP);

        DrugStock s = new DrugStock();
        s.setDrugId(dto.getDrugId());
        s.setLocationId(dto.getLocationId());
        s.setCompanyId(u.getRole() == RoleConstants.ADMIN ? drug.getCompanyId() : u.getCompanyId());
        s.setStockQty(dto.getStockQty());
        s.setPrice(dto.getPrice());
        s.setThreshold(dto.getThreshold() == null ? 0 : dto.getThreshold());
        s.setVersion(0);
        stockMapper.insert(s);

        // 铺货即首笔入库:补一条 replenishment_order,使进销存台账可平。
        // 否则台账 totalIn 只含后续补货、缺初始铺货量 → 入库−出库 永远 ≠ 当前库存。
        ReplenishmentOrder opening = new ReplenishmentOrder();
        opening.setOrderNo("REP" + NO_FMT.format(LocalDateTime.now())
                + String.format("%02d", ThreadLocalRandom.current().nextInt(100)));
        opening.setLocationId(s.getLocationId());
        opening.setDrugId(s.getDrugId());
        opening.setCompanyId(s.getCompanyId());
        opening.setQty(dto.getStockQty());
        opening.setInTime(LocalDateTime.now());
        opening.setRemark("铺货初始化");
        opening.setCreateBy(u.getUsername());
        opening.setCreateTime(LocalDateTime.now());
        replenishOrderMapper.insert(opening);

        return s.getId();
    }

    /** 库存分页查询(批量补药品/网点名,防 N+1)。 */
    public PageResult<StockVO> page(Long drugId, Long locationId, Long companyId, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<DrugStock> w = new LambdaQueryWrapper<>();
        if (u.getRole() != RoleConstants.ADMIN) w.eq(DrugStock::getCompanyId, u.getCompanyId());
        if (drugId != null) w.eq(DrugStock::getDrugId, drugId);
        if (locationId != null) w.eq(DrugStock::getLocationId, locationId);
        // 管理员按药企筛选;非管理员已被上面本企业条件锁死,这里再加 companyId 不会越权
        if (companyId != null) w.eq(DrugStock::getCompanyId, companyId);
        w.orderByDesc(DrugStock::getUpdateTime);
        Page<DrugStock> p = stockMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(toVO(p.getRecords()), p.getTotal(), pageNum, pageSize);
    }

    /** 库存预警列表(派生态:stock_qty <= threshold)。 */
    public List<StockVO> alerts() {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<DrugStock> w = new LambdaQueryWrapper<>();
        if (u.getRole() != RoleConstants.ADMIN) w.eq(DrugStock::getCompanyId, u.getCompanyId());
        w.apply("stock_qty <= threshold").orderByAsc(DrugStock::getStockQty);
        return toVO(stockMapper.selectList(w));
    }

    /**
     * 管理员提醒补货:给该库存归属药企发一条站内催办通知。
     * 与出库时自动触发的 notifyLowStock 互补——覆盖"种子即低库/未经过运行时出库"的情形,
     * 也用于管理员显式 escalation。显式动作,每次调用即发(不自动去重);前端按钮点击后置 loading 防连点。
     */
    public void remindReplenish(Long stockId) {
        DrugStock s = stockMapper.selectById(stockId);
        if (s == null) throw new BusinessException(ResultCode.STOCK_NOT_FOUND);
        if (s.getCompanyId() == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "该库存未归属药企,无法提醒补货");
        Drug drug = drugMapper.selectById(s.getDrugId());
        String name = drug == null ? "药品" : drug.getName();
        int qty = s.getStockQty() == null ? 0 : s.getStockQty();
        int th = s.getThreshold() == null ? 0 : s.getThreshold();
        notificationService.notifyCompany(s.getCompanyId(), NotificationService.CAT_STOCK,
                "管理员提醒补货:" + name,
                "管理员提醒:当前库存 " + qty + " 已低于安全线 " + th + ",请尽快安排补货。",
                NotificationService.REF_STOCK, stockId);
    }

    /** 维护售价/阈值(@Version 乐观锁,版本过期提示刷新)。库存变更走出库/补货,不在此改。 */
    public void update(StockUpdateDTO dto) {
        DrugStock s = stockMapper.selectById(dto.getId());
        if (s == null) throw new BusinessException(ResultCode.STOCK_NOT_FOUND);
        CurrentUser u = SecurityContextHolder.get();
        if (u.getRole() != RoleConstants.ADMIN && !u.getCompanyId().equals(s.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);

        s.setVersion(dto.getVersion());                 // 用客户端读取的版本做乐观锁
        BigDecimal oldPrice = s.getPrice();
        boolean priceChanged = dto.getPrice() != null && oldPrice != null
                && dto.getPrice().compareTo(oldPrice) != 0;
        if (dto.getPrice() != null) s.setPrice(dto.getPrice());
        if (dto.getThreshold() != null) s.setThreshold(dto.getThreshold());
        int rows = stockMapper.updateById(s);
        if (rows == 0) throw new BusinessException(
                ResultCode.PARAM_INVALID.getCode(), "库存数据已被他人修改,请刷新后重试");
        // 价格变动 → 自动发布"价格"类药企公告(save 会同时广播通知全部医疗机构),药企无需手发
        if (priceChanged) {
            try {
                Drug d = drugMapper.selectById(s.getDrugId());
                String dn = (d != null && d.getName() != null) ? d.getName() : "药品#" + s.getDrugId();
                CompanyPolicySaveDTO p = new CompanyPolicySaveDTO();
                p.setPolicyType(3);   // 价格
                p.setTitle(dn + " 挂牌价调整");
                p.setContent("挂牌价由 ¥" + oldPrice + " 调整为 ¥" + s.getPrice() + ",即时生效。");
                p.setEffectiveDate(LocalDate.now());
                companyPolicyService.save(p);
            } catch (Exception ignore) { /* 公告最佳努力,不阻断改价 */ }
        }
    }

    /** DrugStock → StockVO,批量取药品/网点名(2 次额外查询,非 N+1)。 */
    private List<StockVO> toVO(List<DrugStock> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Set<Long> drugIds = list.stream().map(DrugStock::getDrugId).collect(Collectors.toSet());
        Set<Long> locIds = list.stream().map(DrugStock::getLocationId).collect(Collectors.toSet());
        Map<Long, String> drugName = drugMapper.selectBatchIds(drugIds).stream()
                .collect(Collectors.toMap(Drug::getId, Drug::getName));
        Map<Long, String> locName = locationMapper.selectBatchIds(locIds).stream()
                .collect(Collectors.toMap(SalesLocation::getId, SalesLocation::getName));
        Set<Long> coIds = list.stream().map(DrugStock::getCompanyId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> coName = coIds.isEmpty() ? Collections.emptyMap()
                : companyMapper.selectBatchIds(coIds).stream()
                    .collect(Collectors.toMap(PharmaCompany::getId, PharmaCompany::getName, (a, b) -> a));
        return list.stream().map(s -> {
            StockVO v = new StockVO();
            v.setId(s.getId());
            v.setDrugId(s.getDrugId());
            v.setDrugName(drugName.get(s.getDrugId()));
            v.setLocationId(s.getLocationId());
            v.setLocationName(locName.get(s.getLocationId()));
            v.setCompanyId(s.getCompanyId());
            v.setCompanyName(coName.get(s.getCompanyId()));
            v.setStockQty(s.getStockQty());
            v.setPrice(s.getPrice());
            v.setThreshold(s.getThreshold());
            v.setVersion(s.getVersion());
            int th = s.getThreshold() == null ? 0 : s.getThreshold();
            v.setAlert(s.getStockQty() != null && s.getStockQty() <= th);
            return v;
        }).collect(Collectors.toList());
    }
}
