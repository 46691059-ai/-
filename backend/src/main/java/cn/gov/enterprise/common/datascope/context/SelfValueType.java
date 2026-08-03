package cn.gov.enterprise.common.datascope.context;

/** SELF 范围使用的当前用户标识类型。 */
public enum SelfValueType {
    /** 适用于 create_by 等保存登录用户名的现有审计字段。 */
    USERNAME,
    /** 适用于 owner_user_id、user_id 等数值型归属字段。 */
    USER_ID
}
