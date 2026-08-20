package com.huiyi.modules.doctor;

import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.Drug;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医师工作台(医师专用只读门户)。
 * 为「临床用药反馈」选药等场景提供上架药品检索;不改动药品模块本身。
 */
@Tag(name = "医师工作台")
@RestController
@RequestMapping("/api/v1/doctor-portal")
@RequiredArgsConstructor
public class DoctorPortalController {

    private final DoctorPortalService portalService;

    @Operation(summary = "可选药品(上架药品;提交反馈时从已有药品选择,自动关联药品与公司)")
    @GetMapping("/drugs")
    @RequiresRole({RoleConstants.DOCTOR})
    public R<PageResult<Drug>> drugs(@RequestParam(required = false) String name,
                                     @RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(portalService.pageDrugs(name, pageNum, pageSize));
    }
}
