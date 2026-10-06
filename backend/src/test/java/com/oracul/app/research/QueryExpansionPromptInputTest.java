package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** QUERY_EXPANSION input: only intents that need queries are listed (FR-12). */
// @trace FR-12
@Timeout(10)
class QueryExpansionPromptInputTest {

    @Test
    void anIntentWithoutPlannedQueriesIsNotAskedFor() {
        ScenarioConfiguration cfg = PlanSupport.cfgA();
        SearchIntent withQueries = new SearchIntent("I01", QueryBucket.MAJOR, "Major current world events", List.of("Horizon 5 years"));
        SearchIntent without = new SearchIntent("I02", QueryBucket.UNEXPECTED, "Unusual early signals", List.of("Horizon 5 years"));
        SearchPlan plan = new SearchPlan(2, com.oracul.app.api.model.QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>(),
            List.of(withQueries, without),
            List.of(new SearchQuery("Q01", "I01", QueryBucket.MAJOR, "world news", SearchQueryStatus.PENDING, 0),
                new SearchQuery("Q02", "I01", QueryBucket.MAJOR, "global events", SearchQueryStatus.PENDING, 0)));
        String input = QueryExpansionPrompt.input(
            new QueryExpansionPrompt.ResearchProfileView(cfg, "5 years", PlanSupport.profile(cfg).getTopics()), plan);
        assertThat(input).contains("- I01 | MAJOR | 2 | Major current world events");
        assertThat(input).doesNotContain("I02");
        assertThat(input).doesNotContain("Unusual early signals");
    }
}
