package com.oracul.app.reasoning;

import com.oracul.app.api.model.CandidateFuture;
import com.oracul.app.api.model.CausalStep;
import com.oracul.app.api.model.CounterSignalConsideration;
import com.oracul.app.api.model.FactClaim;
import com.oracul.app.api.model.FutureEvent;
import com.oracul.app.api.model.InferenceClaim;
import com.oracul.app.api.model.InformationClass;
import com.oracul.app.api.model.SpeculationClaim;
import com.oracul.app.api.model.StructuredScenario;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict parser of the model's output text into a StructuredScenario (FR-20). Pure. */
public final class StructuredScenarioParser {

    public record ParseResult(Optional<StructuredScenario> scenario, List<String> errors) {
    }

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build();

    private static final Pattern FACT = Pattern.compile("^F[1-9][0-9]*$");
    private static final Pattern INFERENCE = Pattern.compile("^I[1-9][0-9]*$");
    private static final Pattern SPECULATION = Pattern.compile("^P[1-9][0-9]*$");

    /** First mapping error; carries the exact message. */
    private static final class Invalid extends RuntimeException {
        Invalid(String message) {
            super(message, null, false, false);
        }
    }

    private StructuredScenarioParser() {
    }

    public static ParseResult parse(Optional<String> outputText) {
        if (outputText == null || outputText.isEmpty() || outputText.get().isBlank()) {
            return failure("no output text");
        }
        JsonNode root;
        try {
            root = JSON.readTree(outputText.get());
        } catch (RuntimeException e) {
            return failure("output is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            return failure("output is not valid JSON");
        }
        Mapped mapped;
        try {
            mapped = map(root);
        } catch (Invalid e) {
            return failure(e.getMessage());
        }
        StructuredScenario s = mapped.scenario();
        List<String> errors = structural(s, mapped.dateText());
        if (!errors.isEmpty()) {
            return new ParseResult(Optional.empty(), errors);
        }
        return new ParseResult(Optional.of(s), List.of());
    }

    private static ParseResult failure(String message) {
        return new ParseResult(Optional.empty(), List.of(message));
    }

    // ---- strict mapping ---------------------------------------------------------------------------------------------

    private record Mapped(StructuredScenario scenario, String dateText) {
    }

    private static Mapped map(JsonNode root) {
        object(root, "", List.of("candidateFutures", "factsUsed", "inferences", "speculations",
            "counterSignalsConsidered", "causalChain", "futureEvent", "unknowns"), List.of());
        StructuredScenario s = new StructuredScenario();
        List<CandidateFuture> candidates = new ArrayList<>();
        JsonNode arr = array(root, "candidateFutures", "candidateFutures");
        for (int i = 0; i < arr.size(); i++) {
            String p = "candidateFutures[" + i + "]";
            JsonNode n = arr.get(i);
            object(n, p, List.of("title", "summary", "evaluation", "selected"), List.of());
            candidates.add(new CandidateFuture(string(n, p, "title"), string(n, p, "summary"),
                string(n, p, "evaluation"), bool(n, p, "selected")));
        }
        s.setCandidateFutures(candidates);

        List<FactClaim> facts = new ArrayList<>();
        arr = array(root, "factsUsed", "factsUsed");
        for (int i = 0; i < arr.size(); i++) {
            String p = "factsUsed[" + i + "]";
            JsonNode n = arr.get(i);
            object(n, p, List.of("id", "statement", "evidenceIds"), List.of());
            facts.add(new FactClaim(string(n, p, "id"), string(n, p, "statement"), strings(n, p, "evidenceIds")));
        }
        s.setFactsUsed(facts);

        List<InferenceClaim> inferences = new ArrayList<>();
        arr = array(root, "inferences", "inferences");
        for (int i = 0; i < arr.size(); i++) {
            String p = "inferences[" + i + "]";
            JsonNode n = arr.get(i);
            object(n, p, List.of("id", "statement", "basedOn", "evidenceIds"), List.of());
            inferences.add(new InferenceClaim(string(n, p, "id"), string(n, p, "statement"),
                strings(n, p, "basedOn"), strings(n, p, "evidenceIds")));
        }
        s.setInferences(inferences);

        List<SpeculationClaim> speculations = new ArrayList<>();
        arr = array(root, "speculations", "speculations");
        for (int i = 0; i < arr.size(); i++) {
            String p = "speculations[" + i + "]";
            JsonNode n = arr.get(i);
            object(n, p, List.of("id", "statement", "basedOn"), List.of());
            speculations.add(new SpeculationClaim(string(n, p, "id"), string(n, p, "statement"),
                strings(n, p, "basedOn")));
        }
        s.setSpeculations(speculations);

        List<CounterSignalConsideration> counters = new ArrayList<>();
        arr = array(root, "counterSignalsConsidered", "counterSignalsConsidered");
        for (int i = 0; i < arr.size(); i++) {
            String p = "counterSignalsConsidered[" + i + "]";
            JsonNode n = arr.get(i);
            object(n, p, List.of("evidenceId", "howAddressed"), List.of());
            counters.add(new CounterSignalConsideration(string(n, p, "evidenceId"), string(n, p, "howAddressed")));
        }
        s.setCounterSignalsConsidered(counters);

        List<CausalStep> chain = new ArrayList<>();
        arr = array(root, "causalChain", "causalChain");
        for (int i = 0; i < arr.size(); i++) {
            String p = "causalChain[" + i + "]";
            JsonNode n = arr.get(i);
            object(n, p, List.of("order", "informationClass", "claimId", "statement", "evidenceIds", "year"),
                List.of("claimId", "year"));
            int order = integer(n, p, "order");
            String cls = string(n, p, "informationClass");
            InformationClass ic = null;
            for (InformationClass c : InformationClass.values()) {
                if (c.getValue().equals(cls)) {
                    ic = c;
                }
            }
            if (ic == null) {
                throw new Invalid(p + ".informationClass must be one of FACT, INFERENCE, SPECULATION, FUTURE_EVENT");
            }
            CausalStep step = new CausalStep(order, ic, string(n, p, "statement"), strings(n, p, "evidenceIds"));
            step.setClaimId(n.get("claimId").isNull() ? null : string(n, p, "claimId"));
            step.setYear(n.get("year").isNull() ? null : integer(n, p, "year"));
            chain.add(step);
        }
        s.setCausalChain(chain);

        JsonNode fe = root.get("futureEvent");
        object(fe, "futureEvent", List.of("title", "summary", "date"), List.of());
        String date = string(fe, "futureEvent", "date");
        FutureEvent event = new FutureEvent();
        event.setTitle(string(fe, "futureEvent", "title"));
        event.setSummary(string(fe, "futureEvent", "summary"));
        s.setFutureEvent(event);

        s.setUnknowns(strings(root, "", "unknowns"));
        LocalDate parsed = null;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            // reported by the structural rules
        }
        event.setDate(parsed);
        return new Mapped(s, date);
    }

    private static String path(String parent, String key) {
        return parent.isEmpty() ? key : parent + "." + key;
    }

    private static void object(JsonNode n, String path, List<String> required, List<String> nullable) {
        if (n == null || n.isNull()) {
            throw new Invalid("missing field " + path);
        }
        if (!n.isObject()) {
            throw new Invalid(path + " has the wrong type");
        }
        for (String name : n.propertyNames()) {
            if (!required.contains(name)) {
                throw new Invalid("unknown field " + path(path, UntrustedText.id(name)));
            }
        }
        for (String name : required) {
            JsonNode v = n.get(name);
            if (v == null || (v.isNull() && !nullable.contains(name))) {
                throw new Invalid("missing field " + path(path, name));
            }
        }
    }

    private static JsonNode array(JsonNode parent, String key, String path) {
        JsonNode v = parent.get(key);
        if (!v.isArray()) {
            throw new Invalid(path + " has the wrong type");
        }
        return v;
    }

    private static String string(JsonNode n, String parent, String key) {
        JsonNode v = n.get(key);
        if (!v.isString()) {
            throw new Invalid(path(parent, key) + " has the wrong type");
        }
        return v.asString();
    }

    private static boolean bool(JsonNode n, String parent, String key) {
        JsonNode v = n.get(key);
        if (!v.isBoolean()) {
            throw new Invalid(path(parent, key) + " has the wrong type");
        }
        return v.asBoolean();
    }

    private static int integer(JsonNode n, String parent, String key) {
        JsonNode v = n.get(key);
        if (!v.isIntegralNumber() || !v.canConvertToInt()) {
            throw new Invalid(path(parent, key) + " has the wrong type");
        }
        return v.intValue();
    }

    private static List<String> strings(JsonNode n, String parent, String key) {
        JsonNode v = n.get(key);
        String p = path(parent, key);
        if (!v.isArray()) {
            throw new Invalid(p + " has the wrong type");
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) {
            if (!v.get(i).isString()) {
                throw new Invalid(p + "[" + i + "] has the wrong type");
            }
            out.add(v.get(i).asString());
        }
        return out;
    }

    // ---- structural rules ---------------------------------------------------------------------------------------------

    private static List<String> structural(StructuredScenario s, String date) {
        List<String> errors = new ArrayList<>();
        if (s.getCandidateFutures().size() < 2) {
            errors.add("candidateFutures must contain at least 2 items");
        }
        if (s.getCandidateFutures().stream().filter(c -> Boolean.TRUE.equals(c.getSelected())).count() != 1) {
            errors.add("candidateFutures must have exactly 1 selected item");
        }
        if (s.getCausalChain().size() < 2) {
            errors.add("causalChain must contain at least 2 items");
        }
        for (int i = 0; i < s.getCandidateFutures().size(); i++) {
            CandidateFuture c = s.getCandidateFutures().get(i);
            String p = "candidateFutures[" + i + "]";
            blank(errors, p + ".title", c.getTitle());
            blank(errors, p + ".summary", c.getSummary());
            blank(errors, p + ".evaluation", c.getEvaluation());
        }
        for (int i = 0; i < s.getFactsUsed().size(); i++) {
            blank(errors, "factsUsed[" + i + "].statement", s.getFactsUsed().get(i).getStatement());
        }
        for (int i = 0; i < s.getInferences().size(); i++) {
            blank(errors, "inferences[" + i + "].statement", s.getInferences().get(i).getStatement());
        }
        for (int i = 0; i < s.getSpeculations().size(); i++) {
            blank(errors, "speculations[" + i + "].statement", s.getSpeculations().get(i).getStatement());
        }
        for (int i = 0; i < s.getCounterSignalsConsidered().size(); i++) {
            blank(errors, "counterSignalsConsidered[" + i + "].howAddressed",
                s.getCounterSignalsConsidered().get(i).getHowAddressed());
        }
        for (int i = 0; i < s.getCausalChain().size(); i++) {
            blank(errors, "causalChain[" + i + "].statement", s.getCausalChain().get(i).getStatement());
        }
        blank(errors, "futureEvent.title", s.getFutureEvent().getTitle());
        blank(errors, "futureEvent.summary", s.getFutureEvent().getSummary());
        blank(errors, "futureEvent.date", date);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < s.getFactsUsed().size(); i++) {
            String id = s.getFactsUsed().get(i).getId();
            if (!FACT.matcher(id).matches()) {
                errors.add("factsUsed[" + i + "].id must match F<n>");
            }
            ids.add(id);
        }
        for (int i = 0; i < s.getInferences().size(); i++) {
            String id = s.getInferences().get(i).getId();
            if (!INFERENCE.matcher(id).matches()) {
                errors.add("inferences[" + i + "].id must match I<n>");
            }
            ids.add(id);
        }
        for (int i = 0; i < s.getSpeculations().size(); i++) {
            String id = s.getSpeculations().get(i).getId();
            if (!SPECULATION.matcher(id).matches()) {
                errors.add("speculations[" + i + "].id must match P<n>");
            }
            ids.add(id);
        }
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                errors.add("duplicate claim id " + UntrustedText.id(id));
            }
        }
        if (s.getFutureEvent().getDate() == null && date != null && !date.isBlank()) {
            errors.add("futureEvent.date is not a valid date");
        }
        return errors;
    }

    private static void blank(List<String> errors, String path, String value) {
        if (value == null || value.isBlank()) {
            errors.add(path + " must not be blank");
        }
    }
}
