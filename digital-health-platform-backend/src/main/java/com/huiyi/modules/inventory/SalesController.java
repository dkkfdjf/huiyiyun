package com.huiyi.modules.inventory;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "销售出库")
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SalesController {

    private final InventoryService inventoryService;

    @OperationLog(module = "库存", operation = "销售出库")
    @Operation(summary = "销售出库(防超卖,UC-A04-2)")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Long> sell(@Valid @RequestBody SaleDTO dto) {
        return R.ok(inventoryService.sell(dto));
    }

    @Operation(summary = "销售流水分页")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<PageResult<SalesRecordVO>> page(@RequestParam(required = false) Long drugId,
                                             @RequestParam(required = false) Long locationId,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(inventoryService.pageSales(drugId, locationId, pageNum, pageSize));
    }

    @Operation(summary = "进销存台账(药品×网点 对账汇总,设计 4.5/3.4.4)")
    @GetMapping("/ledger")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<PageResult<LedgerVO>> ledger(@RequestParam(required = false) Long drugId,
                                          @RequestParam(required = false) Long locationId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
                                          @RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(inventoryService.ledger(drugId, locationId, start, end, pageNum, pageSize));
    }

    @OperationLog(module = "库存", operation = "对账修正")
    @Operation(summary = "一键对账修正(以当前库存为准,补历史维护对账修正流水;库存数字不变)")
    @PostMapping("/ledger/reconcile")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<ReconcileVO> reconcile() {
        return R.ok(inventoryService.reconcile());
    }
}
