package com.oracul.app.style;

import static com.oracul.app.style.StyleFixtures.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-62: GET /api/writing-styles through the real application (shipped style file, real session filter, database).
 * The exact body, the declared key sets, no prompt text, independence of the session, and nothing stored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WritingStylesApiIT {

    private static final String PATH = "/api/writing-styles";
    private static final Pattern AUTHOR_LABEL = Pattern.compile("^Narrative — inspired by .+$");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private MvcResult call(Cookie cookie) throws Exception {
        var b = get(PATH);
        if (cookie != null) b.cookie(cookie);
        return mvc.perform(b).andExpect(status().isOk()).andReturn();
    }

    private static String text(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static Object json(String body) {
        return JsonPath.parse(body).json();
    }

    /** The exact catalogue of the shipped file, as the contract specifies it. */
    private static Map<String, Object> expected() {
        List<Object> modes = List.of(
            mode("OFFICIAL", "Official / Briefing", true, "official-briefing"),
            mode("NARRATIVE", "Narrative", false, "neutral-narrative"));
        List<Object> profiles = new ArrayList<>();
        for (StyleFixtures.Entry e : SHIPPED) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", e.id());
            p.put("mode", e.mode());
            p.put("label", e.label());
            p.put("characteristics", e.characteristics());
            p.put("isDefault", e.id().equals("official-briefing"));
            p.put("isModeDefault", e.id().equals("official-briefing") || e.id().equals("neutral-narrative"));
            profiles.add(p);
        }
        List<Object> languages = List.of(language("en", "English", true), language("uk", "Ukrainian", false));
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("modes", modes);
        all.put("profiles", profiles);
        all.put("languages", languages);
        return all;
    }

    private static Map<String, Object> mode(String id, String label, boolean isDefault, String defaultProfileId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("isDefault", isDefault);
        m.put("defaultProfileId", defaultProfileId);
        return m;
    }

    private static Map<String, Object> language(String code, String label, boolean isDefault) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("isDefault", isDefault);
        return m;
    }

    // @trace FR-62
    @Test
    void servesTheExactCatalogueOfTheShippedFile() throws Exception {
        MvcResult r = mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();
        assertThat(json(text(r))).isEqualTo(expected());
    }

    // @trace FR-62
    @Test
    void profilesComeInTheShippedOrder() throws Exception {
        assertThat(JsonPath.<List<String>>read(text(call(null)), "$.profiles[*].id")).containsExactly(
            "official-briefing", "neutral-narrative", "poe", "kafka", "wells", "verne", "orwell", "clarke", "harrison");
    }

    static Stream<StyleFixtures.Entry> shippedEntries() {
        return SHIPPED.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "profile {0}")
    @MethodSource("shippedEntries")
    void everyProfileHasItsExactKeysAndFlagsAndLabelShape(StyleFixtures.Entry e) throws Exception {
        String body = text(call(null));
        List<?> found = JsonPath.parse(body).read("$.profiles[?(@.id == '" + e.id() + "')]", List.class);
        assertThat(found).as("profile %s listed", e.id()).hasSize(1);
        Map<String, Object> p = castMap(found.get(0));
        assertThat(p.keySet()).containsExactlyInAnyOrder("id", "mode", "label", "characteristics", "isDefault", "isModeDefault");
        assertThat(p).containsEntry("mode", e.mode()).containsEntry("label", e.label())
            .containsEntry("characteristics", e.characteristics())
            .containsEntry("isDefault", e.id().equals("official-briefing"))
            .containsEntry("isModeDefault", e.id().equals("official-briefing") || e.id().equals("neutral-narrative"));
        boolean author = e.mode().equals("NARRATIVE") && !e.id().equals("neutral-narrative");
        assertThat(AUTHOR_LABEL.matcher((String) p.get("label")).matches()).as("author label shape of %s", e.id()).isEqualTo(author);
        assertThat(body).doesNotContain(e.instructions());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    // @trace FR-62
    @Test
    void promptTextIsNeverSent() throws Exception {
        String body = text(call(null));
        assertThat(body).doesNotContain("instructions");
        for (StyleFixtures.Entry e : SHIPPED) assertThat(body).doesNotContain(e.instructions());
    }

    // @trace FR-62
    @Test
    void theAnswerDoesNotDependOnTheSession() throws Exception {
        MvcResult first = call(null);
        String setCookie = first.getResponse().getHeader("Set-Cookie");
        Cookie own = null;
        if (setCookie != null) {
            Matcher m = Pattern.compile("ORACUL_SID=([^;]+)").matcher(setCookie);
            if (m.find()) own = new Cookie("ORACUL_SID", m.group(1));
        }
        String baseline = text(first);
        assertThat(text(call(own))).isEqualTo(baseline);
        assertThat(text(call(new Cookie("ORACUL_SID", java.util.UUID.randomUUID().toString())))).isEqualTo(baseline);
        assertThat(text(call(null))).isEqualTo(baseline);
        assertThat(json(baseline)).isEqualTo(expected());
    }

    // @trace FR-62
    @Test
    void theCatalogueIsNotStoredAnywhere() throws Exception {
        Long runs = jdbc.queryForObject("select count(*) from generation_run", Long.class);
        call(null);
        call(null);
        assertThat(jdbc.queryForObject("select count(*) from generation_run", Long.class)).isEqualTo(runs);
    }
}
