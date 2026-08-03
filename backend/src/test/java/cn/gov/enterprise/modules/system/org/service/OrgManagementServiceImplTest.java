package cn.gov.enterprise.modules.system.org.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.org.dto.OrgCreateRequest;
import cn.gov.enterprise.modules.system.org.dto.OrgUpdateRequest;
import cn.gov.enterprise.modules.system.org.mapper.OrgManagementMapper;
import cn.gov.enterprise.modules.system.org.service.impl.OrgManagementServiceImpl;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgManagementServiceImplTest {
    @Mock SysOrgMapper orgMapper;
    @Mock SysUserMapper userMapper;
    @Mock OrgManagementMapper managementMapper;
    @Mock CurrentSecurityContext securityContext;
    private OrgManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgManagementServiceImpl(orgMapper, userMapper, managementMapper, securityContext);
    }

    @Test
    void createChildBuildsTreePathFromParent() {
        SysOrgEntity parent = org(100L, "COMPANY", null, "/", 1);
        when(orgMapper.selectCount(any())).thenReturn(0L);
        when(orgMapper.selectById(100L)).thenReturn(parent);

        service.create(new OrgCreateRequest(
                "DEPT009", "审计部", "DEPARTMENT", 100L, null, 1, 9, null));

        ArgumentCaptor<SysOrgEntity> captor = ArgumentCaptor.forClass(SysOrgEntity.class);
        verify(orgMapper).insert(captor.capture());
        assertThat(captor.getValue().getTreePath()).isEqualTo("/100/");
        assertThat(captor.getValue().getTreeLevel()).isEqualTo(2);
    }

    @Test
    void createRejectsPartyOrganizationType() {
        assertThatThrownBy(() -> service.create(new OrgCreateRequest(
                "PARTY01", "党委", "PARTY_ORG", null, null, 1, 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("组织类型仅支持公司、部门和项目组织");
    }

    @Test
    void createRejectsLeaderWithoutActiveUserAccount() {
        when(orgMapper.selectCount(any())).thenReturn(0L);
        when(managementMapper.countActiveLeader(8L)).thenReturn(0L);
        assertThatThrownBy(() -> service.create(new OrgCreateRequest(
                "DEPT009", "审计部", "DEPARTMENT", 100L, 8L, 1, 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("组织负责人必须是已关联账号的有效员工");
    }

    @Test
    void deleteRejectsRootOrganization() {
        when(orgMapper.selectById(100L)).thenReturn(org(100L, "COMPANY", null, "/", 1));
        assertThatThrownBy(() -> service.delete(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("根组织不可删除");
    }

    @Test
    void deleteRejectsOrganizationWithChildren() {
        when(orgMapper.selectById(101L)).thenReturn(org(101L, "DEPARTMENT", 100L, "/100/", 2));
        when(orgMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(101L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("存在子组织，禁止删除");
    }

    @Test
    void movingNodeRewritesAllDescendantPaths() {
        SysOrgEntity current = org(101L, "DEPARTMENT", 100L, "/100/", 2);
        current.setOrgCode("DEPT001");
        current.setOrgName("综合管理部");
        current.setVersion(0);
        SysOrgEntity newParent = org(200L, "COMPANY", null, "/", 1);
        SysOrgEntity descendant = org(102L, "PROJECT_TEAM", 101L, "/100/101/", 3);
        when(orgMapper.selectById(101L)).thenReturn(current);
        when(orgMapper.selectById(200L)).thenReturn(newParent);
        when(orgMapper.selectCount(any())).thenReturn(0L);
        when(orgMapper.selectList(any())).thenReturn(List.of(descendant));
        when(orgMapper.updateById(any(SysOrgEntity.class))).thenReturn(1);

        service.update(new OrgUpdateRequest(
                101L, 0, "DEPT001", "综合管理部", "DEPARTMENT", 200L, null, 1, 0, null));

        assertThat(current.getTreePath()).isEqualTo("/200/");
        assertThat(descendant.getTreePath()).isEqualTo("/200/101/");
        assertThat(descendant.getTreeLevel()).isEqualTo(3);
    }

    @Test
    void departmentManagerTreeOnlyContainsOwnOrganizationAndChildren() {
        SysOrgEntity company = org(100L, "COMPANY", null, "/", 1);
        SysOrgEntity department = org(103L, "DEPARTMENT", 100L, "/100/", 2);
        SysOrgEntity labeling = org(10301L, "PROJECT_TEAM", 103L, "/100/103/", 3);
        SysOrgEntity collection = org(10302L, "PROJECT_TEAM", 103L, "/100/103/", 3);
        SysOrgEntity research = org(10303L, "PROJECT_TEAM", 103L, "/100/103/", 3);
        SysOrgEntity finance = org(102L, "DEPARTMENT", 100L, "/100/", 2);
        when(securityContext.principal()).thenReturn(new SecurityPrincipal(
                90003L, "digital_manager", 103L, Set.of(103L, 10301L, 10302L, 10303L), false, false, 0));
        when(orgMapper.selectList(any())).thenReturn(List.of(company, department, labeling, collection, research, finance));

        var tree = service.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.getFirst().id()).isEqualTo(103L);
        assertThat(tree.getFirst().children()).extracting(node -> node.id())
                .containsExactlyInAnyOrder(10301L, 10302L, 10303L);
    }

    @Test
    void getChildrenOrgIdsIncludesCurrentOrganizationAndAllDescendants() {
        SysOrgEntity department = org(103L, "DEPARTMENT", 100L, "/100/", 2);
        SysOrgEntity labeling = org(10301L, "PROJECT_TEAM", 103L, "/100/103/", 3);
        SysOrgEntity collection = org(10302L, "PROJECT_TEAM", 103L, "/100/103/", 3);
        SysOrgEntity research = org(10303L, "PROJECT_TEAM", 103L, "/100/103/", 3);
        when(orgMapper.selectById(103L)).thenReturn(department);
        when(orgMapper.selectList(any())).thenReturn(List.of(labeling, collection, research));

        assertThat(service.getChildrenOrgIds(103L))
                .containsExactly(103L, 10301L, 10302L, 10303L);
    }

    private SysOrgEntity org(Long id, String type, Long parentId, String path, int level) {
        SysOrgEntity org = new SysOrgEntity();
        org.setId(id);
        org.setOrgType(type);
        org.setParentId(parentId);
        org.setTreePath(path);
        org.setTreeLevel(level);
        org.setSortNo(0);
        org.setStatus(1);
        return org;
    }
}
