package com.huiyi.modules.demand;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.DrugDemand;
import com.huiyi.modules.system.entity.MedicalInstitution;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.DrugDemandMapper;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.MedicalInstitutionMapper;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 临床用药反馈(M8)。
 * 状态机:待处理(0)→处理中(1)→已满足(2)/已驳回(3);医师可 待处理(0)→已撤回(4)。
 * 隔离:医师见本人、公司见本公司、管理员见全部并可指派未关联反馈(company_id 为空)。
 * 每次转移回填 handler_id/handle_time/reply。
 */
@Service
@RequiredArgsConstructor
public class DrugDemandService {

    private static final int S_PENDING = 0, S_PROCESSING = 1, S_SATISFIED = 2, S_REJECTED = 3, S_WITHDRAWN = 4;

    private final DrugDemandMapper demandMapper;
    private final DrugMapper drugMapper;
    private final DoctorMapper doctorMapper;
    private final PharmaCompanyMapper companyMapper;
    private final MedicalInstitutionMapper instMapper;
    private final NotificationService notificationService;

    /** 提交:医师本人;选已有药品则带出 company_id,手填则入"未关联池"。 */
    public Long create(DemandCreateDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        Doctor doc = doctorMapper.selectById(u.getDoctorId());
        if (doc == null) throw new BusinessException(ResultCode.NOT_FOUND);

        DrugDemand d = new DrugDemand();
        d.setDoctorId(u.getDoctorId());
        d.setInstitutionId(doc.getInstitutionId());
        d.setDrugName(dto.getDrugName().trim());
        d.setDemandType(dto.getDemandType());
        d.setQty(dto.getQty());
        d.setUrgency(dto.getUrgency());
        d.setRemark(dto.getRemark());
        d.setStatus(S_PENDING);

        if (dto.getDrugId() != null) {
            Drug drug = drugMapper.selectById(dto.getDrugId());
            if (drug != null) {
                d.setDrugId(drug.getId());
                d.setCompanyId(drug.getCompanyId());   // 选已有药品 → 自动归属其公司
            }
        }
        // drugId 为空 → companyId 为空 → 进"未关联临床反馈池",由管理员指派
        demandMapper.insert(d);
        // 通知:已指派药企 → 通知该公司受理;未关联 → 通知管理员指派
        String docName = doc == null ? "医师" : doc.getName();
        String dtype = d.getDemandType() != null && d.getDemandType() == 2 ? "临床用量反馈" : "临床用药需求";
        if (d.getCompanyId() != null) {
            notificationService.notifyCompany(d.getCompanyId(), NotificationService.CAT_DEMAND,
                    "新临床反馈待受理", docName + " 报告「" + d.getDrugName() + "」" + dtype + " ×" + d.getQty(),
                    NotificationService.REF_DEMAND, d.getId());
        } else {
            notificationService.notifyAdmins(NotificationService.CAT_DEMAND,
                    "未关联反馈待指派", docName + " 报告「" + d.getDrugName() + "」,尚未指派药企",
                    NotificationService.REF_DEMAND, d.getId());
        }
        return d.getId();
    }

    /** 分页:按身份分流(医师本人 / 公司本公司 / 管理员全部;unassigned=true 仅管理员看未关联池)。
     *  返回 DemandVO,批量回填 反馈医师 / 医疗机构 / 归属公司 名称(前端列表直接展示"是谁报的")。 */
    public PageResult<DemandVO> page(Integer status, Integer demandType, Integer urgency,
                                     Boolean unassigned, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<DrugDemand> w = new LambdaQueryWrapper<>();
        if (u.getRole() == RoleConstants.DOCTOR) {
            w.eq(DrugDemand::getDoctorId, u.getDoctorId());
        } else if (u.getRole() == RoleConstants.COMPANY) {
            w.eq(DrugDemand::getCompanyId, u.getCompanyId());   // 仅本公司已指派的
        } else if (u.getRole() == RoleConstants.INSTITUTION) {
            // 机构只读视图:仅本机构医师提交的反馈(institutionId 创建时从 Doctor 冗余而来)。
            // 机构账号必须带 institutionId(种子已绑),空则 eq 匹配不到、看不到——安全降级,不串读。
            w.eq(DrugDemand::getInstitutionId, u.getInstitutionId());
        }
        // 管理员:不加身份过滤(全部)
        if (Boolean.TRUE.equals(unassigned) && u.getRole() == RoleConstants.ADMIN) {
            w.isNull(DrugDemand::getCompanyId);   // 未关联池
        }
        if (status != null) w.eq(DrugDemand::getStatus, status);
        if (demandType != null) w.eq(DrugDemand::getDemandType, demandType);
        if (urgency != null) w.eq(DrugDemand::getUrgency, urgency);
        w.orderByDesc(DrugDemand::getCreateTime);
        Page<DrugDemand> p = demandMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<DrugDemand> recs = p.getRecords();
        if (recs.isEmpty()) return PageResult.of(List.of(), p.getTotal(), pageNum, pageSize);

        // 批量取名称(分页内 ID 去重后一次查回,避免 N+1)
        Set<Long> docIds = recs.stream().map(DrugDemand::getDoctorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> instIds = recs.stream().map(DrugDemand::getInstitutionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> coIds = recs.stream().map(DrugDemand::getCompanyId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> docNames = docIds.isEmpty() ? Map.of()
                : doctorMapper.selectBatchIds(docIds).stream().collect(Collectors.toMap(Doctor::getId, Doctor::getName, (a, b) -> a));
        Map<Long, String> instNames = instIds.isEmpty() ? Map.of()
                : instMapper.selectBatchIds(instIds).stream().collect(Collectors.toMap(MedicalInstitution::getId, MedicalInstitution::getName, (a, b) -> a));
        Map<Long, String> coNames = coIds.isEmpty() ? Map.of()
                : companyMapper.selectBatchIds(coIds).stream().collect(Collectors.toMap(PharmaCompany::getId, PharmaCompany::getName, (a, b) -> a));

        List<DemandVO> vos = recs.stream().map(d -> {
            DemandVO v = new DemandVO();
            BeanUtils.copyProperties(d, v);
            v.setDoctorName(nameOf(docNames, d.getDoctorId()));
            v.setInstitutionName(nameOf(instNames, d.getInstitutionId()));
            v.setCompanyName(nameOf(coNames, d.getCompanyId()));
            return v;
        }).toList();
        return PageResult.of(vos, p.getTotal(), pageNum, pageSize);
    }

    /** null 安全取名:键为空(未关联池反馈 companyId=null 等)或名称表为空时返回 null,避免命中不可变 Map 抛 NPE。 */
    private static String nameOf(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    /** 医师撤回:仅本人 + 仅待处理。 */
    public void withdraw(Long id) {
        CurrentUser u = SecurityContextHolder.get();
        DrugDemand d = requireOwnedByDoctor(id, u);
        if (d.getStatus() != S_PENDING) throw new BusinessException(ResultCode.DEMAND_HANDLED);
        d.setStatus(S_WITHDRAWN);
        demandMapper.updateById(d);
    }

    /** 公司受理:待处理→处理中;锁定本公司跟进。 */
    public void accept(Long id) {
        CurrentUser u = SecurityContextHolder.get();
        DrugDemand d = requireOwnedByCompany(id, u);
        if (d.getStatus() != S_PENDING) throw new BusinessException(ResultCode.DEMAND_BAD_TRANSITION);
        transition(d, u, S_PROCESSING, null);
        notificationService.notifyDoctor(d.getDoctorId(), NotificationService.CAT_DEMAND,
                "反馈已受理", "您提交的「" + d.getDrugName() + "」反馈已被受理,正在处理。",
                NotificationService.REF_DEMAND, d.getId());
    }

    /** 公司标记已满足:处理中→已满足;reply 选填。 */
    public void satisfy(Long id, DemandHandleDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        DrugDemand d = requireOwnedByCompany(id, u);
        if (d.getStatus() != S_PROCESSING) throw new BusinessException(ResultCode.DEMAND_BAD_TRANSITION);
        transition(d, u, S_SATISFIED, dto == null ? null : dto.getReply());
        notificationService.notifyDoctor(d.getDoctorId(), NotificationService.CAT_DEMAND,
                "反馈已满足", "您提交的「" + d.getDrugName() + "」反馈已满足到货。",
                NotificationService.REF_DEMAND, d.getId());
    }

    /** 公司驳回:待处理→已驳回(与受理并列,即"拒单");reply 必填。 */
    public void reject(Long id, DemandHandleDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        DrugDemand d = requireOwnedByCompany(id, u);
        if (d.getStatus() != S_PENDING) throw new BusinessException(ResultCode.DEMAND_BAD_TRANSITION);
        String reply = dto == null ? null : dto.getReply();
        if (reply == null || reply.isBlank())
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "驳回须填写原因");
        transition(d, u, S_REJECTED, reply);
        notificationService.notifyDoctor(d.getDoctorId(), NotificationService.CAT_DEMAND,
                "反馈被驳回", "您提交的「" + d.getDrugName() + "」反馈被驳回:" + reply,
                NotificationService.REF_DEMAND, d.getId());
    }

    /** 管理员指派公司:把未关联反馈(或重新)归属到某公司。
     *  重新指派时重开状态机(→待处理)并清空上一手处理痕迹,否则被驳回/撤回的单子
     *  新公司无法受理(accept 要求 status==待处理),形成死胡同;已满足的不再改派。 */
    public void assign(Long id, DemandAssignDTO dto) {
        DrugDemand d = demandMapper.selectById(id);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (d.getStatus() != null && d.getStatus() == S_SATISFIED)
            throw new BusinessException(ResultCode.DEMAND_REASSIGN_FORBIDDEN); // 终态,不可重开
        d.setCompanyId(dto.getCompanyId());
        d.setStatus(S_PENDING);          // (重)指派即回到待处理,接收公司方可受理
        d.setHandlerId(null);
        d.setHandleTime(null);
        d.setReply(null);
        demandMapper.updateById(d);
        // 通知目标公司:有反馈指派给您受理。
        notificationService.notifyCompany(dto.getCompanyId(), NotificationService.CAT_DEMAND,
                "新指派反馈", "管理员指派给您一条反馈:「" + d.getDrugName() + "」",
                NotificationService.REF_DEMAND, d.getId());
    }

    /** 状态转移公共:回填 handler_id/handle_time/reply。 */
    private void transition(DrugDemand d, CurrentUser u, int to, String reply) {
        d.setStatus(to);
        d.setHandlerId(u.getUserId());
        d.setHandleTime(LocalDateTime.now());
        if (reply != null) d.setReply(reply);
        demandMapper.updateById(d);
    }

    /** 取出并校验归属(医师仅本人)。 */
    private DrugDemand requireOwnedByDoctor(Long id, CurrentUser u) {
        DrugDemand d = demandMapper.selectById(id);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (u.getDoctorId() == null || !u.getDoctorId().equals(d.getDoctorId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        return d;
    }

    /** 取出并校验归属(公司仅本公司已指派的)。 */
    private DrugDemand requireOwnedByCompany(Long id, CurrentUser u) {
        DrugDemand d = demandMapper.selectById(id);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (d.getCompanyId() == null || !d.getCompanyId().equals(u.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        return d;
    }
}
