package cn.gov.enterprise.modules.system.org.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.org.dto.OrgCreateRequest;
import cn.gov.enterprise.modules.system.org.dto.OrgPageQuery;
import cn.gov.enterprise.modules.system.org.dto.OrgUpdateRequest;
import cn.gov.enterprise.modules.system.org.vo.LeaderOptionVO;
import cn.gov.enterprise.modules.system.org.vo.OrgTreeVO;
import cn.gov.enterprise.modules.system.org.vo.OrgVO;
import java.util.List;

public interface OrgManagementService extends OrgDataScopeService {
    List<OrgTreeVO> tree();
    PageResponse<OrgVO> page(OrgPageQuery query);
    OrgVO detail(Long id);
    Long create(OrgCreateRequest request);
    void update(OrgUpdateRequest request);
    void delete(Long id);
    List<LeaderOptionVO> leaderOptions();
}
