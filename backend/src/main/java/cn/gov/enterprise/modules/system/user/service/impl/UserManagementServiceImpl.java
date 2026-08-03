package cn.gov.enterprise.modules.system.user.service.impl;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.entity.SysUserRoleEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysRoleMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserRoleMapper;
import cn.gov.enterprise.modules.system.user.dto.*;
import cn.gov.enterprise.modules.system.user.mapper.UserManagementMapper;
import cn.gov.enterprise.modules.system.user.service.UserManagementService;
import cn.gov.enterprise.modules.system.user.vo.RoleOptionVO;
import cn.gov.enterprise.modules.system.user.vo.OrgOptionVO;
import cn.gov.enterprise.modules.system.user.vo.UserVO;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityIdentityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserManagementServiceImpl implements UserManagementService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysOrgMapper orgMapper;
    private final UserManagementMapper managementMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentSecurityContext securityContext;
    private final SecurityIdentityService identityService;

    public UserManagementServiceImpl(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            SysOrgMapper orgMapper,
            UserManagementMapper managementMapper,
            PasswordEncoder passwordEncoder,
            CurrentSecurityContext securityContext,
            SecurityIdentityService identityService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.orgMapper = orgMapper;
        this.managementMapper = managementMapper;
        this.passwordEncoder = passwordEncoder;
        this.securityContext = securityContext;
        this.identityService = identityService;
    }

    @Override
    public PageResponse<UserVO> page(UserPageQuery query) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .like(StringUtils.hasText(query.username()), SysUserEntity::getUsername, trim(query.username()))
                .like(StringUtils.hasText(query.realName()), SysUserEntity::getRealName, trim(query.realName()))
                .eq(query.orgId() != null, SysUserEntity::getOrgId, query.orgId())
                .eq(query.status() != null, SysUserEntity::getStatus, query.status())
                .orderByDesc(SysUserEntity::getCreateTime);
        Page<SysUserEntity> result = userMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        List<UserVO> records = toVos(result.getRecords());
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public UserVO detail(Long id) {
        return toVos(List.of(requireUser(id))).getFirst();
    }

    @Override
    @Transactional
    public Long create(UserCreateRequest request) {
        assertUsernameUnique(request.username(), null);
        requireActiveOrg(request.orgId());
        SysUserEntity entity = new SysUserEntity();
        entity.setUsername(trim(request.username()));
        entity.setPassword(passwordEncoder.encode(request.password()));
        entity.setRealName(trim(request.realName()));
        entity.setPhone(blankToNull(request.phone()));
        entity.setEmail(blankToNull(request.email()));
        entity.setOrgId(request.orgId());
        entity.setStatus(request.status());
        entity.setTokenVersion(0);
        entity.setLoginFailCount(0);
        userMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(UserUpdateRequest request) {
        SysUserEntity current = requireUser(request.id());
        assertCanManageProtectedUser(request.id());
        assertUsernameUnique(request.username(), request.id());
        requireActiveOrg(request.orgId());
        current.setVersion(request.version());
        current.setUsername(trim(request.username()));
        current.setRealName(trim(request.realName()));
        current.setPhone(blankToNull(request.phone()));
        current.setEmail(blankToNull(request.email()));
        current.setOrgId(request.orgId());
        current.setStatus(request.status());
        current.setTokenVersion(defaultZero(current.getTokenVersion()) + 1);
        if (userMapper.updateById(current) != 1) {
            throw new BusinessException("B0409", "用户数据已被修改，请刷新后重试");
        }
        identityService.evict(request.id());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (Objects.equals(id, securityContext.userId())) {
            throw new BusinessException("B0409", "禁止删除当前登录用户");
        }
        SysUserEntity target = requireUser(id);
        assertCanManageProtectedUser(id);
        target.setDeleteToken(target.getId());
        if (userMapper.updateById(target) != 1 || userMapper.deleteById(target.getId()) != 1) {
            throw new BusinessException("B0409", "用户删除失败，请刷新后重试");
        }
        managementMapper.logicallyDeleteUserRoles(id, securityContext.username());
        identityService.evict(id);
    }

    @Override
    @Transactional
    public void changeStatus(UserStatusRequest request) {
        SysUserEntity user = requireUser(request.id());
        assertCanManageProtectedUser(request.id());
        user.setVersion(request.version());
        user.setStatus(request.status());
        user.setTokenVersion(defaultZero(user.getTokenVersion()) + 1);
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException("B0409", "用户数据已被修改，请刷新后重试");
        }
        identityService.evict(request.id());
    }

    @Override
    @Transactional
    public void resetPassword(UserResetPasswordRequest request) {
        SysUserEntity user = requireUser(request.id());
        assertCanManageProtectedUser(request.id());
        user.setVersion(request.version());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(defaultZero(user.getTokenVersion()) + 1);
        user.setLoginFailCount(0);
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException("B0409", "用户数据已被修改，请刷新后重试");
        }
        userMapper.clearLoginLock(request.id());
        identityService.evict(request.id());
    }

    @Override
    @Transactional
    public void assignRoles(UserRoleAssignRequest request) {
        SysUserEntity user = requireUser(request.userId());
        assertCanManageProtectedUser(request.userId());
        Set<Long> roleIds = new LinkedHashSet<>(request.roleIds());
        if (!roleIds.isEmpty()) {
            List<SysRoleEntity> selectedRoles = roleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                    .in(SysRoleEntity::getId, roleIds)
                    .eq(SysRoleEntity::getStatus, 1));
            if (selectedRoles.size() != roleIds.size()) {
                throw new BusinessException("B0409", "包含不存在或已停用的角色");
            }
            boolean assigningSuperAdmin = selectedRoles.stream()
                    .anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()));
            if (assigningSuperAdmin && !isCurrentUserSuperAdmin()) {
                throw new AccessDeniedException("普通管理员不能分配系统管理员角色");
            }
        }
        managementMapper.logicallyDeleteUserRoles(request.userId(), securityContext.username());
        for (Long roleId : roleIds) {
            SysUserRoleEntity relation = new SysUserRoleEntity();
            relation.setUserId(request.userId());
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
        user.setTokenVersion(defaultZero(user.getTokenVersion()) + 1);
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException("B0409", "用户数据已被修改，请刷新后重试");
        }
        identityService.evict(request.userId());
    }

    @Override
    public List<RoleOptionVO> roleOptions() {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getStatus, 1)
                .ne(!isCurrentUserSuperAdmin(), SysRoleEntity::getRoleCode, "SUPER_ADMIN")
                .orderByAsc(SysRoleEntity::getRoleName);
        return roleMapper.selectList(wrapper)
                .stream().map(role -> new RoleOptionVO(role.getId(), role.getRoleName(), role.getRoleCode()))
                .toList();
    }

    @Override
    public List<OrgOptionVO> orgOptions() {
        return orgMapper.selectList(new LambdaQueryWrapper<SysOrgEntity>()
                        .eq(SysOrgEntity::getStatus, 1)
                        .ne(SysOrgEntity::getOrgType, "PARTY_ORG")
                        .orderByAsc(SysOrgEntity::getSortNo)
                        .orderByAsc(SysOrgEntity::getOrgName))
                .stream().map(org -> new OrgOptionVO(org.getId(), org.getOrgName(), org.getOrgCode()))
                .toList();
    }

    private List<UserVO> toVos(List<SysUserEntity> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(SysUserEntity::getId).toList();
        List<Long> orgIds = users.stream().map(SysUserEntity::getOrgId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> orgNames = orgIds.isEmpty() ? Map.of() : orgMapper.selectBatchIds(orgIds).stream()
                .collect(Collectors.toMap(SysOrgEntity::getId, SysOrgEntity::getOrgName));
        Map<Long, List<UserManagementMapper.UserRoleRow>> rolesByUser = managementMapper.selectUserRoles(userIds)
                .stream().collect(Collectors.groupingBy(UserManagementMapper.UserRoleRow::userId));
        return users.stream().map(user -> {
            List<UserManagementMapper.UserRoleRow> roles = rolesByUser.getOrDefault(user.getId(), List.of());
            return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(),
                    user.getOrgId(), orgNames.get(user.getOrgId()), user.getStatus(),
                    roles.stream().map(UserManagementMapper.UserRoleRow::roleId).toList(),
                    roles.stream().map(UserManagementMapper.UserRoleRow::roleName).toList(),
                    user.getCreateTime(), user.getUpdateTime(), user.getVersion());
        }).toList();
    }

    private SysUserEntity requireUser(Long id) {
        SysUserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("B0404", "用户不存在");
        }
        return user;
    }

    private void requireActiveOrg(Long orgId) {
        SysOrgEntity org = orgMapper.selectById(orgId);
        if (org == null || !Integer.valueOf(1).equals(org.getStatus())
                || "PARTY_ORG".equals(org.getOrgType())) {
            throw new BusinessException("B0409", "所属组织不存在或已停用");
        }
    }

    private void assertUsernameUnique(String username, Long excludedId) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getUsername, trim(username))
                .ne(excludedId != null, SysUserEntity::getId, excludedId);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("B0409", "用户名已存在");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void assertCanManageProtectedUser(Long targetUserId) {
        if (managementMapper.countSuperAdminRole(targetUserId) > 0 && !isCurrentUserSuperAdmin()) {
            throw new AccessDeniedException("普通管理员不能操作系统管理员");
        }
    }

    private boolean isCurrentUserSuperAdmin() {
        return managementMapper.countSuperAdminRole(securityContext.userId()) > 0;
    }
}
