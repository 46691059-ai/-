package cn.gov.enterprise.modules.system.menu.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.menu.dto.MenuCreateRequest;
import cn.gov.enterprise.modules.system.menu.dto.MenuPageQuery;
import cn.gov.enterprise.modules.system.menu.dto.MenuUpdateRequest;
import cn.gov.enterprise.modules.system.menu.vo.MenuVO;
import java.util.List;

public interface MenuManagementService {
    List<MenuVO> tree();
    PageResponse<MenuVO> page(MenuPageQuery query);
    Long create(MenuCreateRequest request);
    void update(MenuUpdateRequest request);
    void delete(Long id);
}
