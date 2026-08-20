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
import org.springframework.web.bind.annotation.*;

@Tag(name = "补货入库")
@RestController
@RequestMapping("/api/v1/replenish")
@RequiredArgsConstructor
public class ReplenishController {

    private final InventoryService inventoryService;

    @OperationLog(module = "库存", operation = "补货入库")
    @Operation(summary = "补货入库(UC-A04-3)")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Long> replenish(@Valid @RequestBody ReplenishDTO dto) {
        return R.ok(inventoryService.replenish(dto));
    }

    @Operation(summary = "补货流水分页")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<PageResult<ReplenishOrderVO>> page(@RequestParam(required = false) Long drugId,
                                                @RequestParam(required = false) Long locationId,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(inventoryService.pageReplenish(drugId, locationId, pageNum, pageSize));
    }
}
