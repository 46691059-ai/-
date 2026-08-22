package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowTaskAssignmentDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowAssignmentResolutionDetail;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowTaskAssignmentApplicationService {
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowTaskAssignmentSnapshotRepository snapshotRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final CurrentSecurityContext securityContext;

    public WorkflowTaskAssignmentApplicationService(
            WorkflowTaskRepository taskRepository,
            WorkflowTaskAssignmentSnapshotRepository snapshotRepository,
            WorkflowInstanceRepository instanceRepository,
            CurrentSecurityContext securityContext) {
        this.taskRepository = taskRepository;
        this.snapshotRepository = snapshotRepository;
        this.instanceRepository = instanceRepository;
        this.securityContext = securityContext;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowTaskAssignmentDetail query(Long taskId) {
        WorkflowTask task = requireAccessibleTask(taskId);
        return snapshotRepository.findByTaskId(taskId)
                .map(WorkflowTaskAssignmentDetail::structured)
                .orElseGet(() -> WorkflowTaskAssignmentDetail.legacy(task));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowAssignmentResolutionDetail queryResolution(Long taskId) {
        WorkflowTask task = requireAccessibleTask(taskId);
        return snapshotRepository.findByTaskId(taskId)
                .map(WorkflowAssignmentResolutionDetail::structured)
                .orElseGet(() -> WorkflowAssignmentResolutionDetail.legacy(task));
    }

    private WorkflowTask requireAccessibleTask(Long taskId) {
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("B2624", "workflow task does not exist"));
        WorkflowInstance instance = instanceRepository.findById(task.instanceId())
                .orElseThrow(() -> new BusinessException("B2624", "workflow instance does not exist"));
        requireAccessible(instance, securityContext.principal());
        return task;
    }

    private void requireAccessible(WorkflowInstance instance, SecurityPrincipal principal) {
        boolean accessible = principal.allDataScope()
                || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null
                    && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) {
            throw new BusinessException("B2623", "workflow task assignment is outside current data scope");
        }
    }
}
