package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.research.StubResponses;
import com.oracul.app.style.WritingMode;
import com.oracul.app.style.WritingStyleProfile;
import com.oracul.app.style.WritingStyles;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.web.servlet.MvcResult;

/**
 * phase-04 styled-story.md FR-65 / FR-66 through the real application: runs started with output.writingStyle and
 * output.language, the recorded STORY_WRITING request, the stored and served Ukrainian / English story.
 */
// @trace FR-65, FR-66
class StyledStoryIT extends AbstractStoryIT {

    private static final WritingStyles STYLES = new WritingStyles("classpath:writing-styles.json", new DefaultResourceLoader());
    private static final String KAFKA_LABEL = "Narrative — inspired by Franz Kafka";
    private static final String STORY_INVALID = "ORACUL could not construct a valid scenario";

    /** Acceptance body A with output.writingStyle / output.language; a null mode or language leaves the key out. */
    private static String body(String mode, String profileId, String language) {
        StringBuilder out = new StringBuilder("{\"story\":true,\"illustration\":false");
        if (mode != null) out.append(",\"writingStyle\":{\"mode\":\"").append(mode).append("\",\"profileId\":\"").append(profileId).append("\"}");
        if (language != null) out.append(",\"language\":\"").append(language).append('"');
        out.append('}');
        String a = A.replace("\"output\":{\"story\":true,\"illustration\":false}", "\"output\":" + out);
        assertThat(a).as("output replaced in body A").contains(out.toString());
        return a;
    }

    private static String sectionOf(String profileId, String language) {
        WritingStyleProfile p = STYLES.find(profileId).orElseThrow();
        return StoryFixtures.styleSection(p.mode() == WritingMode.OFFICIAL, p.label(), p.characteristics(), p.instructions(), "uk".equals(language));
    }

    /** A second run in the same session: fresh news and scripted research answers (the first run consumed them). */
    private Ran again(String sid, String body) throws Exception {
        news.reset();
        newsArticlesPerPipeline(v4(), 4);
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        return runWith(sid, body);
    }

    private static void assertCompletedRun(Ran r) {
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(r.run().get("stage")).isEqualTo("WRITING_STORY");
        assertThat(r.run().get("failure")).isNull();
    }

    private Ran startAlternative(Ran parent) throws Exception {
        MvcResult res = mvc.perform(post("/api/runs/" + parent.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", parent.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as("startAlternativeRun: " + res.getResponse().getContentAsString()).isEqualTo(202);
        String id = (String) json(res.getResponse().getContentAsString()).get("id");
        return new Ran(parent.sid(), id, awaitRun(parent.sid(), id, 30_000,
            m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status"))));
    }

    private static List<StubResponses.Request> storyOf(List<StubResponses.Request> all) {
        return all.stream().filter(r -> STORY.equals(StubResponses.purpose(r))).toList();
    }

    private static List<StubResponses.Request> nonStoryOf(List<StubResponses.Request> all) {
        return all.stream().filter(r -> !STORY.equals(StubResponses.purpose(r))).toList();
    }

    /** Generation id and pack cutoff minute are run-specific lines of the Evidence Pack text, not part of the style. */
    private static String normalized(String rawBody) {
        return rawBody.replaceAll("Generation: ORC-[0-9-]+", "Generation: ORC")
            .replaceAll("Cutoff: \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z", "Cutoff: T");
    }

    // ---- FR-65: the section of the run's style in the recorded request ----------------------------------------------

    // @trace FR-65
    @ParameterizedTest(name = "{0} / {1} / {2}")
    @CsvSource({
        "OFFICIAL, official-briefing, en",
        "OFFICIAL, official-briefing, uk",
        "NARRATIVE, neutral-narrative, en",
        "NARRATIVE, kafka, uk",
        "NARRATIVE, poe, en",
    })
    void theRecordedStoryRequestCarriesTheSectionOfTheRunsStyle(String mode, String profileId, String language) throws Exception {
        Ran r = runV4(body(mode, profileId, language));
        assertCompletedRun(r);
        String d = dayAfter(cutoffDate(r));
        assertThat(requests(STORY)).hasSize(1);
        assertThat(s(1)).contains("Future event date: " + d + "\n" + sectionOf(profileId, language) + "\nTASK\n"
            + "Write the story of the future event of the scenario in structured-scenario under the settings above.\n");
        assertThat(s(1).lines().filter("WRITING STYLE"::equals).count()).isEqualTo(1);
        // post-release correction 2026-10-08: the instructions end with the run's own WRITING STYLE section
        assertThat(instructionsOf(sreq(1))).isEqualTo(StoryFixtures.INSTRUCTIONS + "\n" + sectionOf(profileId, language));
        assertThat(storyModelCalls(r.id())).hasSize(1);
        assertThat((String) storyModelCalls(r.id()).get(0).get("body")).contains("Write the headline and the body in "
            + ("uk".equals(language) ? "Ukrainian." : "English."));
    }

    // @trace FR-65
    @Test
    void twoRunsOfOneSessionDifferOnlyInsideTheWritingStyleSection() throws Exception {
        Ran official = runV4(body("OFFICIAL", "official-briefing", "en"));
        assertCompletedRun(official);
        List<StubResponses.Request> first = new ArrayList<>(responses.requests);
        responses.requests.clear();
        Ran kafka = again(official.sid(), body("NARRATIVE", "kafka", "uk"));
        assertCompletedRun(kafka);
        List<StubResponses.Request> second = new ArrayList<>(responses.requests);

        // every request but STORY_WRITING: same purposes in the same order, pairwise equal bodies (as JSON)
        assertThat(second.stream().map(StubResponses::purpose).toList()).isEqualTo(first.stream().map(StubResponses::purpose).toList());
        Map<String, List<String>> a = new TreeMap<>();
        Map<String, List<String>> b = new TreeMap<>();
        for (StubResponses.Request q : nonStoryOf(first)) a.computeIfAbsent(StubResponses.purpose(q), k -> new ArrayList<>()).add(normalized(q.body()));
        for (StubResponses.Request q : nonStoryOf(second)) b.computeIfAbsent(StubResponses.purpose(q), k -> new ArrayList<>()).add(normalized(q.body()));
        assertThat(b.keySet()).isEqualTo(a.keySet());
        assertThat(a).isNotEmpty();
        for (String purpose : a.keySet()) {
            List<String> x = a.get(purpose).stream().sorted(Comparator.naturalOrder()).toList();
            List<String> y = b.get(purpose).stream().sorted(Comparator.naturalOrder()).toList();
            assertThat(y).as("%s requests of the second run", purpose).hasSameSizeAs(x);
            for (int i = 0; i < x.size(); i++) {
                assertThat((Object) JsonPath.read(y.get(i), "$")).as("%s request %d", purpose, i).isEqualTo(JsonPath.read(x.get(i), "$"));
            }
        }

        // no request but STORY_WRITING knows the style, the language or the profile
        List<String> forbidden = new ArrayList<>(List.of("WRITING STYLE", "Style profile:", "Ukrainian", "kafka"));
        STYLES.profiles().forEach(p -> forbidden.add(p.label()));
        for (StubResponses.Request q : nonStoryOf(first)) assertThat(q.body()).doesNotContain(forbidden.toArray(String[]::new));
        for (StubResponses.Request q : nonStoryOf(second)) assertThat(q.body()).doesNotContain(forbidden.toArray(String[]::new));
        Map<String, Object> pack = pack(kafka);
        assertThat((String) pack.get("promptText")).doesNotContain(forbidden.toArray(String[]::new));
        assertThat(String.valueOf(pack.get("profile"))).doesNotContain(forbidden.toArray(String[]::new));
        String research = getResearch(kafka.sid(), kafka.id()).andReturn().getResponse().getContentAsString();
        assertThat(research).doesNotContain(forbidden.toArray(String[]::new));

        // the two STORY_WRITING requests differ only inside the WRITING STYLE section
        assertThat(storyOf(first)).hasSize(1);
        assertThat(storyOf(second)).hasSize(1);
        String t1 = storyOf(first).get(0).inputText();
        String t2 = storyOf(second).get(0).inputText();
        String s1 = sectionOf("official-briefing", "en");
        String s2 = sectionOf("kafka", "uk");
        assertThat(t1).contains(s1 + "\n");
        assertThat(t2).contains(s2 + "\n");
        assertThat(t1).isNotEqualTo(t2);
        assertThat(t2.replace(s2 + "\n", "")).isEqualTo(t1.replace(s1 + "\n", ""));
        // post-release correction 2026-10-08: the instructions differ by style, equal once the trailing section is removed
        String i1 = instructionsOf(storyOf(first).get(0));
        String i2 = instructionsOf(storyOf(second).get(0));
        assertThat(i1).isEqualTo(StoryFixtures.INSTRUCTIONS + "\n" + s1);
        assertThat(i2).isEqualTo(StoryFixtures.INSTRUCTIONS + "\n" + s2);
        assertThat(i2).isNotEqualTo(i1);
        assertThat(StoryFixtures.withoutTrailingSection(i2, s2)).isEqualTo(StoryFixtures.withoutTrailingSection(i1, s1));
        // the structured-scenario block is the same accepted scenario
        assertThat(StubResponses.dataBlock(t2, "structured-scenario")).isEqualTo(StubResponses.dataBlock(t1, "structured-scenario"));
        assertThat(StubResponses.dataBlock(t2, "structured-scenario")).isNotEmpty();
    }

    // @trace FR-65
    @Test
    void anAlternativeOfTheKafkaUkrainianRunSendsTheKafkaUkrainianSection() throws Exception {
        Ran parent = runV4(body("NARRATIVE", "kafka", "uk"));
        assertCompletedRun(parent);
        Ran alt = startAlternative(parent);
        assertCompletedRun(alt);
        assertThat(requests(STORY)).hasSize(2);
        assertThat(sreq(2).inputText()).contains(sectionOf("kafka", "uk") + "\nTASK\n");
        assertThat(map(map(result(alt).get("story"))).get("headline")).isEqualTo(StoryFixtures.styledHeadline(KAFKA_LABEL, "uk"));
    }

    // @trace FR-65
    @Test
    void theStyleComesFromTheRunsOwnConfigurationNotFromTheEvidencePacksRootConfiguration() throws Exception {
        Ran parent = runV4(body("OFFICIAL", "official-briefing", "en"));
        assertCompletedRun(parent);
        // the pack still holds the root run's Official / Briefing configuration; the parent run row now says Kafka / uk,
        // and an ALTERNATIVE copies the parent run's configuration
        jdbc.update("update generation_run set configuration = cast(? as jsonb) where id = cast(? as uuid)",
            body("NARRATIVE", "kafka", "uk"), parent.id());
        Ran alt = startAlternative(parent);
        assertCompletedRun(alt);
        assertThat(requests(STORY)).hasSize(2);
        assertThat(sreq(1).inputText()).contains(sectionOf("official-briefing", "en") + "\nTASK\n");
        assertThat(sreq(2).inputText()).contains(sectionOf("kafka", "uk") + "\nTASK\n").doesNotContain(sectionOf("official-briefing", "en"));
        assertThat(map(map(result(alt).get("story"))).get("headline")).isEqualTo(StoryFixtures.styledHeadline(KAFKA_LABEL, "uk"));
    }

    // @trace FR-65
    @Test
    void bothAttemptsOfOneStageSendTheSameStyleSection() throws Exception {
        scriptStory(sfx("ST-149"), sfx("ST-DEFAULT"));
        Ran r = runV4(body("NARRATIVE", "kafka", "uk"));
        assertCompletedRun(r);
        assertThat(requests(STORY)).hasSize(2);
        String section = sectionOf("kafka", "uk");
        assertThat(s(1)).contains(section + "\nTASK\n");
        assertThat(s(2)).contains(section + "\nTASK\n");
        assertThat(s(2)).contains("Attempt: 2 | Reason: STORY_CORRECTION");
        assertThat(instructionsOf(sreq(2))).isEqualTo(instructionsOf(sreq(1)));
        assertThat(instructionsOf(sreq(2))).isEqualTo(StoryFixtures.INSTRUCTIONS + "\n" + section);
        assertThat(StubResponses.dataBlock(s(2), "story-errors")).isEqualTo("body must have 150 to 900 words (found 149)");
    }

    // ---- FR-66: the story in the chosen language ---------------------------------------------------------------------

    private void assertServedStory(Ran r, String headline, String body, String language) throws Exception {
        Map<String, Object> result = result(r);
        Map<String, Object> story = map(result.get("story"));
        String futureDate = (String) story.get("futureDate");
        assertThat(futureDate).isEqualTo(dayAfter(cutoffDate(r)));
        assertThat(story.get("headline")).isEqualTo(headline);
        assertThat(story.get("body")).isEqualTo(body);
        LocalDate date = LocalDate.parse(futureDate);
        String dateline = "uk".equals(language) ? StoryFixtures.ukDateline(date) : StoryFixtures.enDateline(date);
        assertThat(story.get("dateline")).isEqualTo(dateline);
        assertThat(result.get("labels")).isEqualTo(List.of(StoryFixtures.LABEL_1, StoryFixtures.LABEL_2));
        Map<String, Object> row = jdbc.queryForMap("select headline, dateline, cast(future_date as text) as future_date, body "
            + "from future_story where run_id = cast(? as uuid)", r.id());
        assertThat(row.get("headline")).isEqualTo(headline);
        assertThat(row.get("dateline")).isEqualTo(dateline);
        assertThat(row.get("future_date")).isEqualTo(futureDate);
        assertThat(row.get("body")).isEqualTo(body);
        assertThat(r.run().get("headline")).isEqualTo(headline);
    }

    // @trace FR-66
    @Test
    void aKafkaUkrainianRunStoresAndServesTheUkrainianStoryWithEnglishLabels() throws Exception {
        Ran r = runV4(body("NARRATIVE", "kafka", "uk"));
        assertCompletedRun(r);
        assertServedStory(r, "Заголовок із майбутнього — Narrative — inspired by Franz Kafka", StoryFixtures.ubody(3), "uk");
    }

    // @trace FR-66
    @ParameterizedTest(name = "kafka, language {0}")
    @CsvSource({"en", "absent"})
    void theSameRunInEnglishKeepsTheEnglishDatelineAndTheEnglishStub(String language) throws Exception {
        Ran r = runV4(body("NARRATIVE", "kafka", "absent".equals(language) ? null : language));
        assertCompletedRun(r);
        assertServedStory(r, "Stub headline from the future — Narrative — inspired by Franz Kafka", StoryFixtures.body(3), "en");
    }

    // @trace FR-66
    @ParameterizedTest(name = "date fallback, language {0}")
    @CsvSource({"uk", "en"})
    void theDateFallbackRebuildsTheDatelineInTheRunsLanguage(String language) throws Exception {
        scriptStory(sfx("BADDATE"), sfx("BADDATE"));
        Ran r = runV4(body("NARRATIVE", "kafka", language));
        assertCompletedRun(r);
        assertThat(requests(STORY)).hasSize(2);
        // BADDATE is ST-DEFAULT with an invalid date: the headline and body stay English, the dateline follows the language
        assertServedStory(r, StoryFixtures.HEADLINE, StoryFixtures.body(3), language);
    }

    // @trace FR-66
    @Test
    void aUkrainianStoryOutsideTheWordRangeFollowsTheRetryAndFailsWithInvalidScenario() throws Exception {
        Answer short149 = req -> StubResponses.completed(StoryFixtures.json(StoryFixtures.UK_HEADLINE,
            StoryFixtures.futureEventDate(req.inputText()), StoryFixtures.ubodyOfWords(149)));
        scriptStory(short149, short149);
        Ran r = runV4(body("NARRATIVE", "kafka", "uk"));
        assertFailed(r.run(), "INVALID_SCENARIO", STORY_INVALID, "WRITING_STORY", 10);
        assertThat(requests(STORY)).hasSize(2);
        assertThat(StubResponses.dataBlock(s(2), "story-errors")).isEqualTo("body must have 150 to 900 words (found 149)");
        assertThat(s(2)).contains(sectionOf("kafka", "uk"));
        assertThat(storyRows(r.id())).isZero();
        assertResultNotReady(getResult(r.sid(), r.id()));
    }

    // @trace FR-66
    @ParameterizedTest(name = "{0} Ukrainian words")
    @CsvSource({"150, true", "900, true", "149, false", "901, false"})
    void theWordRangeIsTheSameForUkrainianAsForEnglish(int words, boolean ok) throws Exception {
        Answer story = req -> StubResponses.completed(StoryFixtures.json(StoryFixtures.UK_HEADLINE,
            StoryFixtures.futureEventDate(req.inputText()), StoryFixtures.ubodyOfWords(words)));
        scriptStory(story, story);
        Ran r = runV4(body("NARRATIVE", "kafka", "uk"));
        if (ok) {
            assertCompletedRun(r);
            assertThat(requests(STORY)).hasSize(1);
            assertThat(map(result(r).get("story")).get("body")).isEqualTo(StoryFixtures.ubodyOfWords(words));
        } else {
            assertFailed(r.run(), "INVALID_SCENARIO", STORY_INVALID, "WRITING_STORY", 10);
            assertThat(requests(STORY)).hasSize(2);
            assertThat(StubResponses.dataBlock(s(2), "story-errors")).isEqualTo("body must have 150 to 900 words (found " + words + ")");
        }
    }
}
