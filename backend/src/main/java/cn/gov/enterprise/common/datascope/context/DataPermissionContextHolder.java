package cn.gov.enterprise.common.datascope.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** 使用栈支持嵌套Service调用，并在调用结束后强制清理线程变量。 */
public final class DataPermissionContextHolder {
    private static final ThreadLocal<Deque<DataScopeInvocation>> HOLDER =
            ThreadLocal.withInitial(ArrayDeque::new);

    private DataPermissionContextHolder() {
    }

    public static Scope push(DataPermissionContext context, DataScopeRule rule) {
        HOLDER.get().push(new DataScopeInvocation(context, rule));
        return new Scope();
    }

    public static Optional<DataScopeInvocation> current() {
        return Optional.ofNullable(HOLDER.get().peek());
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (closed) return;
            Deque<DataScopeInvocation> stack = HOLDER.get();
            if (!stack.isEmpty()) stack.pop();
            if (stack.isEmpty()) HOLDER.remove();
            closed = true;
        }
    }
}
