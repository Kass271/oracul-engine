package com.oracul.app.research;

import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Pure construction of the template search plan: one pipeline per wildcard (wildcard-search.md FR-50). */
@Component
public class SearchPlanner {

    private final QueryTemplates templates = new QueryTemplates();

    public SearchPlanner() {
    }

    public SearchPlan plan(ResearchProfile profile, ScenarioConfiguration cfg) {
        List<ResearchTopic> topics = profile.getTopics();
        int n = topics.size();
        int q = n >= 9 ? 2 : 3;
        List<WildcardPipeline> pipelines = new ArrayList<>();
        int[] queryNo = {0};
        if (n == 0) {
            WildcardPipeline p = new WildcardPipeline("W01", WildcardPipelineKind.GENERAL, "General", "General",
                QueryExpansionMode.TEMPLATE_FALLBACK, List.of());
            p.setQueries(queries(templates.forPipeline(p, cfg), 3, queryNo));
            pipelines.add(p);
        } else {
            for (int i = 0; i < n; i++) {
                ResearchTopic t = topics.get(i);
                int level = (int) Math.round(t.getWeight() * 10);
                WildcardPipeline p = new WildcardPipeline(String.format("W%02d", i + 1),
                    t.getCustom() ? WildcardPipelineKind.CUSTOM : WildcardPipelineKind.CATALOGUE, t.getLabel(),
                    t.getLabel() + " " + level + "/10", QueryExpansionMode.TEMPLATE_FALLBACK, List.of())
                    .level(level).topicKey(t.getKey());
                p.setQueries(queries(templates.forPipeline(p, cfg), q, queryNo));
                pipelines.add(p);
            }
        }
        int budget = pipelines.stream().mapToInt(p -> p.getQueries().size()).sum();
        return new SearchPlan(budget, QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>()).pipelines(pipelines);
    }

    private static List<PipelineQuery> queries(List<String> texts, int q, int[] queryNo) {
        List<PipelineQuery> out = new ArrayList<>();
        for (int i = 0; i < q; i++) {
            out.add(new PipelineQuery(String.format("Q%02d", ++queryNo[0]), texts.get(i), SearchQueryStatus.PENDING,
                0));
        }
        return out;
    }
}
