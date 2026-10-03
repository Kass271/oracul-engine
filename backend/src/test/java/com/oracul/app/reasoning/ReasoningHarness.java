package com.oracul.app.reasoning;

import com.oracul.app.api.model.CustomWildcard;
import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.EvidenceSection;
import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.GuardViolation;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
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
final class ReasoningHarness {

    static final String PKG = "com.oracul.app.reasoning.";
    static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-10-02T18:42:00Z");
    static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ReasoningHarness() {}

    // ---- reflection -------------------------------------------------------------------------------------------

    static Class<?> type(String name) {
        try {
            return Class.forName(PKG + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(PKG + name + " is missing");
        }
    }

    static Object constant(String className, String field) {
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

    static Object call(String className, String method, Object... args) {
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

    static String closedEvidenceMode() {
        return (String) constant("ClosedEvidenceMode", "INSTRUCTIONS");
    }

    static String instructions() {
        return (String) constant("ScenarioGenerationPrompt", "INSTRUCTIONS");
    }

    /** GenerationRequest(attempt, reason, schemaErrors, guardViolations) built reflectively. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object generationRequest(int attempt, String reason, List<String> schemaErrors, List<GuardViolation> violations) {
        Class<?> type = type("GenerationRequest");
        if (!type.isRecord()) throw new AssertionError("GenerationRequest must be a record");
        RecordComponent[] comps = type.getRecordComponents();
        if (comps.length != 4) throw new AssertionError("GenerationRequest must have 4 components, has " + comps.length);
        Object[] values = new Object[4];
        Class<?>[] types = new Class<?>[4];
        for (int i = 0; i < 4; i++) types[i] = comps[i].getType();
        values[0] = attempt;
        Class<?> reasonType = types[1];
        if (reasonType.isEnum()) values[1] = Enum.valueOf((Class<? extends Enum>) reasonType, reason);
        else throw new AssertionError("GenerationRequest.reason must be an enum, is " + reasonType);
        values[2] = new ArrayList<>(schemaErrors);
        values[3] = new ArrayList<>(violations);
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

    static Object initial() {
        return generationRequest(1, "INITIAL", List.of(), List.of());
    }

    static String inputText(EvidencePack pack, Object request) {
        return (String) call("ScenarioGenerationPrompt", "inputText", pack, request);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> body(String model, EvidencePack pack, Object request) {
        return (Map<String, Object>) call("ScenarioGenerationPrompt", "body", model, pack, request);
    }

    // ---- parser -------------------------------------------------------------------------------------------------

    record Parsed(Optional<StructuredScenario> scenario, List<String> errors) {}

    @SuppressWarnings("unchecked")
    static Parsed parse(Optional<String> outputText) {
        Object result = call("StructuredScenarioParser", "parse", outputText);
        return new Parsed((Optional<StructuredScenario>) accessor(result, "scenario"), (List<String>) accessor(result, "errors"));
    }

    static Parsed parse(String text) {
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

    static Checked check(StructuredScenario scenario, EvidencePack pack, int attempt, boolean finalAttempt) {
        Object result = call("EvidenceGuard", "check", scenario, pack, attempt, finalAttempt);
        return new Checked((GuardReport) accessor(result, "report"), (StructuredScenario) accessor(result, "cleaned"));
    }

    /** "TYPE | claimId or - | evidenceId or - | detail | ACTION" */
    static String line(GuardViolation v) {
        return v.getType().name() + " | " + nz(v.getClaimId()) + " | " + nz(v.getEvidenceId()) + " | " + v.getDetail() + " | "
            + v.getAction().name();
    }

    static String v(String type, String claimId, String evidenceId, String detail, String action) {
        return type + " | " + nz(claimId) + " | " + nz(evidenceId) + " | " + detail + " | " + action;
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    // ---- scenarios (JSON <-> model) -----------------------------------------------------------------------------

    static StructuredScenario scenario(String json) {
        return MAPPER.readValue(json, StructuredScenario.class);
    }

    /** Scenario JSON with the mutation applied (document order preserved). */
    static ObjectNode tree(String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }

    static ObjectNode copy(StructuredScenario s) {
        return (ObjectNode) MAPPER.valueToTree(s);
    }

    /** JSON tree without null values and without an empty "unknowns" array: the comparable form of a scenario. */
    static JsonNode comparable(Object scenarioOrJson) {
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

    static ScenarioConfiguration configA(HorizonCode horizon) {
        return new ScenarioConfiguration(8, 9, 2, horizon,
            new ArrayList<>(List.of(new WildcardSetting("biology-new-pandemic", 8), new WildcardSetting("robotics-humanoid-boom", 6))),
            new ArrayList<>(), new OutputSettings(true, false));
    }

    static final ResearchTopic PANDEMIC = new ResearchTopic("biology-new-pandemic", "New pandemic", "biology", 0.8, false);
    static final ResearchTopic HUMANOID = new ResearchTopic("robotics-humanoid-boom", "Humanoid robot boom", "robotics", 0.6, false);

    static ResearchProfile profile(HorizonCode horizon, ResearchTopic... topics) {
        return new ResearchProfile(0.9, 0.2, 0.8, horizon, new ArrayList<>(List.of(topics)));
    }

    static EvidenceItem item(String evidenceId, EvidenceSection section, String summary) {
        EvidenceItem i = new EvidenceItem(evidenceId, section, "EV00" + evidenceId.substring(evidenceId.length() - 1),
            "labour", summary, new ArrayList<>(List.of("Entity")), new ArrayList<>(List.of("S001")), 0.85, 0.8);
        i.setDate(LocalDate.parse("2026-10-01"));
        return i;
    }

    /** The V4 promptText exactly as the Evidence Pack renderer produces it for body A. */
    static String v4PromptText(String summaryE001) {
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

    static EvidencePack pack(ScenarioConfiguration cfg, ResearchProfile profile, String promptText) {
        return new EvidencePack(UUID.randomUUID(), "ORC-2026-10-02-1842", CUTOFF, cfg, profile,
            new ArrayList<>(List.of(item("E001", EvidenceSection.CORE, "Dock workers strike over humanoid robots."))),
            new ArrayList<>(),
            new ArrayList<>(List.of(item("E002", EvidenceSection.COUNTER_SIGNAL, "Health regulators approved a new pandemic vaccine."))),
            new ArrayList<>(), promptText);
    }

    /** The V4 pack under body A (horizon 5y, wildcards pandemic 8 and humanoid 6). */
    static EvidencePack v4Pack() {
        return pack(configA(HorizonCode._5Y), profile(HorizonCode._5Y, PANDEMIC, HUMANOID),
            v4PromptText("Dock workers strike over humanoid robots."));
    }

    /** Fixture GP: V4 pack with the given horizon (profile and configuration agree). */
    static EvidencePack gp(HorizonCode horizon) {
        return pack(configA(horizon), profile(horizon, PANDEMIC, HUMANOID), v4PromptText("Dock workers strike over humanoid robots."));
    }

    static EvidencePack withCustom(String label, int intensity) {
        ScenarioConfiguration cfg = configA(HorizonCode._5Y);
        cfg.setCustomWildcards(new ArrayList<>(List.of(new CustomWildcard(label, intensity))));
        ResearchProfile prof = profile(HorizonCode._5Y, PANDEMIC, HUMANOID,
            new ResearchTopic("custom-1", label, "custom", intensity / 10.0, true));
        return pack(cfg, prof, v4PromptText("Dock workers strike over humanoid robots."));
    }
}
