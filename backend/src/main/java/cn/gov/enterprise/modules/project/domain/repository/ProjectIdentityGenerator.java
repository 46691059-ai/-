package cn.gov.enterprise.modules.project.domain.repository;

/** Generates application-side aggregate identities before persistence. */
public interface ProjectIdentityGenerator {
    Long nextId();
}
