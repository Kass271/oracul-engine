package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** FR-55 "Relevant text extraction": the edges of {@code FragmentExtractor} that need no page (no body, one very long word). */
// @trace FR-55
@Timeout(30)
class FragmentExtractorEdgeTest {

    @ParameterizedTest(name = "content type {0} without a body")
    @ValueSource(strings = {"text/html", "text/plain", "application/xhtml+xml"})
    void aMissingBodyHasNoParagraphsAndNoFragments(String contentType) {
        assertThat(FragmentExtractor.paragraphs(null, contentType)).isEmpty();
        assertThat(FragmentExtractor.extract(null, contentType, Set.of("energy"), Set.of("grid"))).isEmpty();
    }

    @Test
    void aMissingBodyAndMissingContentTypeHasNoParagraphs() {
        assertThat(FragmentExtractor.paragraphs(null, null)).isEmpty();
    }

    @ParameterizedTest(name = "a word of {0} characters")
    @ValueSource(ints = {1201, 1300, 5000})
    void aVeryLongWordWithoutASpaceIsCutToTheFirst1199CharactersAndAnEllipsis(int length) {
        String word = "x".repeat(length);
        String cut = FragmentExtractor.cut(word);
        assertThat(cut).hasSize(FragmentExtractor.MAX_CHARS).isEqualTo("x".repeat(FragmentExtractor.MAX_CHARS - 1) + FragmentExtractor.ELLIPSIS);
    }

    @Test
    void aLongTextWhoseFirstSpaceIsBeyondTheLimitIsCutAtTheLimitNotAtThatSpace() {
        String text = "y".repeat(1300) + " tail";
        String cut = FragmentExtractor.cut(text);
        assertThat(cut).hasSize(FragmentExtractor.MAX_CHARS).isEqualTo("y".repeat(FragmentExtractor.MAX_CHARS - 1) + FragmentExtractor.ELLIPSIS);
    }
}
