package cn.gov.enterprise.modules.system.log.support;

public final class AuditRequestContext {
    public static final String MODULE = AuditRequestContext.class.getName() + ".module";
    public static final String OPERATION = AuditRequestContext.class.getName() + ".operation";
    public static final String PARAMS = AuditRequestContext.class.getName() + ".params";
    public static final String WRITTEN = AuditRequestContext.class.getName() + ".written";
    private AuditRequestContext() {}
}
