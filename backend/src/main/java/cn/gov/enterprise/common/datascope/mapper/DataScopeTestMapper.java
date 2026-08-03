package cn.gov.enterprise.common.datascope.mapper;

import cn.gov.enterprise.common.datascope.vo.DataScopeRowVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 仅供开发环境验收统一数据权限 SQL 拦截能力。 */
@Mapper
public interface DataScopeTestMapper {
    @Select("""
            SELECT scoped.id,
                   scoped.org_id AS orgId,
                   scoped.owner_user_id AS ownerUserId,
                   scoped.org_name AS orgName,
                   scoped.owner_username AS ownerUsername
            FROM (
                SELECT u.id,
                       u.org_id,
                       u.id AS owner_user_id,
                       o.org_name,
                       u.username AS owner_username
                FROM sys_user u
                JOIN sys_org o ON o.id = u.org_id AND o.deleted = 0 AND o.status = 1
                WHERE u.deleted = 0 AND u.status = 1
                  AND o.org_type <> 'PARTY_ORG'
            ) scoped
            ORDER BY scoped.org_id, scoped.owner_user_id
            """)
    List<DataScopeRowVO> selectScopedRows();
}
