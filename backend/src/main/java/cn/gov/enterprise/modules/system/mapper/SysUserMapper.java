package cn.gov.enterprise.modules.system.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** sys_user基础数据访问接口。 */
public interface SysUserMapper extends BaseMapperX<SysUserEntity> {
    @Update("UPDATE sys_user SET locked_until = NULL WHERE id = #{id} AND deleted = 0")
    int clearLoginLock(@Param("id") Long id);
}
