package cn.gov.enterprise.modules.project.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LifecycleTemplateSelectionRow {
    private Long templateId;
    private Long templateVersionId;
    private String templateCode;
    private String templateName;
    private Integer versionNo;
    private String versionName;
    private String contentChecksum;
    private Integer selectionPriority;
}
