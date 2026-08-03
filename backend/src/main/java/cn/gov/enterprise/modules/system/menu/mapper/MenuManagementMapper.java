package cn.gov.enterprise.modules.system.menu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MenuManagementMapper {
    @Select("SELECT COUNT(1) FROM sys_role_menu WHERE menu_id = #{menuId} AND deleted = 0")
    long countRoleBindings(@Param("menuId") Long menuId);

    @Select("""
        SELECT DISTINCT ur.user_id
        FROM sys_role_menu rm
        JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.deleted = 0
        JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0
        WHERE rm.menu_id = #{menuId} AND rm.deleted = 0
        """)
    List<Long> selectBoundUserIds(@Param("menuId") Long menuId);
}
