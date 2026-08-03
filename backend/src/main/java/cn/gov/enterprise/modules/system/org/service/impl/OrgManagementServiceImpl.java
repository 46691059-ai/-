package cn.gov.enterprise.modules.system.org.service.impl;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.org.dto.OrgCreateRequest;
import cn.gov.enterprise.modules.system.org.dto.OrgPageQuery;
import cn.gov.enterprise.modules.system.org.dto.OrgUpdateRequest;
import cn.gov.enterprise.modules.system.org.mapper.OrgManagementMapper;
import cn.gov.enterprise.modules.system.org.service.OrgManagementService;
import cn.gov.enterprise.modules.system.org.vo.LeaderOptionVO;
import cn.gov.enterprise.modules.system.org.vo.OrgTreeVO;
import cn.gov.enterprise.modules.system.org.vo.OrgVO;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrgManagementServiceImpl implements OrgManagementService {
    private static final Set<String> ADMIN_ORG_TYPES = Set.of("COMPANY", "DEPARTMENT", "PROJECT_TEAM");
    private static final int MAX_TREE_PATH_LENGTH = 1000;

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final OrgManagementMapper managementMapper;
    private final CurrentSecurityContext securityContext;

    public OrgManagementServiceImpl(
            SysOrgMapper orgMapper,
            SysUserMapper userMapper,
            OrgManagementMapper managementMapper,
            CurrentSecurityContext securityContext) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.managementMapper = managementMapper;
        this.securityContext = securityContext;
    }

    @Override
    public List<OrgTreeVO> tree() {
        List<SysOrgEntity> organizations = orgMapper.selectList(administrativeOrgWrapper()
                .orderByAsc(SysOrgEntity::getTreeLevel)
                .orderByAsc(SysOrgEntity::getSortNo)
                .orderByAsc(SysOrgEntity::getOrgName));
        Set<Long> readableIds = readableOrgIds();
        if (readableIds != null) {
            organizations = organizations.stream().filter(org -> readableIds.contains(org.getId())).toList();
        }
        Map<Long, LeaderOptionVO> leaders = leaderMap(organizations);
        Map<Long, OrgTreeVO> nodes = new LinkedHashMap<>();
        for (SysOrgEntity org : organizations) {
            LeaderOptionVO leader = org.getLeaderId() == null ? null : leaders.get(org.getLeaderId());
            nodes.put(org.getId(), new OrgTreeVO(org.getId(), org.getOrgCode(), org.getOrgName(), org.getOrgType(),
                    org.getParentId(), org.getLeaderId(), leader == null ? null : leader.realName(), org.getStatus(),
                    org.getTreeLevel(), org.getSortNo(), new ArrayList<>()));
        }
        List<OrgTreeVO> roots = new ArrayList<>();
        for (OrgTreeVO node : nodes.values()) {
            OrgTreeVO parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    @Override
    public PageResponse<OrgVO> page(OrgPageQuery query) {
        validateOptionalType(query.orgType());
        Set<Long> readableIds = readableOrgIds();
        if (readableIds != null && readableIds.isEmpty()) {
            return new PageResponse<>(List.of(), 0, query.currentPage(), query.pageSize());
        }
        LambdaQueryWrapper<SysOrgEntity> wrapper = administrativeOrgWrapper()
                .in(readableIds != null, SysOrgEntity::getId, readableIds)
                .like(StringUtils.hasText(query.orgName()), SysOrgEntity::getOrgName, trim(query.orgName()))
                .eq(StringUtils.hasText(query.orgType()), SysOrgEntity::getOrgType, normalizeType(query.orgType()))
                .eq(query.status() != null, SysOrgEntity::getStatus, query.status())
                .eq(query.parentId() != null, SysOrgEntity::getParentId, query.parentId())
                .orderByAsc(SysOrgEntity::getTreeLevel)
                .orderByAsc(SysOrgEntity::getSortNo)
                .orderByAsc(SysOrgEntity::getOrgName);
        Page<SysOrgEntity> result = orgMapper.selectPage(new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return new PageResponse<>(toVos(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public OrgVO detail(Long id) {
        SysOrgEntity org = requireAdministrativeOrg(id);
        assertReadable(org.getId());
        return toVos(List.of(org)).getFirst();
    }

    @Override
    @Transactional
    public Long create(OrgCreateRequest request) {
        String orgType = requireAdministrativeType(request.orgType());
        assertOrgCodeUnique(request.orgCode(), null);
        assertLeaderValid(request.leaderId());
        SysOrgEntity parent = resolveParent(request.parentId(), orgType, null);
        String treePath = parent == null ? "/" : parent.getTreePath() + parent.getId() + "/";
        assertTreePathLength(treePath);

        SysOrgEntity entity = new SysOrgEntity();
        entity.setOrgCode(trim(request.orgCode()));
        entity.setOrgName(trim(request.orgName()));
        entity.setOrgType(orgType);
        entity.setParentId(request.parentId());
        entity.setLeaderId(request.leaderId());
        entity.setTreePath(treePath);
        entity.setTreeLevel(parent == null ? 1 : parent.getTreeLevel() + 1);
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(request.status());
        entity.setRemark(blankToNull(request.remark()));
        orgMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(OrgUpdateRequest request) {
        SysOrgEntity current = requireAdministrativeOrg(request.id());
        String orgType = requireAdministrativeType(request.orgType());
        assertOrgCodeUnique(request.orgCode(), request.id());
        assertLeaderValid(request.leaderId());
        SysOrgEntity parent = resolveParent(request.parentId(), orgType, current);
        String newTreePath = parent == null ? "/" : parent.getTreePath() + parent.getId() + "/";
        int newTreeLevel = parent == null ? 1 : parent.getTreeLevel() + 1;
        assertTreePathLength(newTreePath);

        String oldDescendantPrefix = current.getTreePath() + current.getId() + "/";
        String newDescendantPrefix = newTreePath + current.getId() + "/";
        int levelDelta = newTreeLevel - current.getTreeLevel();
        List<SysOrgEntity> descendants = !Objects.equals(current.getParentId(), request.parentId())
                ? orgMapper.selectList(new LambdaQueryWrapper<SysOrgEntity>()
                        .likeRight(SysOrgEntity::getTreePath, oldDescendantPrefix)
                        .orderByAsc(SysOrgEntity::getTreeLevel))
                : List.of();

        current.setVersion(request.version());
        current.setOrgCode(trim(request.orgCode()));
        current.setOrgName(trim(request.orgName()));
        current.setOrgType(orgType);
        current.setParentId(request.parentId());
        current.setLeaderId(request.leaderId());
        current.setTreePath(newTreePath);
        current.setTreeLevel(newTreeLevel);
        current.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        current.setStatus(request.status());
        current.setRemark(blankToNull(request.remark()));
        if (orgMapper.updateById(current) != 1) {
            throw new BusinessException("B0409", "组织数据已被修改，请刷新后重试");
        }

        for (SysOrgEntity descendant : descendants) {
            String suffix = descendant.getTreePath().substring(oldDescendantPrefix.length());
            String descendantPath = newDescendantPrefix + suffix;
            assertTreePathLength(descendantPath);
            descendant.setTreePath(descendantPath);
            descendant.setTreeLevel(descendant.getTreeLevel() + levelDelta);
            if (orgMapper.updateById(descendant) != 1) {
                throw new BusinessException("B0409", "下级组织数据已被修改，请刷新后重试");
            }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysOrgEntity org = requireAdministrativeOrg(id);
        if (org.getParentId() == null) {
            throw new BusinessException("B0409", "根组织不可删除");
        }
        long children = orgMapper.selectCount(new LambdaQueryWrapper<SysOrgEntity>()
                .eq(SysOrgEntity::getParentId, id));
        if (children > 0) {
            throw new BusinessException("B0409", "存在子组织，禁止删除");
        }
        if (managementMapper.countUsersByOrg(id) > 0) {
            throw new BusinessException("B0409", "组织下存在用户，禁止删除");
        }
        org.setDeleteToken(org.getId());
        if (orgMapper.updateById(org) != 1 || orgMapper.deleteById(id) != 1) {
            throw new BusinessException("B0409", "组织删除失败，请刷新后重试");
        }
    }

    @Override
    public List<LeaderOptionVO> leaderOptions() {
        return managementMapper.selectLeaderOptions();
    }

    @Override
    public Long getUserOrgId(Long userId) {
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("B0404", "有效用户不存在");
        }
        return user.getOrgId();
    }

    @Override
    public List<Long> getChildrenOrgIds(Long orgId) {
        requireAdministrativeOrg(orgId);
        List<Long> ids = new ArrayList<>();
        ids.add(orgId);
        ids.addAll(orgMapper.selectList(new LambdaQueryWrapper<SysOrgEntity>()
                        .like(SysOrgEntity::getTreePath, "/" + orgId + "/")
                        .ne(SysOrgEntity::getOrgType, "PARTY_ORG")
                        .eq(SysOrgEntity::getStatus, 1)
                        .orderByAsc(SysOrgEntity::getTreeLevel)
                        .orderByAsc(SysOrgEntity::getSortNo))
                .stream().map(SysOrgEntity::getId).toList());
        return List.copyOf(ids);
    }

    @Override
    public List<Long> getParentOrgIds(Long orgId) {
        SysOrgEntity org = requireAdministrativeOrg(orgId);
        if ("/".equals(org.getTreePath())) {
            return List.of();
        }
        return Arrays.stream(org.getTreePath().split("/"))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private List<OrgVO> toVos(List<SysOrgEntity> organizations) {
        if (organizations.isEmpty()) {
            return List.of();
        }
        List<Long> parentIds = organizations.stream().map(SysOrgEntity::getParentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> parentNames = parentIds.isEmpty() ? Map.of() : orgMapper.selectBatchIds(parentIds).stream()
                .collect(Collectors.toMap(SysOrgEntity::getId, SysOrgEntity::getOrgName));
        Map<Long, LeaderOptionVO> leaders = leaderMap(organizations);
        return organizations.stream().map(org -> {
            LeaderOptionVO leader = leaders.get(org.getLeaderId());
            return new OrgVO(org.getId(), org.getOrgCode(), org.getOrgName(), org.getOrgType(), org.getParentId(),
                    parentNames.get(org.getParentId()), org.getLeaderId(), leader == null ? null : leader.userId(),
                    leader == null ? null : leader.realName(), org.getTreePath(), org.getTreeLevel(), org.getSortNo(),
                    org.getStatus(), org.getRemark(), org.getCreateTime(), org.getUpdateTime(), org.getVersion());
        }).toList();
    }

    private Map<Long, LeaderOptionVO> leaderMap(List<SysOrgEntity> organizations) {
        List<Long> leaderIds = organizations.stream().map(SysOrgEntity::getLeaderId)
                .filter(Objects::nonNull).distinct().toList();
        if (leaderIds.isEmpty()) {
            return Map.of();
        }
        return managementMapper.selectLeaders(leaderIds).stream()
                .collect(Collectors.toMap(LeaderOptionVO::employeeId, Function.identity()));
    }

    private LambdaQueryWrapper<SysOrgEntity> administrativeOrgWrapper() {
        return new LambdaQueryWrapper<SysOrgEntity>().ne(SysOrgEntity::getOrgType, "PARTY_ORG");
    }

    private SysOrgEntity requireAdministrativeOrg(Long id) {
        SysOrgEntity org = orgMapper.selectById(id);
        if (org == null || !ADMIN_ORG_TYPES.contains(org.getOrgType())) {
            throw new BusinessException("B0404", "行政组织不存在");
        }
        return org;
    }

    private SysOrgEntity resolveParent(Long parentId, String orgType, SysOrgEntity current) {
        if (parentId == null) {
            if (!"COMPANY".equals(orgType)) {
                throw new BusinessException("B0409", "部门或项目组织必须选择上级组织");
            }
            return null;
        }
        if (current != null && current.getParentId() == null) {
            throw new BusinessException("B0409", "根组织不能调整为下级组织");
        }
        if (current != null && Objects.equals(current.getId(), parentId)) {
            throw new BusinessException("B0409", "组织不能选择自身作为上级");
        }
        SysOrgEntity parent = requireAdministrativeOrg(parentId);
        if (current != null && parent.getTreePath().contains("/" + current.getId() + "/")) {
            throw new BusinessException("B0409", "不能将组织移动到其下级节点");
        }
        return parent;
    }

    private void assertOrgCodeUnique(String orgCode, Long excludedId) {
        LambdaQueryWrapper<SysOrgEntity> wrapper = new LambdaQueryWrapper<SysOrgEntity>()
                .eq(SysOrgEntity::getOrgCode, trim(orgCode))
                .ne(excludedId != null, SysOrgEntity::getId, excludedId);
        if (orgMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("B0409", "组织编码已存在");
        }
    }

    private void assertLeaderValid(Long leaderId) {
        if (leaderId != null && managementMapper.countActiveLeader(leaderId) == 0) {
            throw new BusinessException("B0409", "组织负责人必须是已关联账号的有效员工");
        }
    }

    private String requireAdministrativeType(String orgType) {
        String normalized = normalizeType(orgType);
        if (!ADMIN_ORG_TYPES.contains(normalized)) {
            throw new BusinessException("B0409", "组织类型仅支持公司、部门和项目组织");
        }
        return normalized;
    }

    private void validateOptionalType(String orgType) {
        if (StringUtils.hasText(orgType)) {
            requireAdministrativeType(orgType);
        }
    }

    private String normalizeType(String orgType) {
        return orgType == null ? null : orgType.trim().toUpperCase();
    }

    private void assertTreePathLength(String path) {
        if (path.length() > MAX_TREE_PATH_LENGTH) {
            throw new BusinessException("B0409", "组织层级过深，祖先路径超过数据库长度限制");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** ALL返回null表示不追加过滤，其余范围使用JWT认证时计算出的组织集合。 */
    private Set<Long> readableOrgIds() {
        SecurityPrincipal principal = securityContext.principal();
        return principal.allDataScope() ? null : principal.allowedOrgIds();
    }

    private void assertReadable(Long orgId) {
        Set<Long> readableIds = readableOrgIds();
        if (readableIds != null && !readableIds.contains(orgId)) {
            throw new AccessDeniedException("无权查看该组织");
        }
    }
}
