package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.text.Normalizer;

public record ApprovalRoleCode(String value) implements Comparable<ApprovalRoleCode> {
    public ApprovalRoleCode {
        if (value == null) throw new IllegalArgumentException("roleCode must not be null");
        value = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (!value.matches("[A-Z][A-Z0-9_]{2,99}")) {
            throw new IllegalArgumentException("roleCode must be an uppercase stable key");
        }
    }

    @Override public int compareTo(ApprovalRoleCode other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
