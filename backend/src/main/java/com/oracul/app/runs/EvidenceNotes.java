package com.oracul.app.runs;

import com.oracul.app.api.model.EvidenceNote;
import com.oracul.app.api.model.EvidenceNoteKind;
import com.oracul.app.research.EvidenceSufficiency;
import com.oracul.app.research.MinCoreThresholds;
import java.util.Optional;

/** FR-47: the note a finished run carries when its evidence did not meet the realism. */
public final class EvidenceNotes {

    private EvidenceNotes() {
    }

    /** The decision taken when the Evidence Pack is stored. */
    public record Decision(EvidenceNoteKind kind, int coreItems, int coreNeeded, Integer suggestedRealism) {
    }

    /** {@code total} = core + supporting + counter-signals. Empty when the evidence meets the realism. */
    public static Optional<Decision> decide(int core, int total, int realism, MinCoreThresholds thresholds) {
        int needed = thresholds.forRealism(realism);
        if (total == 0) {
            return Optional.of(new Decision(EvidenceNoteKind.NO_EVIDENCE, 0, needed, null));
        }
        if (core < needed) {
            return Optional.of(new Decision(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, core, needed,
                EvidenceSufficiency.suggestedRealism(realism).stream().boxed().findFirst().orElse(null)));
        }
        return Optional.empty();
    }

    public static String message(EvidenceNoteKind kind, int realism, int coreItems, int coreNeeded) {
        if (kind == EvidenceNoteKind.NO_EVIDENCE) {
            return "No current news could be used — this future is speculative, not grounded in evidence.";
        }
        return "Realism " + realism + " couldn't be fully met: only " + coreItems + " core evidence "
            + (coreItems == 1 ? "item" : "items") + " (needs " + coreNeeded + "). This future is less grounded.";
    }

    public static EvidenceNote note(EvidenceNoteKind kind, int realism, int coreItems, int coreNeeded) {
        return new EvidenceNote(kind, message(kind, realism, coreItems, coreNeeded), coreItems, coreNeeded);
    }
}
