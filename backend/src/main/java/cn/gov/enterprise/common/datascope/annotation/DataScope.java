package cn.gov.enterprise.common.datascope.annotation;

import cn.gov.enterprise.common.datascope.context.SelfValueType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明当前查询需要执行数据权限过滤。
 *
 * <p>字段名必须使用数据库列名，可携带一个安全的表别名，例如 {@code p.org_id}。
 * 现有基础表的 {@code create_by} 保存用户名，因此 SELF 默认按用户名过滤；
 * 对数值型归属字段应显式选择 {@link SelfValueType#USER_ID}。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    String orgField() default "org_id";
    String userField() default "create_by";
    SelfValueType selfValueType() default SelfValueType.USERNAME;
}
