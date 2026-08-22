package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.regex.Pattern;

/** Immutable resolver contract version. */
public record ResolverVersion(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9_.-]{0,63}");

    public ResolverVersion {
        if (value == null || !FORMAT.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("resolver version is invalid");
        }
        value = value.trim();
    }

    public static ResolverVersion of(String value) {
        return new ResolverVersion(value);
    }
}
