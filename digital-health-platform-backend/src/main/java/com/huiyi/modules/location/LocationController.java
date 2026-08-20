package com.huiyi.modules.location;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.modules.system.entity.SalesLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "销售网点")
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @Operation(summary = "网点列表(本企业/全局,铺货下拉与地图复用)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.GUEST})
    public R<List<SalesLocation>> list() {
        return R.ok(locationService.list());
    }

    @Operation(summary = "网点分页查询")
    @GetMapping("/page")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.GUEST})
    public R<PageResult<SalesLocation>> page(@RequestParam(required = false) String name,
                                             @RequestParam(required = false) Long cityId,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(locationService.page(name, cityId, pageNum, pageSize));
    }

    @OperationLog(module = "网点", operation = "新增")
    @Operation(summary = "新增网点")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Long> create(@Valid @RequestBody LocationSaveDTO dto) {
        return R.ok(locationService.save(dto));
    }

    @OperationLog(module = "网点", operation = "编辑")
    @Operation(summary = "修改网点")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody LocationSaveDTO dto) {
        locationService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "网点", operation = "删除")
    @Operation(summary = "删除网点(软删)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return R.ok();
    }
}
