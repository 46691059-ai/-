package cn.gov.enterprise.modules.project.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.member.ProjectMember;
import cn.gov.enterprise.modules.project.domain.repository.ProjectMemberRepository;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis Plus member repository adapter. */
@Repository
public class ProjectMemberRepositoryImpl implements ProjectMemberRepository {
    private final ProjectMemberMapper mapper;

    public ProjectMemberRepositoryImpl(ProjectMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProjectMember> findPage(Long projectId, long offset, long limit) {
        long pageNumber = offset / limit + 1;
        return mapper.selectPage(
                        Page.of(pageNumber, limit, false),
                        new LambdaQueryWrapper<ProjectMemberEntity>()
                                .eq(ProjectMemberEntity::getProjectId, projectId)
                                .orderByAsc(ProjectMemberEntity::getCreateTime))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(Long projectId) {
        return mapper.selectCount(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId));
    }

    @Override
    public Optional<ProjectMember> findById(Long projectId, Long memberId) {
        ProjectMemberEntity entity = mapper.selectOne(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getId, memberId));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public boolean existsMembership(Long projectId, Long employeeId, String role) {
        return mapper.selectCount(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getEmployeeId, employeeId)
                .eq(ProjectMemberEntity::getRole, role)) > 0;
    }

    @Override
    public void insert(ProjectMember member) {
        if (mapper.insert(toEntity(member)) != 1) {
            throw new BusinessException("B0500", "项目成员保存失败");
        }
    }

    @Override
    public void softDelete(Long memberId, String operator) {
        if (mapper.softDelete(memberId, operator) != 1) {
            throw new BusinessException("B0001", "数据已被其他操作修改，请刷新后重试");
        }
    }

    private ProjectMember toDomain(ProjectMemberEntity entity) {
        return new ProjectMember(
                entity.getId(), entity.getProjectId(), entity.getEmployeeId(), entity.getRole(),
                entity.getResponsibilities(), entity.getJoinedDate(), entity.getLeftDate(),
                entity.getStatus(), entity.getRemark(), valueOrZero(entity.getVersion()));
    }

    private ProjectMemberEntity toEntity(ProjectMember member) {
        ProjectMemberEntity entity = new ProjectMemberEntity();
        entity.setId(member.getId());
        entity.setProjectId(member.getProjectId());
        entity.setEmployeeId(member.getEmployeeId());
        entity.setRole(member.getRole());
        entity.setResponsibilities(member.getResponsibilities());
        entity.setJoinedDate(member.getJoinedDate());
        entity.setLeftDate(member.getLeftDate());
        entity.setStatus(member.getStatus());
        entity.setRemark(member.getRemark());
        return entity;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
