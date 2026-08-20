package com.huiyi.modules.inventory;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import com.huiyi.modules.system.entity.DrugStock;
import com.huiyi.modules.system.entity.ReplenishmentOrder;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.entity.SalesRecord;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.DrugStockMapper;
import com.huiyi.modules.system.mapper.ReplenishmentOrderMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import com.huiyi.modules.system.mapper.SalesRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final DrugStockMapper stockMapper;
    private final SalesRecordMapper salesMapper;
    private final ReplenishmentOrderMapper replenishMapper;
    private final DrugMapper drugMapper;
    private final SalesLocationMapper locationMapper;
    private final PharmaCompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 销售/出库(防超卖,UC-A04-2)。
     * 事务内:校验归属与上架 → 条件 UPDATE 扣减 → 写 append-only 流水。
     * 防超卖关键:deductStock 的 WHERE stock_qty>=qty 由 InnoDB 行锁串行化并发,
     * 库存不足即 rows=0 抛 STOCK_NOT_ENOUGH,事务整体回滚(流水不写)。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long sell(SaleDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        DrugStock s = locateStock(dto.getDrugId(), dto.getLocationId(), u);

        Drug drug = drugMapper.selectById(s.getDrugId());
        if (drug == null || drug.getStatus() == null || drug.getStatus() != 1)
            throw new BusinessException(ResultCode.DRUG_OFF_SHELF);

        int rows = stockMapper.deductStock(s.getId(), dto.getQty());
        if (rows == 0) throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);

        // 售价取库存挂牌价(需求:amount = 数量 × 单价,单价只读)。
        // 客户端传入的 price 仅供前端预填展示,服务端一律忽略,杜绝低价做账。
        BigDecimal price = s.getPrice();
        LocalDateTime now = LocalDateTime.now();
        SalesRecord rec = new SalesRecord();
        rec.setRecordNo(nextNo("SAL"));
        rec.setLocationId(s.getLocationId());
        rec.setDrugId(s.getDrugId());
        rec.setCompanyId(s.getCompanyId());
        rec.setQty(dto.getQty());
        rec.setPrice(price);
        rec.setAmount(price.multiply(BigDecimal.valueOf(dto.getQty())));
        rec.setSaleTime(now);
        rec.setRemark(dto.getRemark());
        rec.setCreateBy(u.getUsername());
        rec.setCreateTime(now);
        salesMapper.insert(rec);
        // 库存预警:重读扣减后库存,跨阈值且该公司无未读同库存通知(去重)时提醒本公司。最佳努力,不影响交易。
        DrugStock after = stockMapper.selectById(s.getId());
        if (after != null)
            notificationService.notifyLowStock(after.getCompanyId(), drug.getName(),
                    after.getStockQty() == null ? 0 : after.getStockQty(),
                    after.getThreshold() == null ? 0 : after.getThreshold(), after.getId());
        return rec.getId();
    }

    /**
     * 补货/入库(UC-A04-3):校验归属 → 加库存 → 写 append-only 流水。
     * 入库无库存上限,故无超卖风险,仅校验行存在与归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long replenish(ReplenishDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        DrugStock s = locateStock(dto.getDrugId(), dto.getLocationId(), u);

        // 守卫一致性:下架药品既不可售也不可补货(与 sell 对齐),杜绝向下架品继续堆库存。
        Drug drug = drugMapper.selectById(s.getDrugId());
        if (drug == null || drug.getStatus() == null || drug.getStatus() != 1)
            throw new BusinessException(ResultCode.DRUG_OFF_SHELF);

        int rows = stockMapper.addStock(s.getId(), dto.getQty());
        if (rows == 0) throw new BusinessException(ResultCode.STOCK_NOT_FOUND);

        LocalDateTime now = LocalDateTime.now();
        ReplenishmentOrder o = new ReplenishmentOrder();
        o.setOrderNo(nextNo("REP"));
        o.setLocationId(s.getLocationId());
        o.setDrugId(s.getDrugId());
        o.setCompanyId(s.getCompanyId());
        o.setQty(dto.getQty());
        o.setInTime(now);
        o.setRemark(dto.getRemark());
        o.setCreateBy(u.getUsername());
        o.setCreateTime(now);
        replenishMapper.insert(o);
        return o.getId();
    }

    /** 销售流水分页(批量补药品/网点名,防 N+1)。行级隔离:非管理员仅本企业。 */
    public PageResult<SalesRecordVO> pageSales(Long drugId, Long locationId, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<SalesRecord> w = new LambdaQueryWrapper<>();
        if (u.getRole() != RoleConstants.ADMIN) w.eq(SalesRecord::getCompanyId, u.getCompanyId());
        if (drugId != null) w.eq(SalesRecord::getDrugId, drugId);
        if (locationId != null) w.eq(SalesRecord::getLocationId, locationId);
        w.orderByDesc(SalesRecord::getSaleTime);
        Page<SalesRecord> p = salesMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(toSalesVO(p.getRecords()), p.getTotal(), pageNum, pageSize);
    }

    /** 补货流水分页。 */
    public PageResult<ReplenishOrderVO> pageReplenish(Long drugId, Long locationId, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<ReplenishmentOrder> w = new LambdaQueryWrapper<>();
        if (u.getRole() != RoleConstants.ADMIN) w.eq(ReplenishmentOrder::getCompanyId, u.getCompanyId());
        if (drugId != null) w.eq(ReplenishmentOrder::getDrugId, drugId);
        if (locationId != null) w.eq(ReplenishmentOrder::getLocationId, locationId);
        w.orderByDesc(ReplenishmentOrder::getInTime);
        Page<ReplenishmentOrder> p = replenishMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(toReplenishVO(p.getRecords()), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 进销存台账(药品×网点 对账汇总,设计 4.5 / 3.4.4第6点):以 drug_stock 为行维度(自带当前库存),
     * 批量补累计入库/累计销售,三值对账。行级隔离:非管理员仅本企业。
     * 不加 @Cacheable——行级隔离数据,与缓存阶段一范围一致(只缓存无租户隔离的全局参照数据)。
     */
    public PageResult<LedgerVO> ledger(Long drugId, Long locationId,
                                       LocalDateTime start, LocalDateTime end,
                                       int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        boolean isAdmin = u.getRole() == RoleConstants.ADMIN;

        // 1. 分页取库存行(药品×网点 维度 + 当前库存);非管理员限定本企业
        LambdaQueryWrapper<DrugStock> w = new LambdaQueryWrapper<>();
        if (!isAdmin) w.eq(DrugStock::getCompanyId, u.getCompanyId());
        if (drugId != null) w.eq(DrugStock::getDrugId, drugId);
        if (locationId != null) w.eq(DrugStock::getLocationId, locationId);
        w.orderByAsc(DrugStock::getDrugId).orderByAsc(DrugStock::getLocationId);
        Page<DrugStock> p = stockMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<DrugStock> rows = p.getRecords();
        if (rows == null || rows.isEmpty())
            return PageResult.of(Collections.emptyList(), p.getTotal(), pageNum, pageSize);

        // 2. 批量取累计入库/累计销售(按 drug×loc 分组;管理员不限公司)。固定两条 SQL,非 N+1。
        Long scopeCompany = isAdmin ? null : u.getCompanyId();
        Map<String, Integer> inMap = replenishMapper.sumByDrugLocation(scopeCompany, drugId, locationId, start, end)
                .stream().collect(Collectors.toMap(LedgerFlowVO::key, LedgerFlowVO::getQty, Integer::sum));
        Map<String, Integer> outMap = salesMapper.sumByDrugLocation(scopeCompany, drugId, locationId, start, end)
                .stream().collect(Collectors.toMap(LedgerFlowVO::key, LedgerFlowVO::getQty, Integer::sum));

        // 3. 批量补药品/网点名(防 N+1)
        Set<Long> drugIds = rows.stream().map(DrugStock::getDrugId).collect(Collectors.toSet());
        Set<Long> locIds = rows.stream().map(DrugStock::getLocationId).collect(Collectors.toSet());
        Map<Long, String> drugName = drugMapper.selectBatchIds(drugIds).stream()
                .collect(Collectors.toMap(Drug::getId, Drug::getName));
        Map<Long, String> locName = locationMapper.selectBatchIds(locIds).stream()
                .collect(Collectors.toMap(SalesLocation::getId, SalesLocation::getName));

        // 4. 组装对账:当前库存是时点余额,不受时间窗口影响;窗口仅约束累计入库/销售流量
        List<LedgerVO> vos = rows.stream().map(s -> {
            String k = LedgerFlowVO.key(s.getDrugId(), s.getLocationId());
            int totalIn = inMap.getOrDefault(k, 0);
            int totalOut = outMap.getOrDefault(k, 0);
            int stock = s.getStockQty() == null ? 0 : s.getStockQty();
            LedgerVO v = new LedgerVO();
            v.setDrugId(s.getDrugId());
            v.setDrugName(drugName.get(s.getDrugId()));
            v.setLocationId(s.getLocationId());
            v.setLocationName(locName.get(s.getLocationId()));
            v.setCompanyId(s.getCompanyId());
            v.setTotalIn(totalIn);
            v.setTotalOut(totalOut);
            v.setCurrentStock(s.getStockQty());
            v.setWindowDiff(totalIn - totalOut);
            v.setBalanced((totalIn - totalOut) == stock); // 仅查全历史(无 start/end)时具对账意义
            return v;
        }).collect(Collectors.toList());
        return PageResult.of(vos, p.getTotal(), pageNum, pageSize);
    }

    /**
     * 一键对账修正(以当前库存为准):给所有不平的 drug_stock 行补「历史维护对账修正」流水。
     * 差值 diff = stock_qty − (Σ入库 − Σ销售)。
     *  - diff>0(盘盈:库存多于流水)→ 补一条 replenishment_order,让 Σ入库 追上库存;
     *  - diff<0(盘亏:库存少于流水)→ 补
     *  一条 sales_record(按库存挂牌价记账),让 Σ销售 追上库存;
     *  一律不动 stock_qty(库存数字不变),仅补 append-only 流水使台账重新可平。
     * 幂等:diff==0 跳过;维护已禁止改库存,修正后永久可平。行级隔离:非管理员仅本企业。事务:全补或全不补。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReconcileVO reconcile() {
        CurrentUser u = SecurityContextHolder.get();
        boolean isAdmin = u.getRole() == RoleConstants.ADMIN;
        Long scopeCompany = isAdmin ? null : u.getCompanyId();

        LambdaQueryWrapper<DrugStock> w = new LambdaQueryWrapper<>();
        if (!isAdmin) w.eq(DrugStock::getCompanyId, u.getCompanyId());
        List<DrugStock> rows = stockMapper.selectList(w);
        ReconcileVO r = new ReconcileVO();
        if (rows == null || rows.isEmpty()) return r;

        // 全历史 Σ入库 / Σ销售(按 药品×网点 聚合,复用台账汇总;不带时间窗口=查全量)
        Map<String, Integer> inMap = replenishMapper.sumByDrugLocation(scopeCompany, null, null, null, null)
                .stream().collect(Collectors.toMap(LedgerFlowVO::key, LedgerFlowVO::getQty, Integer::sum));
        Map<String, Integer> outMap = salesMapper.sumByDrugLocation(scopeCompany, null, null, null, null)
                .stream().collect(Collectors.toMap(LedgerFlowVO::key, LedgerFlowVO::getQty, Integer::sum));

        // 批量取药品/网点名(通知文案用,2 次查询非 N+1)
        Set<Long> drugIds = rows.stream().map(DrugStock::getDrugId).collect(Collectors.toSet());
        Set<Long> locIds = rows.stream().map(DrugStock::getLocationId).collect(Collectors.toSet());
        Map<Long, String> drugName = drugMapper.selectBatchIds(drugIds).stream()
                .collect(Collectors.toMap(Drug::getId, Drug::getName));
        Map<Long, String> locName = locationMapper.selectBatchIds(locIds).stream()
                .collect(Collectors.toMap(SalesLocation::getId, SalesLocation::getName));

        LocalDateTime now = LocalDateTime.now();
        for (DrugStock s : rows) {
            String k = LedgerFlowVO.key(s.getDrugId(), s.getLocationId());
            int totalIn = inMap.getOrDefault(k, 0);
            int totalOut = outMap.getOrDefault(k, 0);
            int stock = s.getStockQty() == null ? 0 : s.getStockQty();
            int diff = stock - (totalIn - totalOut);
            if (diff == 0) { r.setSkipped(r.getSkipped() + 1); continue; }
            String dn = drugName.getOrDefault(s.getDrugId(), "药品#" + s.getDrugId());
            String ln = locName.get(s.getLocationId());
            String where = (ln == null || ln.isEmpty()) ? "" : "(" + ln + ")";
            if (diff > 0) {
                // 盘盈:补一笔入库,归属取库存行 companyId(管理员修正也落到正确企业)
                ReplenishmentOrder o = new ReplenishmentOrder();
                o.setOrderNo(nextNo("REP"));
                o.setLocationId(s.getLocationId());
                o.setDrugId(s.getDrugId());
                o.setCompanyId(s.getCompanyId());
                o.setQty(diff);
                o.setInTime(now);
                o.setRemark("历史维护对账修正·盘盈 +" + diff);
                o.setCreateBy(u.getUsername());
                o.setCreateTime(now);
                replenishMapper.insert(o);
                r.setSurplus(r.getSurplus() + 1);
                // 通知归属药企(最佳努力):已补入库流水,库存数字不变
                notificationService.notifyCompany(s.getCompanyId(), NotificationService.CAT_STOCK,
                        "库存对账修正:" + dn + " 盘盈",
                        "历史维护对账修正:" + dn + where + " 盘盈 +" + diff + " 件,已补入库流水,当前库存数字未变。",
                        NotificationService.REF_STOCK, s.getId());
            } else {
                // 盘亏:补一笔出库,按库存挂牌价记账(与 sell 的防低价做账口径一致)
                int qty = -diff;
                BigDecimal price = s.getPrice() == null ? BigDecimal.ZERO : s.getPrice();
                SalesRecord rec = new SalesRecord();
                rec.setRecordNo(nextNo("SAL"));
                rec.setLocationId(s.getLocationId());
                rec.setDrugId(s.getDrugId());
                rec.setCompanyId(s.getCompanyId());
                rec.setQty(qty);
                rec.setPrice(price);
                rec.setAmount(price.multiply(BigDecimal.valueOf(qty)));
                rec.setSaleTime(now);
                rec.setRemark("历史维护对账修正·盘亏 −" + qty);
                rec.setCreateBy(u.getUsername());
                rec.setCreateTime(now);
                salesMapper.insert(rec);
                r.setDeficit(r.getDeficit() + 1);
                // 通知归属药企(最佳努力):已补出库流水,库存数字不变
                notificationService.notifyCompany(s.getCompanyId(), NotificationService.CAT_STOCK,
                        "库存对账修正:" + dn + " 盘亏",
                        "历史维护对账修正:" + dn + where + " 盘亏 −" + qty + " 件,已补出库流水,当前库存数字未变。",
                        NotificationService.REF_STOCK, s.getId());
            }
        }
        // 管理员汇总留痕(最佳努力):谁、何时执行了对账修正,盘盈/盘亏若干;库存数字未变
        int fixed = r.getSurplus() + r.getDeficit();
        if (fixed > 0)
            notificationService.notifyAdmins(NotificationService.CAT_SYSTEM,
                    "库存对账修正完成",
                    actorLabel(u, isAdmin) + " 执行一键对账修正:盘盈 " + r.getSurplus()
                            + " 条、盘亏 " + r.getDeficit() + " 条、已平 " + r.getSkipped() + " 条;库存数字未变。",
                    null, null);
        return r;
    }

    /** 通知署名:药企执行显示「药企名 · 操作人真实姓名」;管理员显示「管理员 真实姓名」;realName 缺失退 username。 */
    private String actorLabel(CurrentUser u, boolean isAdmin) {
        User user = userMapper.selectById(u.getUserId());
        String rn = (user != null && user.getRealName() != null && !user.getRealName().isBlank())
                ? user.getRealName() : u.getUsername();
        if (isAdmin) return "管理员 " + rn;
        PharmaCompany co = u.getCompanyId() != null ? companyMapper.selectById(u.getCompanyId()) : null;
        return (co != null && co.getName() != null ? co.getName() : "药企") + " · " + rn;
    }

    /** 定位库存行并校验行级归属(管理员全局,否则本企业)。 */
    private DrugStock locateStock(Long drugId, Long locationId, CurrentUser u) {
        DrugStock s = stockMapper.selectOne(new LambdaQueryWrapper<DrugStock>()
                .eq(DrugStock::getDrugId, drugId)
                .eq(DrugStock::getLocationId, locationId));
        if (s == null) throw new BusinessException(ResultCode.STOCK_NOT_FOUND);
        if (u.getRole() != RoleConstants.ADMIN && !u.getCompanyId().equals(s.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        return s;
    }

    /** 业务单号:前缀 + 毫秒时间戳 + 2 位随机,满足 record_no/order_no 的 UNIQUE 约束。 */
    private String nextNo(String prefix) {
        return prefix + NO_FMT.format(LocalDateTime.now())
                + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
    }

    private List<SalesRecordVO> toSalesVO(List<SalesRecord> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Set<Long> drugIds = list.stream().map(SalesRecord::getDrugId).collect(Collectors.toSet());
        Set<Long> locIds = list.stream().map(SalesRecord::getLocationId).collect(Collectors.toSet());
        Map<Long, String> drugName = drugMapper.selectBatchIds(drugIds).stream()
                .collect(Collectors.toMap(Drug::getId, Drug::getName));
        Map<Long, String> locName = locationMapper.selectBatchIds(locIds).stream()
                .collect(Collectors.toMap(SalesLocation::getId, SalesLocation::getName));
        return list.stream().map(r -> {
            SalesRecordVO v = new SalesRecordVO();
            v.setId(r.getId());
            v.setRecordNo(r.getRecordNo());
            v.setDrugId(r.getDrugId());
            v.setDrugName(drugName.get(r.getDrugId()));
            v.setLocationId(r.getLocationId());
            v.setLocationName(locName.get(r.getLocationId()));
            v.setQty(r.getQty());
            v.setPrice(r.getPrice());
            v.setAmount(r.getAmount());
            v.setSaleTime(r.getSaleTime());
            v.setRemark(r.getRemark());
            return v;
        }).collect(Collectors.toList());
    }

    private List<ReplenishOrderVO> toReplenishVO(List<ReplenishmentOrder> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Set<Long> drugIds = list.stream().map(ReplenishmentOrder::getDrugId).collect(Collectors.toSet());
        Set<Long> locIds = list.stream().map(ReplenishmentOrder::getLocationId).collect(Collectors.toSet());
        Map<Long, String> drugName = drugMapper.selectBatchIds(drugIds).stream()
                .collect(Collectors.toMap(Drug::getId, Drug::getName));
        Map<Long, String> locName = locationMapper.selectBatchIds(locIds).stream()
                .collect(Collectors.toMap(SalesLocation::getId, SalesLocation::getName));
        return list.stream().map(o -> {
            ReplenishOrderVO v = new ReplenishOrderVO();
            v.setId(o.getId());
            v.setOrderNo(o.getOrderNo());
            v.setDrugId(o.getDrugId());
            v.setDrugName(drugName.get(o.getDrugId()));
            v.setLocationId(o.getLocationId());
            v.setLocationName(locName.get(o.getLocationId()));
            v.setQty(o.getQty());
            v.setInTime(o.getInTime());
            v.setRemark(o.getRemark());
            return v;
        }).collect(Collectors.toList());
    }
}
