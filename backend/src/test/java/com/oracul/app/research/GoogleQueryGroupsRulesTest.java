package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Missing-input rules of the pure OR-group helpers (FR-48). */
// @trace FR-48
@Timeout(10)
class GoogleQueryGroupsRulesTest {

    @Test
    void aMissingQueryTextHasNoElementAndIsNotSendable() {
        assertThat(GoogleQueryGroups.element(null)).isNull();
        assertThat(GoogleQueryGroups.element("  ")).isNull();
        assertThat(GoogleQueryGroups.element("OR AND NOT")).isNull();
        assertThat(GoogleQueryGroups.element("\"(solar)\" OR storage")).isEqualTo("\"solar storage\"");
    }

    @Test
    void aMissingTitleHasNoTokensAndFallsBackToTheFirstElement() {
        assertThat(GoogleQueryGroups.tokens(null)).isEmpty();
        assertThat(GoogleQueryGroups.tokens("  -- !! ")).isEmpty();
        assertThat(GoogleQueryGroups.attribute(List.of("\"solar power\"", "wind"), null)).isEqualTo(0);
        assertThat(GoogleQueryGroups.attribute(List.of("\"solar power\"", "wind"), "Wind farms grow")).isEqualTo(1);
    }
}
