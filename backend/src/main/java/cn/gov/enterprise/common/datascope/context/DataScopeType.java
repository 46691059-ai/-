package cn.gov.enterprise.common.datascope.context;

import java.util.Locale;

/** 平台统一数据范围类型。未知值按SELF处理，确保权限计算失败时默认收紧。 */
public enum DataScopeType {
    ALL,
    ORG,
    ORG_AND_CHILDREN,
    SELF,
    CUSTOM;

    public static DataScopeType from(String value) {
        if (value == null || value.isBlank()) return SELF;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SELF;
        }
    }
}
