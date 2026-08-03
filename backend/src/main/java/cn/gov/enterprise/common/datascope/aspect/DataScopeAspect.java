package cn.gov.enterprise.common.datascope.aspect;

import cn.gov.enterprise.common.datascope.annotation.DataScope;
import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataPermissionContextHolder;
import cn.gov.enterprise.common.datascope.context.DataScopeRule;
import cn.gov.enterprise.common.datascope.service.DataPermissionService;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 在进入受控Service/Controller方法前建立数据权限上下文。 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class DataScopeAspect {
    private final DataPermissionService dataPermissionService;

    public DataScopeAspect(DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
    }

    @Around("@annotation(cn.gov.enterprise.common.datascope.annotation.DataScope)"
            + " || @within(cn.gov.enterprise.common.datascope.annotation.DataScope)")
    public Object apply(ProceedingJoinPoint joinPoint) throws Throwable {
        DataScope annotation = resolveAnnotation(joinPoint);
        if (annotation == null) return joinPoint.proceed();
        DataPermissionContext context = DataPermissionContextHolder.current()
                .map(invocation -> invocation.context())
                .orElseGet(dataPermissionService::current);
        DataScopeRule rule = new DataScopeRule(annotation.orgField(), annotation.userField(), annotation.selfValueType());
        try (DataPermissionContextHolder.Scope ignored = DataPermissionContextHolder.push(context, rule)) {
            return joinPoint.proceed();
        }
    }

    private DataScope resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        DataScope annotation = AnnotatedElementUtils.findMergedAnnotation(method, DataScope.class);
        return annotation != null ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), DataScope.class);
    }
}
