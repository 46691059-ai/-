package cn.gov.enterprise.modules.project.domain.repository;

import cn.gov.enterprise.modules.project.domain.model.member.ProjectMember;
import java.util.List;
import java.util.Optional;

/** Persistence port for project member sub-aggregates. */
public interface ProjectMemberRepository {
    List<ProjectMember> findPage(Long projectId, long offset, long limit);
    long count(Long projectId);
    Optional<ProjectMember> findById(Long projectId, Long memberId);
    boolean existsMembership(Long projectId, Long employeeId, String role);
    void insert(ProjectMember member);
    void softDelete(Long memberId, String operator);
}
