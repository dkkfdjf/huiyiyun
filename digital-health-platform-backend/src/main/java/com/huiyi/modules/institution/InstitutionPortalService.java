package com.huiyi.modules.institution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.City;
import com.huiyi.modules.system.entity.Department;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.MedicalInstitution;
import com.huiyi.modules.system.entity.Notification;
import com.huiyi.modules.system.mapper.CityMapper;
import com.huiyi.modules.system.mapper.DepartmentMapper;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.MedicalInstitutionMapper;
import com.huiyi.modules.system.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 医疗机构工作台(机构管理员视角):本院医师数、科室数、科室人员分布。
 */
@Service
@RequiredArgsConstructor
public class InstitutionPortalService {

    private final MedicalInstitutionMapper institutionMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CityMapper cityMapper;
    private final DrugMapper drugMapper;
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    public InstitutionHomeVO home() {
        CurrentUser u = SecurityContextHolder.get();
        Long instId = u.getInstitutionId();

        MedicalInstitution inst = institutionMapper.selectById(instId);
        List<Doctor> docs = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getInstitutionId, instId));
        List<Department> depts = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(Department::getInstitutionId, instId));

        Map<Long, String> nameById = depts.stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
        Map<Long, Long> cnt = docs.stream()
                .filter(d -> d.getDepartmentId() != null)
                .collect(Collectors.groupingBy(Doctor::getDepartmentId, Collectors.counting()));

        List<InstitutionHomeVO.DeptStat> stats = new ArrayList<>();
        cnt.forEach((deptId, c) -> stats.add(stat(deptId, nameById.getOrDefault(deptId, "未命名"), c)));
        long unassigned = docs.stream().filter(d -> d.getDepartmentId() == null).count();
        if (unassigned > 0) stats.add(stat(null, "未分配科室", unassigned));

        // 获取城市名称
        String cityName = null;
        if (inst != null && inst.getCityId() != null) {
            City city = cityMapper.selectById(inst.getCityId());
            cityName = city == null ? null : city.getName();
        }

        InstitutionHomeVO vo = new InstitutionHomeVO();
        vo.setInstitutionName(inst == null ? "" : inst.getName());
        vo.setAddress(inst == null ? null : inst.getAddress());
        vo.setCityName(cityName);
        vo.setContactPerson(inst == null ? null : inst.getContactPerson());
        vo.setContactPhone(inst == null ? null : inst.getContactPhone());
        vo.setDoctorCount(docs.size());
        vo.setDepartmentCount(depts.size());
        vo.setDepartmentStats(stats);
        return vo;
    }

    private InstitutionHomeVO.DeptStat stat(Long id, String name, long c) {
        InstitutionHomeVO.DeptStat s = new InstitutionHomeVO.DeptStat();
        s.setDepartmentId(id);
        s.setDepartmentName(name);
        s.setCount(c);
        return s;
    }

    /** 可售药品目录(只读):仅上架药品(status=1),支持名称模糊。供机构端「信息查询」浏览。 */
    public PageResult<Drug> pageDrugs(String name, int pageNum, int pageSize) {
        LambdaQueryWrapper<Drug> w = new LambdaQueryWrapper<Drug>()
                .eq(Drug::getStatus, 1)
                .orderByDesc(Drug::getCreateTime);
        if (name != null && !name.isBlank()) w.like(Drug::getName, name.trim());
        Page<Drug> p = drugMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 向本院所有在职医师群发公告(站内通知:category=系统 + 专用 refType=ANNOUNCE 标记)。
     * 投递范围严格限定为当前机构(institutionId 取登录态),不可跨院。返回实际投递人数。
     * refType=ANNOUNCE 供 myAnnouncements 精确反查,避免与其它系统通知混淆串显。
     */
    public int sendAnnouncement(AnnouncementDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        Long instId = u.getInstitutionId();
        MedicalInstitution inst = institutionMapper.selectById(instId);
        String prefix = inst == null ? "本院公告" : inst.getName() + " · 公告";
        String title = prefix + ":" + dto.getTitle().trim();
        return notificationService.notifyInstitutionDoctors(instId,
                NotificationService.CAT_SYSTEM, title, dto.getContent().trim(),
                NotificationService.REF_ANNOUNCE, null);
    }

    /**
     * 本机构已发公告历史:聚合本院医师收到的 ANNOUNCE 标记通知(按 title+body 去重),
     * 给出"发送时间 + 投递人数"。仅展示本院范围(隔离),不会串入其它系统通知。
     */
    public List<AnnouncementVO> myAnnouncements() {
        CurrentUser u = SecurityContextHolder.get();
        Long instId = u.getInstitutionId();
        // 本院医师的 user_id 集合
        List<Long> userIds = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                        .eq(Doctor::getInstitutionId, instId))
                .stream().map(Doctor::getUserId).filter(java.util.Objects::nonNull).toList();
        if (userIds.isEmpty()) return List.of();
        // 仅取打了 ANNOUNCE 标记的本院公告(精确隔离,不串入其它系统通知),按 title+body 聚合
        List<Notification> all = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .in(Notification::getUserId, userIds)
                .eq(Notification::getRefType, NotificationService.REF_ANNOUNCE));
        // 用 body 作内容、title 去标题前缀后作标题;按 (title,body) 聚合,保留最早时间
        Map<String, AnnouncementVO> grouped = new LinkedHashMap<>();
        for (Notification n : all) {
            String key = safeKey(n.getTitle()) + "|" + safeKey(n.getBody());
            AnnouncementVO v = grouped.computeIfAbsent(key, k -> {
                AnnouncementVO vo = new AnnouncementVO();
                vo.setTitle(n.getTitle());
                vo.setContent(n.getBody());
                vo.setSendTime(n.getCreateTime());
                vo.setRecipientCount(0);
                return vo;
            });
            v.setRecipientCount(v.getRecipientCount() + 1);
            if (n.getCreateTime() != null && (v.getSendTime() == null
                    || n.getCreateTime().isBefore(v.getSendTime()))) {
                v.setSendTime(n.getCreateTime());
            }
        }
        return grouped.values().stream()
                .sorted(Comparator.comparing(AnnouncementVO::getSendTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static String safeKey(String s) { return s == null ? "" : s; }
}
