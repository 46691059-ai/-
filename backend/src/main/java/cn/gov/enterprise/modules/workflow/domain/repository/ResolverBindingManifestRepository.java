package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import java.util.Optional;

/** Append/query-only persistence boundary for immutable release evidence. */
public interface ResolverBindingManifestRepository {
    void append(ResolverBindingManifest manifest);
    Optional<ResolverBindingManifest> findByDefinitionVersionId(Long definitionVersionId);
}
