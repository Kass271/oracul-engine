package com.oracul.app.reasoning;

import com.oracul.app.api.model.CriticIssue;
import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.EvidenceSection;
import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.GuardViolation;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.PackSourceItem;
import com.oracul.app.api.model.PackWildcardSection;
import com.oracul.app.api.model.WildcardPipelineKind;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.api.model.WildcardSetting;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pure (no Spring) driver of {@code ClosedEvidenceMode}, {@code ScenarioGenerationPrompt},
 * {@code StructuredScenarioParser} and {@code EvidenceGuard} for the slice 08 unit tests (scenario-reasoning.md
 * "Slice 08_validated-scenario"). The production classes are reached by reflection so RED compiles whatever they look
 * like; a missing class or method is an AssertionError naming it. Generated API models are used directly.
 */
public final class ReasoningHarness {

    public static final String PKG = "com.oracul.app.reasoning.";
    public static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-10-02T18:42:00Z");
    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ReasoningHarness() {}

    // ---- reflection -------------------------------------------------------------------------------------------

    public static Class<?> type(String name) {
        try {
            return Class.forName(PKG + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(PKG + name + " is missing");
        }
    }

    /** First existing class among top-level and nested homes of a record the spec does not pin to one file. */
    public static Class<?> typeAny(String simple, String... nestedIn) {
        List<String> names = new ArrayList<>(List.of(simple));
        for (String outer : nestedIn) names.add(outer + "$" + simple);
        for (String n : names) {
            try {
                return Class.forName(PKG + n);
            } catch (ClassNotFoundException ignored) {
                // try the next home
            }
        }
        throw new AssertionError(PKG + simple + " is missing (looked in " + names + ")");
    }

    public static Object constant(String className, String field) {
        Class<?> type = type(className);
        try {
            Field f = type.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(className + "." + field + " is missing");
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object target(Class<?> type, Method m) {
        if (Modifier.isStatic(m.getModifiers())) return null;
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                try {
                    c.setAccessible(true);
                    return c.newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(type.getSimpleName() + " cannot be instantiated: " + e, e);
                }
            }
        }
        throw new AssertionError(type.getSimpleName() + "." + m.getName() + " is not static and the class has no no-arg constructor");
    }

    public static Object call(String className, String method, Object... args) {
        Class<?> type = type(className);
        Method m = null;
        for (Method c : type.getDeclaredMethods()) {
            if (c.getName().equals(method) && c.getParameterCount() == args.length) m = c;
        }
        if (m == null) throw new AssertionError(className + "." + method + "(" + args.length + " args) is missing");
        try {
            m.setAccessible(true);
            return m.invoke(target(type, m), args);
        } catch (InvocationTargetException e) {
            throw new AssertionError(className + "." + method + " failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object accessor(Object record, String name) {
        try {
            Method m = record.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(record.getClass().getSimpleName() + "." + name + "() is missing: " + e, e);
        }
    }

    // ---- prompt -------------------------------------------------------------------------------------------------

    public static String closedEvidenceMode() {
        return (String) constant("ClosedEvidenceMode", "INSTRUCTIONS");
    }

    public static String instructions() {
        return (String) constant("ScenarioGenerationPrompt", "INSTRUCTIONS");
    }

    /** GenerationRequest(attempt, reason, schemaErrors, guardViolations[, criticIssues]) built reflectively. */
    public static Object generationRequest(int attempt, String reason, List<String> schemaErrors, List<GuardViolation> violations) {
        return generationRequest(attempt, reason, schemaErrors, violations, null);
    }

    /** With {@code criticIssues} non-null the record must have the 5th component of slice 10; null builds 4 or 5 components. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object generationRequest(int attempt, String reason, List<String> schemaErrors, List<GuardViolation> violations,
                                           List<CriticIssue> criticIssues) {
        Class<?> type = type("GenerationRequest");
        if (!type.isRecord()) throw new AssertionError("GenerationRequest must be a record");
        RecordComponent[] comps = type.getRecordComponents();
        int expected = criticIssues != null ? 5 : comps.length;
        if (comps.length != expected || (comps.length != 4 && comps.length != 5)) {
            throw new AssertionError("GenerationRequest must have " + (criticIssues != null ? 5 : "4 or 5")
                + " components (the 5th is criticIssues), has " + comps.length);
        }
        Object[] values = new Object[comps.length];
        Class<?>[] types = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) types[i] = comps[i].getType();
        values[0] = attempt;
        Class<?> reasonType = types[1];
        if (reasonType.isEnum()) values[1] = Enum.valueOf((Class<? extends Enum>) reasonType, reason);
        else throw new AssertionError("GenerationRequest.reason must be an enum, is " + reasonType);
        values[2] = new ArrayList<>(schemaErrors);
        values[3] = new ArrayList<>(violations);
        if (comps.length == 5) values[4] = new ArrayList<>(criticIssues == null ? List.of() : criticIssues);
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        } catch (InvocationTargetException e) {
            throw new AssertionError("GenerationRequest constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("GenerationRequest has an unexpected canonical constructor: " + e, e);
        }
    }

    public static Object initial() {
        return generationRequest(1, "INITIAL", List.of(), List.of());
    }

    public static String inputText(EvidencePack pack, Object request) {
        return (String) call("ScenarioGenerationPrompt", "inputText", pack, request);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> body(String model, EvidencePack pack, Object request) {
        return (Map<String, Object>) call("ScenarioGenerationPrompt", "body", model, pack, request);
    }

    // ---- alternative runs (slice 17) ------------------------------------------------------------------------------

    /** AvoidedFuture(title, steps) built reflectively. */
    public static Object avoided(String title, String... steps) {
        return avoided(title, List.of(steps));
    }

    public static Object avoided(String title, List<String> steps) {
        Class<?> type = typeAny("AvoidedFuture", "AvoidedFutures", "AlternativeDistinctness", "ScenarioGenerationPrompt");
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(String.class, List.class);
            ctor.setAccessible(true);
            return ctor.newInstance(title, new ArrayList<>(steps));
        } catch (InvocationTargetException e) {
            throw new AssertionError("AvoidedFuture constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("AvoidedFuture(String, List) is missing: " + e, e);
        }
    }

    /** Alternative(futuresToAvoid, rejectedTitle, duplicateFindings) built reflectively. */
    public static Object alternative(List<Object> futures, String rejectedTitle, List<String> findings) {
        Class<?> type = typeAny("Alternative", "ScenarioGenerationPrompt");
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(List.class, String.class, List.class);
            ctor.setAccessible(true);
            return ctor.newInstance(new ArrayList<>(futures), rejectedTitle, new ArrayList<>(findings));
        } catch (InvocationTargetException e) {
            throw new AssertionError("Alternative constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Alternative(List, String, List) is missing: " + e, e);
        }
    }

    public static Object alternativeNone() {
        try {
            Field f = typeAny("Alternative", "ScenarioGenerationPrompt").getDeclaredField("NONE");
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Alternative.NONE is missing: " + e, e);
        }
    }

    public static String inputText(EvidencePack pack, Object request, Object alt) {
        return (String) call("ScenarioGenerationPrompt", "inputText", pack, request, alt);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> body(String model, EvidencePack pack, Object request, Object alt) {
        return (Map<String, Object>) call("ScenarioGenerationPrompt", "body", model, pack, request, alt);
    }

    @SuppressWarnings("unchecked")
    public static List<String> findings(StructuredScenario candidate, List<Object> avoided) {
        return (List<String>) call("AlternativeDistinctness", "findings", candidate, new ArrayList<>(avoided));
    }

    // ---- critic (slice 10) ----------------------------------------------------------------------------------------

    public static String criticInstructions() {
        return (String) constant("ScenarioCriticPrompt", "INSTRUCTIONS");
    }

    public static String criticInputText(EvidencePack pack, StructuredScenario scenario, int attempt, String reason) {
        return (String) call("ScenarioCriticPrompt", "inputText", pack, scenario, attempt,
            com.oracul.app.api.model.ScenarioAttemptReason.valueOf(reason));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> criticBody(String model, EvidencePack pack, StructuredScenario scenario, int attempt, String reason) {
        return (Map<String, Object>) call("ScenarioCriticPrompt", "body", model, pack, scenario, attempt,
            com.oracul.app.api.model.ScenarioAttemptReason.valueOf(reason));
    }

    /** Verdict name and issues "TYPE | description" of a parsed critique. */
    record Critiqued(Optional<String> verdict, List<String> issues, List<String> errors) {}

    @SuppressWarnings("unchecked")
    public static Critiqued parseCritic(Optional<String> outputText) {
        Object result = call("CriticParser", "parse", outputText);
        Optional<Object> critique = (Optional<Object>) accessor(result, "critique");
        List<String> errors = (List<String>) accessor(result, "errors");
        if (critique.isEmpty()) return new Critiqued(Optional.empty(), List.of(), errors);
        Object c = critique.get();
        List<CriticIssue> issues = (List<CriticIssue>) accessor(c, "issues");
        return new Critiqued(Optional.of(String.valueOf(accessor(c, "verdict"))),
            issues.stream().map(i -> i.getType().name() + " | " + i.getDescription()).toList(), errors);
    }

    public static Critiqued parseCritic(String text) {
        return parseCritic(Optional.of(text));
    }

    // ---- parser -------------------------------------------------------------------------------------------------

    record Parsed(Optional<StructuredScenario> scenario, List<String> errors) {}

    @SuppressWarnings("unchecked")
    public static Parsed parse(Optional<String> outputText) {
        Object result = call("StructuredScenarioParser", "parse", outputText);
        return new Parsed((Optional<StructuredScenario>) accessor(result, "scenario"), (List<String>) accessor(result, "errors"));
    }

    public static Parsed parse(String text) {
        return parse(Optional.of(text));
    }

    // ---- guard --------------------------------------------------------------------------------------------------

    record Checked(GuardReport report, StructuredScenario cleaned) {
        /** "OUTCOME@attempt" */
        String head() {
            return report.getOutcome().name() + "@" + report.getAttempt();
        }

        List<String> violations() {
            return report.getViolations().stream().map(ReasoningHarness::line).toList();
        }
    }

    public static Checked check(StructuredScenario scenario, EvidencePack pack, int attempt, boolean finalAttempt) {
        Object result = call("EvidenceGuard", "check", scenario, pack, attempt, finalAttempt);
        return new Checked((GuardReport) accessor(result, "report"), (StructuredScenario) accessor(result, "cleaned"));
    }

    /** "TYPE | claimId or - | evidenceId or - | detail | ACTION" */
    public static String line(GuardViolation v) {
        return v.getType().name() + " | " + nz(v.getClaimId()) + " | " + nz(v.getEvidenceId()) + " | " + v.getDetail() + " | "
            + v.getAction().name();
    }

    public static String v(String type, String claimId, String evidenceId, String detail, String action) {
        return type + " | " + nz(claimId) + " | " + nz(evidenceId) + " | " + detail + " | " + action;
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    // ---- scenarios (JSON <-> model) -----------------------------------------------------------------------------

    public static StructuredScenario scenario(String json) {
        return MAPPER.readValue(json, StructuredScenario.class);
    }

    /** Scenario JSON with the mutation applied (document order preserved). */
    public static ObjectNode tree(String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }

    public static ObjectNode copy(StructuredScenario s) {
        return (ObjectNode) MAPPER.valueToTree(s);
    }

    /** JSON tree without null values and without an empty "unknowns" array: the comparable form of a scenario. */
    public static JsonNode comparable(Object scenarioOrJson) {
        JsonNode n = scenarioOrJson instanceof String s ? MAPPER.readTree(s) : MAPPER.valueToTree(scenarioOrJson);
        return strip(n);
    }

    private static JsonNode strip(JsonNode n) {
        if (n.isObject()) {
            ObjectNode out = MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> e : n.properties()) {
                JsonNode c = e.getValue();
                if (c.isNull()) continue;
                if (e.getKey().equals("unknowns") && c.isArray() && c.isEmpty()) continue;
                out.set(e.getKey(), strip(c));
            }
            return out;
        }
        if (n.isArray()) {
            var out = MAPPER.createArrayNode();
            for (JsonNode c : n) out.add(strip(c));
            return out;
        }
        return n;
    }

    // ---- packs --------------------------------------------------------------------------------------------------

    public static ScenarioConfiguration configA(HorizonCode horizon) {
        return new ScenarioConfiguration(8, 9, 2, horizon,
            new ArrayList<>(List.of(new WildcardSetting("biology-new-pandemic", 8), new WildcardSetting("robotics-humanoid-boom", 6))),
            new ArrayList<>(), new OutputSettings(true, false));
    }

    public static final ResearchTopic PANDEMIC = new ResearchTopic("biology-new-pandemic", "New pandemic", "biology", 0.8, false);
    public static final ResearchTopic HUMANOID = new ResearchTopic("robotics-humanoid-boom", "Humanoid robot boom", "robotics", 0.6, false);

    public static ResearchProfile profile(HorizonCode horizon, ResearchTopic... topics) {
        return new ResearchProfile(0.9, 0.2, 0.8, horizon, new ArrayList<>(List.of(topics)));
    }

    public static EvidenceItem item(String evidenceId, EvidenceSection section, String summary) {
        EvidenceItem i = new EvidenceItem(evidenceId, section, "EV00" + evidenceId.substring(evidenceId.length() - 1),
            "labour", summary, new ArrayList<>(List.of("Entity")), new ArrayList<>(List.of("S001")), 0.85, 0.8);
        i.setDate(LocalDate.parse("2026-10-01"));
        return i;
    }

    /** The V4 promptText exactly as the Evidence Pack renderer produces it for body A. */
    public static String v4PromptText(String summaryE001) {
        return String.join("\n",
            "ORACUL EVIDENCE PACK",
            "Generation: ORC-2026-10-02-1842",
            "Cutoff: 2026-10-02T18:42Z",
            "SCENARIO",
            "Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years",
            "WILDCARDS",
            "New pandemic: 8 | Humanoid robot boom: 6",
            "CORE EVIDENCE",
            "[E001] 2026-10-01 · labour · " + summaryE001 + " · sources: Stub Site (S004) · quality 0.85",
            "SUPPORTING EVIDENCE",
            "none",
            "COUNTER-SIGNALS",
            "[E002] 2026-10-01 · health · Health regulators approved a new pandemic vaccine. Reports differ on the number of doses approved."
                + " · sources: Stub Site (S001), Stub Site (S002), Stub Site (S003) · quality 0.95");
    }

    public static EvidencePack pack(ScenarioConfiguration cfg, ResearchProfile profile, String promptText) {
        return new EvidencePack(UUID.randomUUID(), "ORC-2026-10-02-1842", CUTOFF, cfg, profile,
            new ArrayList<>(List.of(item("E001", EvidenceSection.CORE, "Dock workers strike over humanoid robots."))),
            new ArrayList<>(),
            new ArrayList<>(List.of(item("E002", EvidenceSection.COUNTER_SIGNAL, "Health regulators approved a new pandemic vaccine."))),
            new ArrayList<>(), promptText);
    }

    /** The V4 pack under body A (horizon 5y, wildcards pandemic 8 and humanoid 6). */
    public static EvidencePack v4Pack() {
        return pack(configA(HorizonCode._5Y), profile(HorizonCode._5Y, PANDEMIC, HUMANOID),
            v4PromptText("Dock workers strike over humanoid robots."));
    }

    /** Fixture GP: V4 pack with the given horizon (profile and configuration agree). */
    public static EvidencePack gp(HorizonCode horizon) {
        return pack(configA(horizon), profile(horizon, PANDEMIC, HUMANOID), v4PromptText("Dock workers strike over humanoid robots."));
    }

    /**
     * A phase-03 pack (wildcard-evidence.md slice 06): core / supporting / counterSignals empty, one wildcard section per entry of
     * {@code itemsPerSection} holding that many items numbered E001... in section order (distinct sources S001...).
     */
    public static EvidencePack wildcardPack(int... itemsPerSection) {
        EvidencePack p = gp(HorizonCode._5Y);
        p.getCore().clear();
        p.getSupporting().clear();
        p.getCounterSignals().clear();
        List<PackWildcardSection> sections = new ArrayList<>();
        int next = 1;
        for (int j = 0; j < itemsPerSection.length; j++) {
            List<PackSourceItem> items = new ArrayList<>();
            for (int k = 0; k < itemsPerSection[j]; k++, next++) {
                PackSourceItem i = new PackSourceItem(String.format("E%03d", next), String.format("S%03d", next), "Title " + next,
                    "Publisher " + next, java.net.URI.create("https://www.reuters.com/s" + next), false, new ArrayList<>());
                i.setSnippet("Summary " + next);
                items.add(i);
            }
            PackWildcardSection s = new PackWildcardSection(String.format("W%02d", j + 1), WildcardPipelineKind.CATALOGUE, "Wildcard " + (j + 1),
                "Wildcard " + (j + 1) + " 5/10", items);
            s.setLevel(5);
            sections.add(s);
        }
        p.setWildcardSections(sections);
        return p;
    }

    public static EvidencePack withCustom(String label, int intensity) {
        ScenarioConfiguration cfg = configA(HorizonCode._5Y);
        cfg.setCustomWildcards(new ArrayList<>(List.of(new CustomWildcard(label, intensity))));
        ResearchProfile prof = profile(HorizonCode._5Y, PANDEMIC, HUMANOID,
            new ResearchTopic("custom-1", label, "custom", intensity / 10.0, true));
        return pack(cfg, prof, v4PromptText("Dock workers strike over humanoid robots."));
    }
}
