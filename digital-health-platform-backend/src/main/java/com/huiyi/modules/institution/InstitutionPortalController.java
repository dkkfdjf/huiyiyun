package com.huiyi.modules.institution;

import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.Drug;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 医疗机构工作台(机构管理员专用)。
 */
@Tag(name = "医疗机构工作台")
@RestController
@RequestMapping("/api/v1/institution-portal")
@RequiredArgsConstructor
public class InstitutionPortalController {

    private final InstitutionPortalService portalService;

    @Operation(summary = "机构主页统计(本院医师数 / 科室分布)")
    @GetMapping("/home")
    @RequiresRole({RoleConstants.INSTITUTION})
    public R<InstitutionHomeVO> home() {
        return R.ok(portalService.home());
    }

    @Operation(summary = "可售药品目录(只读:仅上架药品,供机构端信息查询浏览)")
    @GetMapping("/drugs")
    @RequiresRole({RoleConstants.INSTITUTION})
    public R<PageResult<Drug>> drugs(@RequestParam(required = false) String name,
                                     @RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(portalService.pageDrugs(name, pageNum, pageSize));
    }

    @Operation(summary = "向本院医师群发公告(站内通知)")
    @PostMapping("/announcements")
    @RequiresRole({RoleConstants.INSTITUTION})
    public R<Integer> sendAnnouncement(@Valid @RequestBody AnnouncementDTO dto) {
        return R.ok(portalService.sendAnnouncement(dto));
    }

    @Operation(summary = "本机构已发公告历史(按公告聚合 + 投递人数)")
    @GetMapping("/announcements")
    @RequiresRole({RoleConstants.INSTITUTION})
    public R<List<AnnouncementVO>> myAnnouncements() {
        return R.ok(portalService.myAnnouncements());
    }
}
