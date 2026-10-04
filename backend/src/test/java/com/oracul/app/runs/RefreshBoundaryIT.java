package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * phase-02 chatgpt-inference.md FR-38 "Refresh before use" with an injected clock: the access token is refreshed when
 * accessTokenExpiresAt <= now + refresh-skew (PT5M). Walks both sides of the boundary second by second.
 */
// @trace FR-38
class RefreshBoundaryIT extends AbstractDeadlineIT {

    /** remaining lifetime of the access token (seconds) -> refreshed? */
    static Stream<Arguments> remainingSeconds() {
        return IntStream.concat(IntStream.rangeClosed(295, 305), IntStream.of(3600, 1800, 600, 301, 60, 59, 30, 1, 0, -1, -60, -3600))
            .distinct().sorted().boxed().map(s -> Arguments.of(s, s <= 300));
    }

    @ParameterizedTest(name = "{0} s left -> refresh: {1}")
    @MethodSource("remainingSeconds")
    void theTokenIsRefreshedExactlyWhenFiveMinutesOrLessAreLeft(int remaining, boolean refreshed) throws Exception {
        stub.responder = req -> stub.ok(3600, com.oracul.app.chatgpt.StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        Instant signedIn = clock.instant();
        assertThat(stub.grant("refresh_token")).isEmpty();
        clock.set(signedIn.plus(Duration.ofSeconds(3600L - remaining))); // now + remaining = the token's expiry
        String id = (String) startOk(sid, A).get("id");
        awaitDone(sid, id);
        assertThat(stub.grant("refresh_token")).as(remaining + " s left").hasSize(refreshed ? 1 : 0);
        assertThat(responses.requests).isNotEmpty();
        String latestAccess = stub.issued.stream().filter(v -> v.startsWith("at-")).reduce((x, y) -> y).orElseThrow();
        assertThat(responses.requests.get(0).headers().get("authorization")).as("the call uses the " + (refreshed ? "refreshed" : "sign-in") + " token")
            .isEqualTo("Bearer " + latestAccess);
    }
}
