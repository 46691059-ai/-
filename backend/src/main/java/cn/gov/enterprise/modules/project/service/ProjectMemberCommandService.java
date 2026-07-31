package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberCommandService {
    private final ProjectMemberMapper memberMapper;
    private final ProjectAccessPolicy accessPolicy;
    private final ProjectReferenceValidator referenceValidator;
    private final ProjectLifecycleAssembler assembler;
    private final CurrentSecurityContext securityContext;

    public ProjectMemberCommandService(
            ProjectMemberMapper memberMapper,
            ProjectAccessPolicy accessPolicy,
            ProjectReferenceValidator referenceValidator,
            ProjectLifecycleAssembler assembler,
            CurrentSecurityContext securityContext) {
        this.memberMapper = memberMapper;
        this.accessPolicy = accessPolicy;
        this.referenceValidator = referenceValidator;
        this.assembler = assembler;
        this.securityContext = securityContext;
    }

    @Transactional
    public ProjectDtos.MemberResponse add(
            Long projectId, ProjectDtos.MemberRequest request) {
        accessPolicy.requireAccessible(projectId);
        validate(request);
        ensureUnique(projectId, null, request);
        ProjectMemberEntity member = new ProjectMemberEntity();
        apply(member, projectId, request);
        memberMapper.insert(member);
        return assembler.member(member);
    }

    @Transactional
    public ProjectDtos.MemberResponse update(
            Long projectId, Long memberId, ProjectDtos.MemberRequest request) {
        if (request.version() == null) {
            throw new BusinessException("B0001", "更新成员时版本号不能为空");
        }
        accessPolicy.requireAccessible(projectId);
        validate(request);
        ProjectMemberEntity member = requireMember(projectId, memberId);
        ensureUnique(projectId, memberId, request);
        apply(member, projectId, request);
        member.setVersion(request.version());
        if (memberMapper.updateById(member) == 0) {
            throw concurrentModification();
        }
        return assembler.member(memberMapper.selectById(memberId));
    }

    @Transactional
    public void delete(Long projectId, Long memberId) {
        ProjectEntity project = accessPolicy.requireAccessible(projectId);
        ProjectMemberEntity member = requireMember(projectId, memberId);
        if (member.getEmployeeId().equals(project.getLeaderId())
                && "MANAGER".equals(member.getRole())) {
            throw new BusinessException("B0001", "项目负责人不能从成员中移除");
        }
        if (memberMapper.softDelete(memberId, securityContext.username()) == 0) {
            throw concurrentModification();
        }
    }

    @Transactional
    public void synchronizeManager(
            Long projectId, Long previousEmployeeId, Long employeeId, LocalDate joinedDate) {
        if (previousEmployeeId != null && !previousEmployeeId.equals(employeeId)) {
            ProjectMemberEntity previous = memberMapper.selectOne(
                    managerQuery(projectId, previousEmployeeId));
            if (previous != null) {
                previous.setStatus("INACTIVE");
                previous.setLeftDate(LocalDate.now());
                update(previous);
            }
        }
        ProjectMemberEntity current = memberMapper.selectOne(managerQuery(projectId, employeeId));
        if (current == null) {
            current = new ProjectMemberEntity();
            current.setProjectId(projectId);
            current.setEmployeeId(employeeId);
            current.setRole("MANAGER");
            current.setResponsibilities("项目总体负责");
            current.setJoinedDate(joinedDate == null ? LocalDate.now() : joinedDate);
            current.setStatus("ACTIVE");
            memberMapper.insert(current);
        } else if (!"ACTIVE".equals(current.getStatus())) {
            current.setStatus("ACTIVE");
            current.setLeftDate(null);
            current.setJoinedDate(joinedDate == null ? LocalDate.now() : joinedDate);
            update(current);
        }
    }

    private LambdaQueryWrapper<ProjectMemberEntity> managerQuery(
            Long projectId, Long employeeId) {
        return new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getEmployeeId, employeeId)
                .eq(ProjectMemberEntity::getRole, "MANAGER");
    }

    private void ensureUnique(
            Long projectId, Long memberId, ProjectDtos.MemberRequest request) {
        long count = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getEmployeeId, request.employeeId())
                .eq(ProjectMemberEntity::getRole, request.role())
                .ne(memberId != null, ProjectMemberEntity::getId, memberId));
        if (count > 0) {
            throw new BusinessException("B0001", "该员工已具有相同项目角色");
        }
    }

    private void validate(ProjectDtos.MemberRequest request) {
        if (request.leftDate() != null && request.joinedDate().isAfter(request.leftDate())) {
            throw new BusinessException("B0001", "成员加入日期不能晚于退出日期");
        }
        referenceValidator.requireActiveEmployee(request.employeeId(), "项目成员");
    }

    private void apply(
            ProjectMemberEntity member, Long projectId, ProjectDtos.MemberRequest request) {
        member.setProjectId(projectId);
        member.setEmployeeId(request.employeeId());
        member.setRole(request.role());
        member.setResponsibilities(request.responsibilities());
        member.setJoinedDate(request.joinedDate());
        member.setLeftDate(request.leftDate());
        member.setStatus(request.status());
        member.setRemark(request.remark());
    }

    private ProjectMemberEntity requireMember(Long projectId, Long memberId) {
        ProjectMemberEntity member = memberMapper.selectOne(
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getId, memberId)
                        .eq(ProjectMemberEntity::getProjectId, projectId));
        if (member == null) {
            throw new BusinessException("B0404", "项目成员不存在");
        }
        return member;
    }

    private void update(ProjectMemberEntity member) {
        if (memberMapper.updateById(member) == 0) {
            throw concurrentModification();
        }
    }

    private BusinessException concurrentModification() {
        return new BusinessException("B0001", "数据已被其他操作修改，请刷新后重试");
    }
}
