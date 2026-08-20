package com.huiyi.modules.department;

import com.huiyi.modules.system.entity.Department;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科室列表视图:在科室基础上附带「医师数」,供管理员/机构看到科室的人员规模。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepartmentVO extends Department {

    /** 该科室下在职医师数(@TableLogic 已排除软删)。 */
    private Long doctorCount;
}
