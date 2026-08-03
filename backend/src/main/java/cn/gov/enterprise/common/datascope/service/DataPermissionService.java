package cn.gov.enterprise.common.datascope.service;

import cn.gov.enterprise.common.datascope.context.DataPermissionContext;

/** 解析当前登录用户角色并生成统一数据权限上下文。 */
public interface DataPermissionService {
    DataPermissionContext current();
}
