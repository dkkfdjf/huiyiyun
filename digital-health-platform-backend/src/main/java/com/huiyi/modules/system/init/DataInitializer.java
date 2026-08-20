package com.huiyi.modules.system.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.*;
import com.huiyi.modules.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 幂等 seed:首次启动(user 表为空)时写入 admin / 演示账号 / 字典 / 演示业务数据,
 * 让管理员看板、图表、地图首屏即有内容。生产可用 huiyi.seed.enabled=false 关闭。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserMapper userMapper;
    private final SysDictMapper dictMapper;
    private final CityMapper cityMapper;
    private final PharmaCompanyMapper companyMapper;
    private final DrugMapper drugMapper;
    private final SalesLocationMapper locationMapper;
    private final DrugStockMapper stockMapper;
    private final SalesRecordMapper salesMapper;
    private final ReplenishmentOrderMapper replenishMapper;
    private final MedicalInstitutionMapper instMapper;
    private final DepartmentMapper deptMapper;
    private final DoctorMapper doctorMapper;
    private final CompanyPolicyMapper policyMapper;
    private final DrugDemandMapper demandMapper;
    private final NotificationMapper notificationMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Bean
    ApplicationRunner seed() {
        return args -> {
            if (userMapper.selectCount(null) > 0) {
                log.info("seed skipped: data already exists");
                return;
            }
            log.info("seed start...");
            Long beijing = city("北京", "北京市", "110000");
            Long shanghai = city("上海", "上海市", "310000");
            Long guangzhou = city("广州", "广东省", "440100");
            Long chengdu = city("成都", "四川省", "510100");
            Long wuhan = city("武汉", "湖北省", "420100");
            Long xian = city("西安", "陕西省", "610100");

            Long huarun = company("华润医药", "91110000GR01", "张经理");
            Long yangtze = company("扬子江药业", "91321200YZJ01", "李经理");

            // 账号:每家正常药企都有可登录账号(公司01→华润、公司02→扬子江),消除"空壳药企"
            Long adminId = account("admin", "admin123", "系统管理员", RoleConstants.ADMIN, null, null);
            Long hrUserId = account("company01", "company123", "张伟", RoleConstants.COMPANY, huarun, null);
            Long yzUserId = account("company02", "company123", "李伟", RoleConstants.COMPANY, yangtze, null);
            Long doctorUserId = account("doctor01", "doctor123", "陈明", RoleConstants.DOCTOR, null, null);

            Long[] hrDrugs = drugs(huarun, "华润三九", HR_DRUGS);    // 6 华润药品
            Long[] yzDrugs = drugs(yangtze, "扬子江药业", YZ_DRUGS);  // 4 扬子江药品(独立批准文号)

            // 网点:华润 5 城 + 扬子江 2 城(含西安,补掉"空城市")
            Long dongmen = location("东门店", "上海市浦东新区东门路 1 号", shanghai, huarun, 121.526, 31.238);
            Long nanzhan = location("南站店", "北京市丰台区南站路 8 号", beijing, huarun, 116.378, 39.865);
            Long zhongxin = location("中心店", "广州市天河区中心大道 66 号", guangzhou, huarun, 113.331, 23.135);
            Long xinqu = location("新区店", "成都市高新区新程路 9 号", chengdu, huarun, 104.073, 30.578);
            Long ximen = location("西门店", "武汉市武昌区西门路 12 号", wuhan, huarun, 114.311, 30.588);
            Long yzXian = location("扬子江西安店", "西安市雁塔区科技路 38 号", xian, yangtze, 108.940, 34.341);
            Long yzShanghai = location("扬子江上海办", "上海市静安区南京西路 88 号", shanghai, yangtze, 121.458, 31.232);

            // 铺货 + 12 周出入库流水 + 当前库存,三者自洽(台账恒等、不超卖、不悬空、售价一致)。
            // threshold 给定预警水位;最终库存由流水反推,是否预警自然产生。
            stockFlow(hrDrugs[1], dongmen, huarun, 25.50, 80);     // 布洛芬
            stockFlow(hrDrugs[0], dongmen, huarun, 31.00, 100);    // 阿莫西林
            stockFlow(hrDrugs[2], nanzhan, huarun, 14.20, 50);     // 氨氯地平
            stockFlow(hrDrugs[3], nanzhan, huarun, 9.80, 40);      // 二甲双胍
            stockFlow(hrDrugs[4], zhongxin, huarun, 36.00, 60);    // 硝苯地平
            stockFlow(hrDrugs[5], xinqu, huarun, 21.00, 30);       // 奥美拉唑
            stockFlow(hrDrugs[0], ximen, huarun, 31.00, 50);       // 阿莫西林·西门
            stockFlow(yzDrugs[0], yzXian, yangtze, 28.00, 40);     // 蓝芩口服液
            stockFlow(yzDrugs[1], yzXian, yangtze, 12.00, 30);     // 板蓝根颗粒
            stockFlow(yzDrugs[2], yzShanghai, yangtze, 22.00, 30); // 胃苏颗粒
            stockFlow(yzDrugs[3], yzShanghai, yangtze, 8.50, 40);  // 右美沙芬片

            // 机构 + 科室 + 医师
            Long xiehe = institution("协和医院", "北京市东城区帅府园 1 号", beijing, 116.410, 39.912);
            Long shiyi = institution("市第一医院", "上海市虹口区海宁路 85 号", shanghai, 121.490, 31.254);
            Long shengren = institution("省人民医院", "广州市越秀区中山二路 106 号", guangzhou, 113.283, 23.140);

            // 机构管理员账号(绑定协和,供机构工作台演示;协和已有种子医师/科室)
            account("inst01", "inst123", "协和医院管理员", RoleConstants.INSTITUTION, null, xiehe);

            departmentsAndDoctors(xiehe, shiyi, shengren, doctorUserId);

            policies(huarun);
            policies(yangtze);
            demands(doctorUserId, xiehe, shiyi, shengren, hrUserId, yzUserId, hrDrugs, yzDrugs);
            log.info("seed done");
        };
    }

    private Long city(String name, String province, String code) {
        City c = new City();
        c.setName(name); c.setProvince(province); c.setRegionCode(code);
        cityMapper.insert(c);
        return c.getId();
    }

    private Long company(String name, String credit, String contact) {
        PharmaCompany c = new PharmaCompany();
        c.setName(name); c.setCreditCode(credit); c.setContactPerson(contact);
        c.setContactPhone("13800000000"); c.setAddress("中国");
        c.setAuditStatus(1);   // 管理员录入默认正常(无审核流程)
        c.setAuditTime(LocalDateTime.now()); c.setAuditRemark("管理员录入");
        companyMapper.insert(c);
        return c.getId();
    }

    private Long account(String username, String pwd, String realName, int role, Long companyId, Long institutionId) {
        User u = new User();
        u.setUsername(username); u.setPassword(encoder.encode(pwd));
        u.setRealName(realName); u.setRole(role); u.setStatus(1);
        u.setCompanyId(companyId); u.setInstitutionId(institutionId);
        userMapper.insert(u);
        return u.getId();
    }

    // 华润药品(西药为主)
    private static final Object[][] HR_DRUGS = {
            {"阿莫西林胶囊", "0.25g × 30 粒", "胶囊", "国药准字H20000001", "盒", 31.00},
            {"布洛芬片", "0.2g × 24 片", "片剂", "国药准字H20000102", "盒", 25.50},
            {"氨氯地平片", "5mg × 28 片", "片剂", "国药准字H20000203", "盒", 14.20},
            {"二甲双胍片", "0.5g × 20 片", "片剂", "国药准字H20000304", "盒", 9.80},
            {"硝苯地平控释片", "30mg × 7 片", "片剂", "国药准字H20000405", "盒", 36.00},
            {"奥美拉唑肠溶胶囊", "20mg × 14 粒", "胶囊", "国药准字H20000506", "盒", 21.00},
    };
    // 扬子江药品(中西药皆有,批准文号独立,避免与华润唯一键冲突)
    private static final Object[][] YZ_DRUGS = {
            {"蓝芩口服液", "10ml × 12 支", "口服液", "国药准字Z20110001", "盒", 28.00},
            {"板蓝根颗粒", "10g × 20 袋", "颗粒剂", "国药准字Z20110002", "盒", 12.00},
            {"胃苏颗粒", "5g × 15 袋", "颗粒剂", "国药准字Z20110003", "盒", 22.00},
            {"右美沙芬片", "15mg × 24 片", "片剂", "国药准字H20110004", "盒", 8.50},
    };

    private Long[] drugs(Long companyId, String producer, Object[][] defs) {
        Long[] ids = new Long[defs.length];
        for (int i = 0; i < defs.length; i++) {
            Drug d = new Drug();
            d.setName((String) defs[i][0]); d.setSpecification((String) defs[i][1]);
            d.setDosageForm((String) defs[i][2]); d.setCompanyId(companyId);
            d.setApprovalNo((String) defs[i][3]); d.setUnit((String) defs[i][4]);
            d.setProducer(producer); d.setStatus(1);
            drugMapper.insert(d);
            ids[i] = d.getId();
        }
        return ids;
    }

    private Long location(String name, String address, Long cityId, Long companyId, double lng, double lat) {
        SalesLocation l = new SalesLocation();
        l.setName(name); l.setAddress(address); l.setCityId(cityId); l.setCompanyId(companyId);
        l.setLongitude(BigDecimal.valueOf(lng)); l.setLatitude(BigDecimal.valueOf(lat));
        l.setContactPerson("店长"); l.setContactPhone("13900000000");
        locationMapper.insert(l);
        return l.getId();
    }

    private final java.util.concurrent.atomic.AtomicLong flowSeq = new java.util.concurrent.atomic.AtomicLong(1000);

    /**
     * 铺货 + 12 周出入库流水 + 当前库存,三者自洽:
     *  ① 期初铺货入库(流水里最早一笔,作为对账期初余额);
     *  ② 12 周内顺序 出库(单笔 ≤ 当前余额 → 永不超卖)/ 入库;
     *  ③ stock_qty = Σ入 − Σ出(由构造保证,台账 balanced 恒为 true;售价取铺货价,流水与库存一致)。
     * threshold 由调用方给定(预警水位);最终余额随机,是否预警自然产生。
     */
    private void stockFlow(Long drugId, Long locationId, Long companyId, double price, int threshold) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        BigDecimal unitPrice = BigDecimal.valueOf(price);

        // ① 期初铺货(13 周前,确保是流水最早一笔)
        int balance = 120 + r.nextInt(180);           // 120~299
        int opening = balance;
        replenish(drugId, locationId, companyId, opening, now.minusWeeks(13).minusHours(r.nextInt(120)), "铺货初始化");

        // ② 12 周,每周 1~2 笔;出库偏多让水位自然下沉、产生预警
        for (int w = 11; w >= 0; w--) {
            for (int k = 0; k < 1 + r.nextInt(2); k++) {
                LocalDateTime t = now.minusWeeks(w).minusHours(r.nextInt(24 * 7));
                if (balance > 0 && r.nextDouble() < 0.78) {
                    int q = 1 + r.nextInt(Math.max(1, Math.min(balance, 35)));
                    sale(drugId, locationId, companyId, q, unitPrice, t);
                    balance -= q;
                } else {
                    int q = 15 + r.nextInt(46);        // 15~60
                    replenish(drugId, locationId, companyId, q, t, "周期补货");
                    balance += q;
                }
            }
        }

        // ③ 当前库存 = Σ入 − Σ出
        DrugStock s = new DrugStock();
        s.setDrugId(drugId); s.setLocationId(locationId); s.setCompanyId(companyId);
        s.setStockQty(balance); s.setPrice(unitPrice); s.setThreshold(threshold);
        stockMapper.insert(s);
    }

    private void sale(Long drugId, Long locationId, Long companyId, int qty, BigDecimal price, LocalDateTime t) {
        SalesRecord s = new SalesRecord();
        s.setRecordNo("SAL" + t.toLocalDate().toString().replace("-", "") + String.format("%06d", flowSeq.incrementAndGet()));
        s.setLocationId(locationId); s.setDrugId(drugId); s.setCompanyId(companyId);
        s.setQty(qty); s.setPrice(price); s.setAmount(price.multiply(BigDecimal.valueOf(qty)));
        s.setSaleTime(t);
        salesMapper.insert(s);
    }

    private void replenish(Long drugId, Long locationId, Long companyId, int qty, LocalDateTime t, String remark) {
        ReplenishmentOrder o = new ReplenishmentOrder();
        o.setOrderNo("REP" + t.toLocalDate().toString().replace("-", "") + String.format("%06d", flowSeq.incrementAndGet()));
        o.setLocationId(locationId); o.setDrugId(drugId); o.setCompanyId(companyId);
        o.setQty(qty); o.setInTime(t); o.setRemark(remark);
        replenishMapper.insert(o);
    }

    private Long institution(String name, String address, Long cityId, double lng, double lat) {
        MedicalInstitution m = new MedicalInstitution();
        m.setName(name); m.setAddress(address); m.setCityId(cityId);
        m.setLongitude(BigDecimal.valueOf(lng)); m.setLatitude(BigDecimal.valueOf(lat));
        m.setAuditStatus(1); m.setContactPerson("院办"); m.setContactPhone("010-00000000");
        instMapper.insert(m);
        return m.getId();
    }

    private void departmentsAndDoctors(Long xiehe, Long shiyi, Long shengren, Long doctorUserId) {
        // 科室
        String[] deptNames = {"内科", "外科", "心内科", "儿科"};
        String[] titles = {"医师", "主治医师", "副主任医师", "主任医师"};
        String[] names = {"陈明","李娜","王芳","刘强","赵敏","孙伟","周涛","吴静",
                "郑磊","马丽","黄勇","林雪","徐杰","胡静","朱涛","高翔","谢军","韩雪"};
        int seq = 2; // doctor01 为主登录账号,演示医师从 doctor02 起
        for (Long inst : new Long[]{xiehe, shiyi, shengren}) {
            Long[] deptIds = new Long[deptNames.length];
            for (int i = 0; i < deptNames.length; i++) {
                Department d = new Department();
                d.setInstitutionId(inst); d.setName(deptNames[i]); d.setSort(i);
                deptMapper.insert(d);
                deptIds[i] = d.getId();
            }
            // 医师(每个机构每科室若干):用户名 doctor02/doctor03…,密码统一 doctor123,均可登录
            for (int i = 0; i < deptNames.length; i++) {
                int n = 2 + ThreadLocalRandom.current().nextInt(4);
                for (int j = 0; j < n; j++) {
                    String name = names[(seq - 2) % names.length];
                    Long uid = account(String.format("doctor%02d", seq), "doctor123",
                            name, RoleConstants.DOCTOR, null, inst);
                    Doctor doc = new Doctor();
                    doc.setUserId(uid); doc.setName(name);
                    doc.setInstitutionId(inst); doc.setDepartmentId(deptIds[i]);
                    doc.setTitle(titles[ThreadLocalRandom.current().nextInt(titles.length)]);
                    doctorMapper.insert(doc);
                    seq++;
                }
            }
        }
        // doctor01 可登录账号的档案(绑定协和 心内科)
        Department heart = deptMapper.selectList(null).stream()
                .filter(d -> d.getInstitutionId().equals(xiehe) && "心内科".equals(d.getName()))
                .findFirst().orElse(null);
        Doctor me = new Doctor();
        me.setUserId(doctorUserId); me.setName("陈明");
        me.setInstitutionId(xiehe); me.setDepartmentId(heart == null ? null : heart.getId());
        me.setTitle("主治医师"); me.setPhone("13700000000");
        doctorMapper.insert(me);
    }

    private void policies(Long companyId) {
        policy(companyId, "2026 年医保药品目录调整通知", "自 2026 年起,部分慢性病常用药纳入医保统筹...", 1,
                LocalDate.now().minusMonths(2), null);
        policy(companyId, "心内科常用药价格调整", "阿托伐他汀、氨氯地平等药品零售价调整...", 3,
                LocalDate.now().minusDays(10), LocalDate.now().plusMonths(6));
        policy(companyId, "新药企合作公告", "我司与多家三甲医院建立直供合作...", 2,
                LocalDate.now().minusDays(3), null);
    }

    private void policy(Long companyId, String title, String content, int type, LocalDate eff, LocalDate exp) {
        CompanyPolicy p = new CompanyPolicy();
        p.setCompanyId(companyId); p.setTitle(title); p.setContent(content);
        p.setPolicyType(type); p.setEffectiveDate(eff); p.setExpireDate(exp);
        policyMapper.insert(p);
    }

    /** 取某机构下 id 最小的医师(种子反馈用)。 */
    private Doctor firstDoctor(Long institutionId) {
        return doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getInstitutionId, institutionId).orderByAsc(Doctor::getId))
                .stream().findFirst().orElse(null);
    }

    /**
     * 种子临床反馈:覆盖 待处理/处理中/已满足/已驳回 + 未关联池,让核心流程与各角色看板首屏有内容。
     * doctorId 取医师档案 id(与运行时 create 一致),handlerId 取药企账号 id。
     */
    private void demands(Long mainDoctorUserId, Long xiehe, Long shiyi, Long shengren,
                         Long hrUserId, Long yzUserId, Long[] hrDrugs, Long[] yzDrugs) {
        Doctor chen = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>().eq(Doctor::getUserId, mainDoctorUserId));
        Long chenId = chen == null ? null : chen.getId();
        Doctor shiyiDoc = firstDoctor(shiyi);
        Doctor shengrenDoc = firstDoctor(shengren);
        Long shiyiId = shiyiDoc == null ? null : shiyiDoc.getId();
        Long shengrenId = shengrenDoc == null ? null : shengrenDoc.getId();

        LocalDateTime now = LocalDateTime.now();
        // 1. 陈明(协和)→ 阿莫西林,华润,紧急,待处理
        demand(chenId, xiehe, "阿莫西林胶囊", hrDrugs[0], hrUserId, 1, 50, 2, 0,
                "心内科门诊量上升,阿莫西林库存告急,请尽快补货。", null, null, null);
        // 2. 协和医师 → 氨氯地平,华润,用量反馈,处理中
        demand(chenId, xiehe, "氨氯地平片", hrDrugs[2], hrUserId, 2, 30, 1, 1,
                "本月氨氯地平用量较上月增加约 20%。", null, hrUserId, now.minusDays(1));
        // 3. 市第一医师 → 蓝芩口服液,扬子江,已满足
        demand(shiyiId, shiyi, "蓝芩口服液", yzDrugs[0], yzUserId, 1, 40, 1, 2,
                "儿科上呼吸道感染高发,蓝芩口服液需求大。", "已补货 60 盒至市第一医院药房。", yzUserId, now.minusDays(2));
        // 4. 省人民医师 → 奥美拉唑,华润,已驳回
        demand(shengrenId, shengren, "奥美拉唑肠溶胶囊", hrDrugs[5], hrUserId, 1, 20, 1, 3,
                "消化内科申请增加奥美拉唑供应。", "该规格当前库存紧张,建议暂用同类替代品。", hrUserId, now.minusDays(5));
        // 5. 陈明(协和)→ 手填药名,未关联池,待处理
        demand(chenId, xiehe, "盐酸氨溴索口服溶液", null, null, 1, 15, 1, 0,
                "痰多患者增多,申请补充氨溴索。", null, null, null);
    }

    private void demand(Long doctorId, Long institutionId, String drugName, Long drugId, Long companyId,
                        int type, int qty, int urgency, int status,
                        String remark, String reply, Long handlerId, LocalDateTime handleTime) {
        DrugDemand d = new DrugDemand();
        d.setDoctorId(doctorId); d.setInstitutionId(institutionId);
        d.setDrugName(drugName); d.setDrugId(drugId); d.setCompanyId(companyId);
        d.setDemandType(type); d.setQty(qty); d.setUrgency(urgency); d.setStatus(status);
        d.setRemark(remark); d.setReply(reply);
        d.setHandlerId(handlerId); d.setHandleTime(handleTime);
        demandMapper.insert(d);
    }

    @Bean
    ApplicationRunner seedNotifications() {
        return args -> {
            if (notificationMapper.selectCount(null) > 0) {
                log.info("notification seed skipped: data already exists");
                return;
            }
            log.info("notification seed start...");
            LocalDateTime now = LocalDateTime.now();
            // 按角色挑演示账号,各塞几条贴合角色的站内通知,让四角色铃铛首屏即有内容
            User admin = byUsername("admin");
            User company01 = byUsername("company01");
            User doctor01 = byUsername("doctor01");
            User inst01 = byUsername("inst01");
            if (admin != null) {
                seedNotif(admin.getId(), NotificationService.CAT_SYSTEM, "欢迎使用慧医云管理台",
                        "您可在「系统监控」查看缓存与接口健康。", null, null, now.minusDays(3));
                seedNotif(admin.getId(), NotificationService.CAT_DEMAND, "1 条反馈未指派药企",
                        "「盐酸氨溴索口服溶液」反馈尚未归属药企,请前往「临床反馈」指派。",
                        NotificationService.REF_DEMAND, null, now.minusHours(8));
            }
            if (company01 != null) {
                seedNotif(company01.getId(), NotificationService.CAT_STOCK, "库存预警:布洛芬片",
                        "东门店 当前库存 18 已低于安全线 80,请及时补货。",
                        NotificationService.REF_STOCK, null, now.minusDays(2));
                seedNotif(company01.getId(), NotificationService.CAT_DEMAND, "新临床反馈待受理",
                        "陈明 报告「阿莫西林胶囊」临床用药需求 ×50,紧急。",
                        NotificationService.REF_DEMAND, null, now.minusDays(1));
                seedNotif(company01.getId(), NotificationService.CAT_SYSTEM, "欢迎使用慧医云企业台",
                        "处理临床反馈、管理库存与流向、查阅政策。", null, null, now.minusDays(3));
            }
            if (doctor01 != null) {
                seedNotif(doctor01.getId(), NotificationService.CAT_DEMAND, "反馈已受理",
                        "您提交的「氨氯地平片」反馈已被受理,正在处理。",
                        NotificationService.REF_DEMAND, null, now.minusDays(1));
                seedNotif(doctor01.getId(), NotificationService.CAT_SYSTEM, "新政策发布",
                        "「2026 年医保药品目录调整通知」已发布,请前往政策查阅。",
                        null, null, now.minusDays(2));
            }
            if (inst01 != null) {
                seedNotif(inst01.getId(), NotificationService.CAT_SYSTEM, "新政策发布",
                        "「心内科常用药价格调整」已发布,请通知相关科室。",
                        null, null, now.minusDays(3));
                seedNotif(inst01.getId(), NotificationService.CAT_SYSTEM, "必备材料公告更新",
                        "「门诊特殊病报销」材料清单已更新,请查阅。",
                        null, null, now.minusDays(1));
            }
            log.info("notification seed done");
        };
    }

    private User byUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private void seedNotif(Long userId, int category, String title, String body, String refType, Long refId, LocalDateTime time) {
        Notification x = new Notification();
        x.setUserId(userId);
        x.setCategory(category);
        x.setTitle(title);
        x.setBody(body);
        x.setRefType(refType);
        x.setRefId(refId);
        x.setIsRead(0);
        x.setCreateTime(time);
        x.setUpdateTime(time);
        notificationMapper.insert(x);
    }

    @Bean
    ApplicationRunner seedDict(SysDictMapper mapper) {
        return args -> {
            if (mapper.selectCount(null) > 0) return;
            String[][] d = {
                    {"user_role", "0", "系统管理员"}, {"user_role", "1", "医药公司用户"},
                    {"user_role", "2", "医疗机构管理员"}, {"user_role", "3", "医师"},
                    {"audit_status", "1", "正常"}, {"audit_status", "3", "停用"},
                    {"drug_status", "0", "下架"}, {"drug_status", "1", "上架"},
                    {"demand_status", "0", "待处理"}, {"demand_status", "1", "处理中"},
                    {"demand_status", "2", "已满足"}, {"demand_status", "3", "已驳回"}, {"demand_status", "4", "已撤回"},
                    {"demand_type", "1", "临床用药需求"}, {"demand_type", "2", "临床用量反馈"},
                    {"demand_urgency", "1", "一般"}, {"demand_urgency", "2", "紧急"},
                    {"policy_type", "1", "医保"}, {"policy_type", "2", "药企"}, {"policy_type", "3", "价格"},
            };
            for (int i = 0; i < d.length; i++) {
                SysDict s = new SysDict();
                s.setDictType(d[i][0]); s.setDictKey(d[i][1]); s.setDictValue(d[i][2]); s.setSort(i);
                mapper.insert(s);
            }
            log.info("dict seed done");
        };
    }

    @Bean
    ApplicationRunner seedMaterials(EssentialMaterialMapper mapper) {
        return args -> {
            if (mapper.selectCount(null) > 0) return;
            // 必备材料 = 患者办理报销、特殊病种等事项所需携带的资料清单(全局参照数据,无租户隔离)。
            // 必须有真实数据:它是 materials:page 缓存的演示对象,空结果会被缓存(空 != null)。
            String[][] rows = {
                    {"门诊报销",       "报销类",     "门诊报销携带资料:门诊发票、合作医疗证历本(或病历)。"},
                    {"住院报销",       "报销类",     "住院报销携带资料:住院发票、合作医疗证历本(或病历)、费用明细清单、出院小结、其它有关证明。"},
                    {"门诊特殊病报销", "报销类",     "门诊特殊病报销携带资料:门诊发票、特殊病种合作医疗证历本。"},
                    {"特殊病种办理",   "特殊病种类", "办理特殊病种携带资料:特殊病种门诊治疗建议书、合作医疗证历本、病历、有关化验报告单、照片二张。"},
                    {"糖尿病必备材料", "慢病类",     "糖尿病慢病备查资料:慢性病证明、身份证复印件。"},
            };
            for (String[] r : rows) {
                EssentialMaterial m = new EssentialMaterial();
                m.setName(r[0]); m.setCategory(r[1]); m.setContent(r[2]);
                mapper.insert(m);
            }
            log.info("materials seed done: {} rows", rows.length);
        };
    }
}
