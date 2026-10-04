package com.oracul.app.reasoning;

import com.oracul.app.api.model.CausalStep;
import com.oracul.app.api.model.CounterSignalConsideration;
import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.FactClaim;
import com.oracul.app.api.model.FutureEvent;
import com.oracul.app.api.model.GuardAction;
import com.oracul.app.api.model.GuardOutcome;
import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.GuardViolation;
import com.oracul.app.api.model.GuardViolationType;
import com.oracul.app.api.model.InferenceClaim;
import com.oracul.app.api.model.InformationClass;
import com.oracul.app.api.model.SpeculationClaim;
import com.oracul.app.api.model.StructuredScenario;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Deterministic Evidence Guard (FR-21): every factual claim must rest on the Evidence Pack. Pure; never mutates input. */
public final class EvidenceGuard {

    public record GuardResult(GuardReport report, StructuredScenario cleaned) {
    }

    private EvidenceGuard() {
    }

    public static GuardResult check(StructuredScenario scenario, EvidencePack pack, int attempt, boolean finalAttempt) {
        Set<String> known = new HashSet<>();
        Set<String> counterIds = new HashSet<>();
        for (EvidenceItem i : pack.getCore()) {
            known.add(i.getEvidenceId());
        }
        for (EvidenceItem i : pack.getSupporting()) {
            known.add(i.getEvidenceId());
        }
        for (EvidenceItem i : pack.getCounterSignals()) {
            known.add(i.getEvidenceId());
            counterIds.add(i.getEvidenceId());
        }
        // FR-47: an empty pack means speculative mode (three differences, see below)
        boolean speculative = pack.getCore().isEmpty() && pack.getSupporting().isEmpty()
            && pack.getCounterSignals().isEmpty();
        ScenarioWindow window = ScenarioWindow.of(pack);
        List<GuardViolation> violations = new ArrayList<>();
        boolean regeneration = false;

        // 1. facts
        Set<String> removedClaims = new HashSet<>();
        List<FactClaim> facts = new ArrayList<>();
        for (FactClaim f : scenario.getFactsUsed()) {
            String id = UntrustedText.id(f.getId());
            if (f.getEvidenceIds().isEmpty()) {
                violations.add(violation(GuardViolationType.FACT_WITHOUT_EVIDENCE, f.getId(), null,
                    id + " cites no Evidence ID", GuardAction.REMOVED));
                removedClaims.add(f.getId());
                continue;
            }
            Set<String> unknown = unknown(f.getEvidenceIds(), known);
            if (unknown.isEmpty()) {
                facts.add(new FactClaim(f.getId(), f.getStatement(), new ArrayList<>(f.getEvidenceIds())));
                continue;
            }
            for (String e : unknown) {
                violations.add(violation(GuardViolationType.UNKNOWN_EVIDENCE_ID, f.getId(), e,
                    id + " cites " + UntrustedText.id(e) + ", which is not in the Evidence Pack", GuardAction.REMOVED));
            }
            removedClaims.add(f.getId());
        }
        Set<String> remainingFacts = new HashSet<>();
        for (FactClaim f : facts) {
            remainingFacts.add(f.getId());
        }

        // 2. inferences
        List<InferenceClaim> inferences = new ArrayList<>();
        for (InferenceClaim inf : scenario.getInferences()) {
            String id = UntrustedText.id(inf.getId());
            Set<String> unknown = unknown(inf.getEvidenceIds(), known);
            if (!unknown.isEmpty()) {
                for (String e : unknown) {
                    violations.add(violation(GuardViolationType.UNSUPPORTED_INFERENCE, inf.getId(), e,
                        id + " cites " + UntrustedText.id(e) + ", which is not in the Evidence Pack",
                        GuardAction.REMOVED));
                }
                removedClaims.add(inf.getId());
                continue;
            }
            List<String> basedOn = new ArrayList<>();
            for (String b : inf.getBasedOn()) {
                if (remainingFacts.contains(b)) {
                    basedOn.add(b);
                }
            }
            if (!inf.getBasedOn().isEmpty() && basedOn.isEmpty()) {
                violations.add(violation(GuardViolationType.UNSUPPORTED_INFERENCE, inf.getId(), null,
                    id + " is based only on removed facts", GuardAction.REMOVED));
                removedClaims.add(inf.getId());
                continue;
            }
            if (inf.getBasedOn().isEmpty() && inf.getEvidenceIds().isEmpty()) {
                violations.add(violation(GuardViolationType.PRESENT_DAY_CLAIM_WITHOUT_EVIDENCE, inf.getId(), null,
                    id + " has neither facts nor Evidence IDs", GuardAction.REMOVED));
                removedClaims.add(inf.getId());
                continue;
            }
            inferences.add(new InferenceClaim(inf.getId(), inf.getStatement(), basedOn,
                new ArrayList<>(inf.getEvidenceIds())));
        }

        // 3. counter-signals
        List<CounterSignalConsideration> counters = new ArrayList<>();
        for (CounterSignalConsideration c : scenario.getCounterSignalsConsidered()) {
            if (counterIds.contains(c.getEvidenceId())) {
                counters.add(new CounterSignalConsideration(c.getEvidenceId(), c.getHowAddressed()));
            } else {
                violations.add(violation(GuardViolationType.UNKNOWN_EVIDENCE_ID, null, c.getEvidenceId(),
                    "counter-signal " + UntrustedText.id(c.getEvidenceId())
                        + " is not a counter-signal of the Evidence Pack", GuardAction.REMOVED));
            }
        }

        // 4./5. causal chain
        List<CausalStep> chain = new ArrayList<>();
        String shape = shapeError(scenario, speculative);
        if (shape != null) {
            violations.add(violation(GuardViolationType.CAUSAL_CHAIN_INVALID, null, null, "causal chain: " + shape,
                GuardAction.REGENERATION_REQUESTED));
            regeneration = true;
            for (CausalStep s : scenario.getCausalChain()) {
                if (s.getClaimId() == null || !removedClaims.contains(s.getClaimId())) {
                    chain.add(copy(s));
                }
            }
        } else {
            for (CausalStep s : scenario.getCausalChain()) {
                if (s.getClaimId() != null && removedClaims.contains(s.getClaimId())) {
                    continue;
                }
                if (s.getInformationClass() == InformationClass.FACT) {
                    String claim = UntrustedText.id(s.getClaimId());
                    if (s.getEvidenceIds().isEmpty()) {
                        violations.add(violation(GuardViolationType.PRESENT_DAY_CLAIM_WITHOUT_EVIDENCE, s.getClaimId(),
                            null, "causal step " + s.getOrder() + " states a fact without Evidence IDs",
                            GuardAction.REMOVED));
                        continue;
                    }
                    Set<String> unknown = unknown(s.getEvidenceIds(), known);
                    if (!unknown.isEmpty()) {
                        for (String e : unknown) {
                            violations.add(violation(GuardViolationType.UNKNOWN_EVIDENCE_ID, s.getClaimId(), e,
                                "causal step " + s.getOrder() + " cites " + UntrustedText.id(e)
                                    + ", which is not in the Evidence Pack", GuardAction.REMOVED));
                        }
                        continue;
                    }
                }
                chain.add(copy(s));
            }
            boolean anyFact = chain.stream().anyMatch(s -> s.getInformationClass() == InformationClass.FACT);
            if (!anyFact && !speculative) {
                violations.add(violation(GuardViolationType.CAUSAL_CHAIN_INVALID, null, null,
                    "causal chain: no FACT step remains", GuardAction.REGENERATION_REQUESTED));
                regeneration = true;
            } else if (chain.size() < 2) {
                violations.add(violation(GuardViolationType.CAUSAL_CHAIN_INVALID, null, null,
                    "causal chain: fewer than 2 steps remain", GuardAction.REGENERATION_REQUESTED));
                regeneration = true;
            }
        }
        for (int i = 0; i < chain.size(); i++) {
            chain.get(i).setOrder(i + 1);
        }

        // 6. future event
        FutureEvent fe = scenario.getFutureEvent();
        LocalDate d = fe.getDate();
        if (d == null || !d.isAfter(window.cutoff()) || d.isAfter(window.end())) {
            violations.add(violation(GuardViolationType.FUTURE_EVENT_NOT_IN_HORIZON, null, null,
                "futureEvent date " + d + " must be after " + window.cutoff() + " and no later than " + window.end(),
                GuardAction.REGENERATION_REQUESTED));
            regeneration = true;
        }

        // 7. outcome
        GuardOutcome outcome;
        if (violations.isEmpty()) {
            outcome = GuardOutcome.PASS;
        } else if (regeneration || (facts.isEmpty() && !speculative)) {
            outcome = GuardOutcome.FAIL;
        } else {
            outcome = GuardOutcome.PASS_WITH_REMOVALS;
        }
        if (finalAttempt && outcome == GuardOutcome.FAIL) {
            for (GuardViolation v : violations) {
                v.setAction(GuardAction.REJECTED);
            }
        }

        StructuredScenario cleaned = new StructuredScenario();
        cleaned.setCandidateFutures(new ArrayList<>(scenario.getCandidateFutures().stream()
            .map(c -> new com.oracul.app.api.model.CandidateFuture(c.getTitle(), c.getSummary(), c.getEvaluation(),
                c.getSelected())).toList()));
        cleaned.setFactsUsed(facts);
        cleaned.setInferences(inferences);
        cleaned.setSpeculations(new ArrayList<>(scenario.getSpeculations().stream()
            .map(p -> new SpeculationClaim(p.getId(), p.getStatement(), new ArrayList<>(p.getBasedOn()))).toList()));
        cleaned.setCounterSignalsConsidered(counters);
        cleaned.setCausalChain(chain);
        cleaned.setFutureEvent(new FutureEvent(fe.getTitle(), fe.getSummary(), fe.getDate()));
        cleaned.setUnknowns(new ArrayList<>(scenario.getUnknowns()));
        return new GuardResult(new GuardReport(outcome, violations, attempt), cleaned);
    }

    private static Set<String> unknown(List<String> ids, Set<String> known) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            if (!known.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** First broken chain shape rule, or null. */
    private static String shapeError(StructuredScenario s, boolean speculative) {
        List<CausalStep> chain = s.getCausalChain();
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i).getOrder() == null || chain.get(i).getOrder() != i + 1) {
                return "order must be 1..n";
            }
        }
        if (chain.isEmpty() || (!speculative && chain.get(0).getInformationClass() != InformationClass.FACT)) {
            return "first step must be FACT";
        }
        for (int i = 1; i < chain.size(); i++) {
            if (chain.get(i).getInformationClass().ordinal() < chain.get(i - 1).getInformationClass().ordinal()) {
                return "information classes must not go back";
            }
        }
        long events = chain.stream().filter(c -> c.getInformationClass() == InformationClass.FUTURE_EVENT).count();
        if (events != 1 || chain.get(chain.size() - 1).getInformationClass() != InformationClass.FUTURE_EVENT) {
            return "exactly one FUTURE_EVENT step, last";
        }
        CausalStep last = chain.get(chain.size() - 1);
        LocalDate date = s.getFutureEvent().getDate();
        if (last.getYear() == null || date == null || last.getYear() != date.getYear()) {
            return "FUTURE_EVENT step needs the year of futureEvent.date";
        }
        Set<String> facts = new HashSet<>();
        Set<String> inferences = new HashSet<>();
        Set<String> speculations = new HashSet<>();
        s.getFactsUsed().forEach(f -> facts.add(f.getId()));
        s.getInferences().forEach(f -> inferences.add(f.getId()));
        s.getSpeculations().forEach(f -> speculations.add(f.getId()));
        for (CausalStep step : chain) {
            String claim = step.getClaimId();
            boolean ok = switch (step.getInformationClass()) {
                case FACT -> claim != null && facts.contains(claim);
                case INFERENCE -> claim != null && inferences.contains(claim);
                case SPECULATION -> claim != null && speculations.contains(claim);
                case FUTURE_EVENT -> claim == null;
            };
            if (!ok) {
                return "step " + step.getOrder() + " refers to unknown claim "
                    + (claim == null ? "-" : UntrustedText.id(claim));
            }
        }
        return null;
    }

    private static CausalStep copy(CausalStep s) {
        CausalStep c = new CausalStep(s.getOrder(), s.getInformationClass(), s.getStatement(),
            new ArrayList<>(s.getEvidenceIds()));
        c.setClaimId(s.getClaimId());
        c.setYear(s.getYear());
        return c;
    }

    private static GuardViolation violation(GuardViolationType type, String claimId, String evidenceId, String detail,
                                            GuardAction action) {
        GuardViolation v = new GuardViolation(type, detail, action);
        v.setClaimId(claimId == null ? null : UntrustedText.id(claimId));
        v.setEvidenceId(evidenceId == null ? null : UntrustedText.id(evidenceId));
        return v;
    }
}
