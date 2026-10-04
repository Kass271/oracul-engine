package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-35: the documented authorize request — urn:uuid host id, /callback redirect, nonce, exact parameter set. */
class ChatGptAuthorizeIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    private static final Set<String> FIRST = Set.of("client_id", "agent_name_hint", "ext_agent_host_id", "response_type",
        "redirect_uri", "scope", "resource", "state", "nonce", "code_challenge_method", "code_challenge");
    private static final Set<String> REAUTH = Set.of("client_id", "ext_agent_host_id", "response_type", "redirect_uri",
        "scope", "resource", "state", "nonce", "code_challenge_method", "code_challenge");
    private static final String URN = "^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final String B64_43 = "^[A-Za-z0-9_-]{43}$";
    private static final int STARTS = 3;

    /** Parameter names exactly as they appear on the wire, in order (duplicates visible). */
    private static List<String> rawNames(String location) {
        List<String> names = new ArrayList<>();
        for (String pair : location.substring(location.indexOf('?') + 1).split("&")) {
            names.add(pair.substring(0, pair.indexOf('=')));
        }
        return names;
    }

    private void assertDocumentedRequest(Started s, Set<String> expectedNames, String clientId) {
        List<String> names = rawNames(s.location());
        assertThat(names).as("each parameter name exactly once, no extra parameter").hasSize(expectedNames.size());
        assertThat(new HashSet<>(names)).isEqualTo(expectedNames);
        assertThat(names).doesNotContain("id_token_hint", "login_hint");
        assertThat(s.params().get("client_id")).isEqualTo(clientId);
        assertThat(s.params().get("response_type")).isEqualTo("code");
        assertThat(s.params().get("redirect_uri")).isEqualTo("http://127.0.0.1:4200/callback");
        assertThat(s.params().get("resource")).isEqualTo("https://api.openai.com/v1");
        assertThat(s.params().get("scope"))
            .isEqualTo("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct");
        assertThat(s.location()).contains("scope=openid%20profile%20email").doesNotContain("+");
        assertThat(s.params().get("code_challenge_method")).isEqualTo("S256");
        assertThat(s.params().get("ext_agent_host_id")).hasSize(45).matches(URN);
        assertThat(s.state()).matches(B64_43);
        assertThat(s.nonce()).matches(B64_43);
        assertThat(s.challenge()).matches(B64_43);
        if (expectedNames.contains("agent_name_hint")) {
            assertThat(s.params().get("agent_name_hint")).isEqualTo("ORACUL");
        }
    }

    private void assertPairwiseDistinct(List<Started> starts) {
        assertThat(starts.stream().map(Started::state).distinct().count()).isEqualTo(starts.size());
        assertThat(starts.stream().map(Started::nonce).distinct().count()).isEqualTo(starts.size());
        assertThat(starts.stream().map(Started::challenge).distinct().count()).isEqualTo(starts.size());
        assertThat(starts.stream().map(s -> s.params().get("ext_agent_host_id")).distinct().count()).isEqualTo(1);
        assertThat(starts.stream().map(s -> s.params().get("redirect_uri")).distinct().count()).isEqualTo(1);
    }

    // @trace FR-35
    @Test
    void everyFirstRegistrationStartCarriesExactlyTheElevenDocumentedParameters() throws Exception {
        resetRegistration();
        String sid = newSid();
        List<Started> starts = new ArrayList<>();
        for (int i = 0; i < STARTS; i++) starts.add(start(sid));
        for (Started s : starts) assertDocumentedRequest(s, FIRST, "dynamic_agent_client");
        assertPairwiseDistinct(starts);
        assertThat(jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class)).isEqualTo(1);
        assertThat("urn:uuid:" + jdbc.queryForObject("select cast(host_id as text) from chatgpt_client_registration", String.class))
            .isEqualTo(starts.get(0).params().get("ext_agent_host_id"));

        // the S256 challenge matches the verifier later sent to the token endpoint; redirect_uri is the same one
        for (Started s : starts) {
            assertThat(callbackLocation(returnQuery(s, "code-" + s.state()))).isEqualTo(CONNECTED);
        }
        assertThat(stub.requests).hasSize(STARTS);
        for (int i = 0; i < STARTS; i++) {
            var form = stub.requests.get(i).form();
            assertThat(form.get("code_verifier")).matches("^[A-Za-z0-9_-]{86}$");
            assertThat(s256(form.get("code_verifier"))).isEqualTo(starts.get(i).challenge());
            assertThat(form.get("redirect_uri")).isEqualTo(starts.get(i).params().get("redirect_uri"));
        }
        assertThat(jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class)).isEqualTo(1);
    }

    // @trace FR-35
    @Test
    void everyReauthorizationStartCarriesExactlyTheTenDocumentedParameters() throws Exception {
        resetRegistration();
        String sid = newSid();
        Started first = start(sid);
        assertThat(callbackLocation("code=c0&state=" + enc(first.state()) + "&client_id=oaiapp_reauth_1")).isEqualTo(CONNECTED);
        stub.requests.clear();

        List<Started> starts = new ArrayList<>();
        for (int i = 0; i < STARTS; i++) starts.add(start(sid));
        for (Started s : starts) assertDocumentedRequest(s, REAUTH, "oaiapp_reauth_1");
        assertPairwiseDistinct(starts);
        assertThat(starts.get(0).params().get("ext_agent_host_id")).isEqualTo(first.params().get("ext_agent_host_id"));

        for (Started s : starts) {
            assertThat(callbackLocation(returnQuery(s, "code-" + s.state()))).isEqualTo(CONNECTED);
        }
        for (int i = 0; i < STARTS; i++) {
            var form = stub.requests.get(i).form();
            assertThat(form.get("client_id")).isEqualTo("oaiapp_reauth_1");
            assertThat(s256(form.get("code_verifier"))).isEqualTo(starts.get(i).challenge());
            assertThat(form.get("redirect_uri")).isEqualTo(starts.get(i).params().get("redirect_uri"));
        }
        assertThat(jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class)).isEqualTo(1);
    }

    // @trace FR-35
    @Test
    void aPhaseOneRowWithABareUuidIsReusedAndOnlyTheWireFormChanges() throws Exception {
        resetRegistration();
        UUID legacy = UUID.fromString("0b6d1f0e-52f4-4f0a-9a43-8f0f3d3a7c11");
        jdbc.update("insert into chatgpt_client_registration (host_id, created_at, updated_at) values (?, now(), now())", legacy);
        List<Started> starts = new ArrayList<>();
        for (int i = 0; i < STARTS; i++) starts.add(start(newSid()));
        for (Started s : starts) {
            assertThat(s.params().get("ext_agent_host_id")).isEqualTo("urn:uuid:" + legacy);
        }
        assertThat(jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select cast(host_id as text) from chatgpt_client_registration", String.class))
            .isEqualTo(legacy.toString());
    }

    // @trace FR-35
    @Test
    void aStoredIssuedClientIdMakesTheNextStartAReauthorizationWithTheSameUrnHostId() throws Exception {
        resetRegistration();
        UUID host = UUID.fromString("5f3c9a7e-1b2d-4c5e-8f60-0a1b2c3d4e5f");
        jdbc.update("insert into chatgpt_client_registration (host_id, client_id, created_at, updated_at) "
            + "values (?, 'oaiapp_preexisting', now(), now())", host);
        Started s = start(newSid());
        assertDocumentedRequest(s, REAUTH, "oaiapp_preexisting");
        assertThat(s.params().get("ext_agent_host_id")).isEqualTo("urn:uuid:" + host);
        assertThat(s.params()).doesNotContainKey("agent_name_hint");
    }

    // @trace FR-35
    @Test
    void redirectUriIsTheLoopbackCallbackAndNeverLocalhost() throws Exception {
        Started s = start(newSid());
        assertThat(s.params().get("redirect_uri")).isEqualTo("http://127.0.0.1:4200/callback").doesNotContain("localhost");
        assertThat(java.net.URI.create(s.params().get("redirect_uri")).getPath()).isEqualTo("/callback");
    }

    // @trace FR-35
    @Test
    void databaseUnavailableWhileEnsuringTheRegistrationRedirectsToNotCompleted() throws Exception {
        String sid = newSid();
        jdbc.execute("alter table chatgpt_client_registration rename to chatgpt_client_registration_off");
        try {
            mvc.perform(get("/api/auth/chatgpt/authorize").cookie(new Cookie("ORACUL_SID", sid)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", NOT_COMPLETED));
        } finally {
            jdbc.execute("alter table chatgpt_client_registration_off rename to chatgpt_client_registration");
        }
    }
}
