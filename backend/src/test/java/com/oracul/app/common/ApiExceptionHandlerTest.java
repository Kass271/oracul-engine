package com.oracul.app.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// @trace FR-2
@WebMvcTest
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    // @trace FR-2
    @Test
    void malformedRunIdIsRunNotFound() throws Exception {
        mvc.perform(get("/api/runs/not-a-uuid"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // @trace FR-2
    @Test
    void unknownApiPathIsNotFoundApiError() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // @trace FR-2
    @Test
    void wrongMethodIsMethodNotAllowedApiError() throws Exception {
        mvc.perform(delete("/api/scenario/catalogue"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
