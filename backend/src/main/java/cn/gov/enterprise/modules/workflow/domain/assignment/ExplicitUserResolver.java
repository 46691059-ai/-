package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.List;
import java.util.Objects;

/** Deterministic USER resolver. ROLE/POSITION/ORG resolvers are intentionally absent. */
public final class ExplicitUserResolver implements AssignmentResolver {
    public static final String VERSION = "EXPLICIT_USER_V1";
    public static final ResolverCode CODE = ResolverCode.of("EXPLICIT_USER");
    public static final ResolverVersion RESOLVER_VERSION = ResolverVersion.of(VERSION);
    public static final ResolverContractHash CONTRACT_HASH = ResolverContractHash.sha256(
            "EXPLICIT_USER|EXPLICIT_USER_V1|USER|DIRECT|SINGLE_POSITIVE_USER|"
                    + "TARGET_JSON_USER_ID|SORT_ASC");
    private static final AssignmentResolverDescriptor DESCRIPTOR =
            new AssignmentResolverDescriptor(CODE, RESOLVER_VERSION,
                    AssignmentStrategy.Type.USER, ResolverMode.DIRECT, CONTRACT_HASH,
                    ResolverStatus.ACTIVE, true);

    @Override
    public AssignmentResolverDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CandidatePool resolve(AssignmentResolverContext context) {
        Objects.requireNonNull(context, "context");
        AssignmentResult strategyResult = context.strategyResult();
        if (strategyResult.strategyType() != supportedType()
                || strategyResult.targetType() != supportedType()) {
            throw new IllegalArgumentException("ExplicitUserResolver only supports USER assignments");
        }
        Long userId = strategyResult.singleUserId();
        CandidateUser candidate = new CandidateUser(userId, supportedType(),
                "EXPLICIT_USER:" + userId, 1, strategyResult.resolveTime(), null);
        String auditInfo = "{\"reasonCode\":\"EXPLICIT_USER\",\"resolverCode\":\""
                + CODE.value() + "\",\"resolverVersion\":\"" + VERSION
                + "\",\"contractHash\":\"" + CONTRACT_HASH.value()
                + "\",\"resolvedBy\":\""
                + context.assignmentContext().resolvedBy() + "\"}";
        return new CandidatePool(supportedType(), supportedType(),
                strategyResult.targetSnapshot(), List.of(candidate), strategyResult.resolveTime(),
                null, VERSION, "EXPLICIT_USER", auditInfo);
    }
}
