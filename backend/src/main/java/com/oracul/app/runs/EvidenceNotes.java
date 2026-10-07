package com.oracul.app.runs;

import com.oracul.app.api.model.EvidenceNote;
import com.oracul.app.api.model.EvidenceNoteKind;
import com.oracul.app.research.EvidenceSufficiency;
import com.oracul.app.research.MinCoreThresholds;
import java.util.List;
import java.util.Optional;

/** FR-47: the note a finished run carries when its evidence did not meet the realism. */
public final class EvidenceNotes {

    private EvidenceNotes() {
    }

    /** The decision taken when the Evidence Pack is stored. */
    public record Decision(EvidenceNoteKind kind, int coreItems, int coreNeeded, Integer suggestedRealism,
                           List<String> wildcardsWithoutSources) {

        public Decision(EvidenceNoteKind kind, int coreItems, int coreNeeded, Integer suggestedRealism) {
            this(kind, coreItems, coreNeeded, suggestedRealism, List.of());
        }
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

    /** FR-59: every item of a wildcard-grouped pack counts as core; {@code missing} names the wildcards without sources. */
    public static Optional<Decision> decide(int total, List<String> missing, int realism, MinCoreThresholds thresholds) {
        int needed = thresholds.forRealism(realism);
        List<String> labels = List.copyOf(missing);
        if (total == 0) {
            return Optional.of(new Decision(EvidenceNoteKind.NO_EVIDENCE, 0, needed, null));
        }
        if (total < needed) {
            return Optional.of(new Decision(EvidenceNoteKind.INSUFFICIENT_EVIDENCE, total, needed,
                EvidenceSufficiency.suggestedRealism(realism).stream().boxed().findFirst().orElse(null), labels));
        }
        if (!labels.isEmpty()) {
            return Optional.of(new Decision(EvidenceNoteKind.MISSING_WILDCARD_SOURCES, total, needed, null, labels));
        }
        return Optional.empty();
    }

    public static String missingSentence(List<String> labels) {
        return "No current sources found for: " + String.join(", ", labels) + ". This part of the future is speculative.";
    }

    public static String message(EvidenceNoteKind kind, int realism, int coreItems, int coreNeeded) {
        return message(kind, realism, coreItems, coreNeeded, List.of());
    }

    public static String message(EvidenceNoteKind kind, int realism, int coreItems, int coreNeeded, List<String> wildcards) {
        if (kind == EvidenceNoteKind.NO_EVIDENCE) {
            return "No current news could be used — this future is speculative, not grounded in evidence.";
        }
        if (kind == EvidenceNoteKind.MISSING_WILDCARD_SOURCES) {
            return missingSentence(wildcards);
        }
        return (wildcards.isEmpty() ? "" : missingSentence(wildcards) + " ") + "Realism " + realism
            + " couldn't be fully met: only " + coreItems + " core evidence "
            + (coreItems == 1 ? "item" : "items") + " (needs " + coreNeeded + "). This future is less grounded.";
    }

    public static EvidenceNote note(EvidenceNoteKind kind, int realism, int coreItems, int coreNeeded) {
        return note(kind, realism, coreItems, coreNeeded, List.of());
    }

    public static EvidenceNote note(EvidenceNoteKind kind, int realism, int coreItems, int coreNeeded, List<String> wildcards) {
        EvidenceNote n = new EvidenceNote(kind, message(kind, realism, coreItems, coreNeeded, wildcards), coreItems, coreNeeded);
        if (kind != EvidenceNoteKind.NO_EVIDENCE && !wildcards.isEmpty()) {
            n.setWildcardsWithoutSources(List.copyOf(wildcards));
        }
        return n;
    }
}
