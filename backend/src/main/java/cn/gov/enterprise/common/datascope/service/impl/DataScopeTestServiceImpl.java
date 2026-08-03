package cn.gov.enterprise.common.datascope.service.impl;

import cn.gov.enterprise.common.datascope.annotation.DataScope;
import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataPermissionContextHolder;
import cn.gov.enterprise.common.datascope.context.SelfValueType;
import cn.gov.enterprise.common.datascope.mapper.DataScopeTestMapper;
import cn.gov.enterprise.common.datascope.service.DataScopeTestService;
import cn.gov.enterprise.common.datascope.vo.DataScopeRowVO;
import cn.gov.enterprise.common.datascope.vo.DataScopeTestVO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DataScopeTestServiceImpl implements DataScopeTestService {
    private final DataScopeTestMapper mapper;

    public DataScopeTestServiceImpl(DataScopeTestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @DataScope(
            orgField = "scoped.org_id",
            userField = "scoped.owner_user_id",
            selfValueType = SelfValueType.USER_ID)
    public DataScopeTestVO query() {
        DataPermissionContext context = DataPermissionContextHolder.current()
                .orElseThrow(() -> new IllegalStateException("数据权限上下文未建立"))
                .context();
        List<DataScopeRowVO> rows = mapper.selectScopedRows();
        return new DataScopeTestVO(context.userId(), context.orgId(), context.roleIds(),
                context.dataScope(), context.allowedOrgIds(), rows);
    }
}
