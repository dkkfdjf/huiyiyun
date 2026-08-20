package com.huiyi.modules.drug;

import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.aspect.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "库存管理")
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class DrugStockController {

    private final DrugStockService stockService;

    @OperationLog(module = "库存", operation = "铺货初始化")
    @Operation(summary = "铺货初始化(UC-A04-1)")
    @PostMapping("/init")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Long> init(@Valid @RequestBody StockInitDTO dto) {
        return R.ok(stockService.init(dto));
    }

    @Operation(summary = "库存分页查询")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<PageResult<StockVO>> page(@RequestParam(required = false) Long drugId,
                                       @RequestParam(required = false) Long locationId,
                                       @RequestParam(required = false) Long companyId,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(stockService.page(drugId, locationId, companyId, pageNum, pageSize));
    }

    @Operation(summary = "库存预警列表(派生态)")
    @GetMapping("/alerts")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<List<StockVO>> alerts() {
        return R.ok(stockService.alerts());
    }

    @Operation(summary = "提醒补货(管理员催办归属药企)")
    @PostMapping("/alerts/{stockId}/remind")
    @RequiresRole({RoleConstants.ADMIN})
    @OperationLog(module = "库存", operation = "提醒补货")
    public R<Void> remindReplenish(@PathVariable Long stockId) {
        stockService.remindReplenish(stockId);
        return R.ok();
    }

    @OperationLog(module = "库存", operation = "维护库存")
    @Operation(summary = "维护库存/售价/阈值(乐观锁)")
    @PutMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Void> update(@Valid @RequestBody StockUpdateDTO dto) {
        stockService.update(dto);
        return R.ok();
    }
}
