package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable append-only state transition evidence. */
public record RuntimeBindingPromotionAudit(String promotionId, List<Entry> entries) {
    public RuntimeBindingPromotionAudit {
        promotionId = PersistentActivationCanonical.text(promotionId, "promotionId", 100);
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.isEmpty() || entries.getFirst().status() != RuntimeBindingPromotionStatus.CREATED) {
            throw new IllegalArgumentException("promotion audit must begin with CREATED");
        }
        for (int index = 1; index < entries.size(); index++) {
            if (!allowed(entries.get(index - 1).status(), entries.get(index).status())) {
                throw new IllegalArgumentException("illegal promotion audit transition");
            }
        }
    }

    public static RuntimeBindingPromotionAudit created(String promotionId, Instant at) {
        return new RuntimeBindingPromotionAudit(promotionId,
                List.of(Entry.of(promotionId, RuntimeBindingPromotionStatus.CREATED, "CREATED", at)));
    }

    public RuntimeBindingPromotionAudit append(
            RuntimeBindingPromotionStatus status, String reason, Instant at) {
        List<Entry> copy = new ArrayList<>(entries);
        copy.add(Entry.of(promotionId, status, reason, at));
        return new RuntimeBindingPromotionAudit(promotionId, copy);
    }

    public RuntimeBindingPromotionStatus currentStatus() {
        return entries.getLast().status();
    }

    private static boolean allowed(RuntimeBindingPromotionStatus source,
            RuntimeBindingPromotionStatus target) {
        if (target == RuntimeBindingPromotionStatus.BLOCKED) {
            return source == RuntimeBindingPromotionStatus.CREATED
                    || source == RuntimeBindingPromotionStatus.VALIDATING
                    || source == RuntimeBindingPromotionStatus.ELIGIBLE;
        }
        if (target == RuntimeBindingPromotionStatus.REJECTED) {
            return source == RuntimeBindingPromotionStatus.ELIGIBLE;
        }
        if (target == RuntimeBindingPromotionStatus.REVOKED) {
            return source == RuntimeBindingPromotionStatus.APPROVED
                    || source == RuntimeBindingPromotionStatus.PROMOTED;
        }
        return switch (source) {
            case CREATED -> target == RuntimeBindingPromotionStatus.VALIDATING;
            case VALIDATING -> target == RuntimeBindingPromotionStatus.ELIGIBLE;
            case ELIGIBLE -> target == RuntimeBindingPromotionStatus.APPROVED;
            case APPROVED -> target == RuntimeBindingPromotionStatus.PROMOTED;
            default -> false;
        };
    }

    public record Entry(RuntimeBindingPromotionStatus status, String reason,
                        Instant timestamp, String entryHash) {
        public Entry {
            Objects.requireNonNull(status, "status");
            reason = PersistentActivationCanonical.text(reason, "reason", 300);
            Objects.requireNonNull(timestamp, "timestamp");
            entryHash = PersistentActivationCanonical.hash(entryHash, "entryHash");
        }

        static Entry of(String promotionId, RuntimeBindingPromotionStatus status,
                String reason, Instant timestamp) {
            return new Entry(status, reason, timestamp,
                    RuntimeBindingPromotionCanonical.auditEntryHash(
                            promotionId, status, reason, timestamp.toString()));
        }
    }
}
