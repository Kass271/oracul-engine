package com.oracul.app.runs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oracul.app.style.WritingStyles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * FR-62 "a profile added to the file is usable without any other code change", seen from startRun: a context started
 * with a style file that also lists the NARRATIVE profile dickens accepts it (and only with mode NARRATIVE).
 */
// @trace FR-63
@WebMvcTest
@Import(WritingStyles.class)
class StartRunWritingStyleDickensTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void styleFile(DynamicPropertyRegistry registry) {
        registry.add("oracul.writing-styles.location", () -> DickensStyleFile.LOCATION);
    }

    private ResultActions run(String style) throws Exception {
        return mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(
            "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":[],\"customWildcards\":[],"
                + "\"output\":{\"story\":true,\"illustration\":false,\"writingStyle\":" + style + "}}"));
    }

    // @trace FR-63
    @Test
    void narrativeDickensPassesValidation() throws Exception {
        run("{\"mode\":\"NARRATIVE\",\"profileId\":\"dickens\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("CHATGPT_NOT_CONNECTED"));
    }

    // @trace FR-63
    @Test
    void officialDickensIsRejected() throws Exception {
        run("{\"mode\":\"OFFICIAL\",\"profileId\":\"dickens\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Unknown writing style"));
    }

    // @trace FR-63
    @Test
    void theShippedProfilesStillPassWithTheExtendedFile() throws Exception {
        for (String id : DickensStyleFile.SHIPPED_IDS) {
            String mode = id.equals("official-briefing") ? "OFFICIAL" : "NARRATIVE";
            run("{\"mode\":\"" + mode + "\",\"profileId\":\"" + id + "\"}").andExpect(status().isUnauthorized());
        }
    }

    // @trace FR-63
    @Test
    void anIdThatIsInNeitherFileIsStillRejected() throws Exception {
        run("{\"mode\":\"NARRATIVE\",\"profileId\":\"dickens2\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Unknown writing style"));
    }
}
