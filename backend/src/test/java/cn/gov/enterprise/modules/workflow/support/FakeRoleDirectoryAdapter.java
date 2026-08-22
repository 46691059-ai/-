package cn.gov.enterprise.modules.workflow.support;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryErrorCode;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryPort;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Scripted test double. It is deliberately located outside production sources. */
public final class FakeRoleDirectoryAdapter implements RoleDirectoryPort {
    private final Deque<Object> outcomes = new ArrayDeque<>();
    private int calls;

    public static FakeRoleDirectoryAdapter returning(RoleDirectoryResult... results) {
        FakeRoleDirectoryAdapter adapter = new FakeRoleDirectoryAdapter();
        adapter.outcomes.addAll(Arrays.asList(results));
        return adapter;
    }

    public FakeRoleDirectoryAdapter thenReturn(RoleDirectoryResult result) {
        outcomes.addLast(result);
        return this;
    }

    public FakeRoleDirectoryAdapter thenFail(RoleDirectoryErrorCode code) {
        outcomes.addLast(code);
        return this;
    }

    @Override
    public RoleDirectoryResult resolve(RoleDirectoryQuery query) {
        calls++;
        if (outcomes.isEmpty()) {
            throw new RoleDirectoryException(RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE,
                    "fake directory has no scripted outcome");
        }
        Object outcome = outcomes.removeFirst();
        if (outcome instanceof RoleDirectoryErrorCode code) {
            throw new RoleDirectoryException(code, "scripted fake directory failure");
        }
        return (RoleDirectoryResult) outcome;
    }

    public int calls() {
        return calls;
    }
}
