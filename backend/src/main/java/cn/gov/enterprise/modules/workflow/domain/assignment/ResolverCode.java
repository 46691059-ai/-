package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.regex.Pattern;

/** Stable resolver business key. */
public record ResolverCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    public ResolverCode {
        if (value == null || !FORMAT.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("resolver code must be an uppercase stable key");
        }
        value = value.trim();
    }

    public static ResolverCode of(String value) {
        return new ResolverCode(value);
    }
}
