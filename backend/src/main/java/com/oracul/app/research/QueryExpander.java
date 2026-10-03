package com.oracul.app.research;

import com.oracul.app.api.model.HorizonOption;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Replaces template query texts by model-written ones where possible (FR-12). */
@Component
public class QueryExpander {

    private static final Logger log = LoggerFactory.getLogger(QueryExpander.class);
    private static final int MAX_LENGTH = 120;

    private final HttpResponsesClient responses;
    private final QueryTemplates templates;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, String> horizonLabels = new HashMap<>();

    QueryExpander(HttpResponsesClient responses, QueryTemplates templates) {
        this.responses = responses;
        this.templates = templates;
        for (HorizonOption h : ScenarioCatalogueData.catalogue().getHorizons()) {
            horizonLabels.put(h.getCode().getValue(), h.getLabel());
        }
    }

    /** May throw ApiException (session expired); every other failure yields the template plan. */
    public SearchPlan expand(UUID sessionId, ResearchProfile profile, ScenarioConfiguration cfg, SearchPlan template) {
        String input = QueryExpansionPrompt.input(new QueryExpansionPrompt.ResearchProfileView(cfg,
            horizonLabels.get(cfg.getHorizon().getValue()), profile.getTopics()), template);
        Optional<String> text = responses.createText(sessionId, QueryExpansionPrompt.body(responses.model(), input));
        if (text.isEmpty()) {
            return template;
        }
        List<String[]> pairs = parse(text.get());
        if (pairs == null) {
            return template;
        }
        return merge(template, pairs);
    }

    private List<String[]> parse(String text) {
        try {
            JsonNode root = json.readTree(text);
            if (!root.isObject() || !root.path("queries").isArray()) {
                return null;
            }
            List<String[]> out = new ArrayList<>();
            for (JsonNode q : root.path("queries")) {
                if (!q.path("intentId").isString() || !q.path("text").isString()) {
                    return null;
                }
                out.add(new String[] {q.path("intentId").asString(), q.path("text").asString()});
            }
            return out;
        } catch (Exception e) {
            log.warn("query expansion output unreadable");
            return null;
        }
    }

    SearchPlan merge(SearchPlan template, List<String[]> pairs) {
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (SearchIntent in : template.getIntents()) {
            wanted.put(in.getId(), 0);
        }
        for (SearchQuery q : template.getQueries()) {
            wanted.merge(q.getIntentId(), 1, Integer::sum);
        }
        Map<String, List<String>> accepted = new HashMap<>();
        Set<String> used = new HashSet<>();
        int modelCount = 0;
        for (String[] pair : pairs) {
            String text = pair[1].trim().replaceAll("\\s+", " ");
            Integer n = wanted.get(pair[0]);
            if (text.isEmpty() || text.length() > MAX_LENGTH || n == null || n == 0) {
                continue;
            }
            List<String> list = accepted.computeIfAbsent(pair[0], k -> new ArrayList<>());
            if (list.size() >= n || !used.add(text.toLowerCase(Locale.ROOT))) {
                continue;
            }
            list.add(text);
            modelCount++;
        }
        List<SearchQuery> queries = new ArrayList<>();
        for (SearchIntent in : template.getIntents()) {
            List<String> list = accepted.computeIfAbsent(in.getId(), k -> new ArrayList<>());
            int n = wanted.get(in.getId());
            if (list.size() < n) {
                for (String candidate : templates.forIntent(in)) {
                    if (list.size() >= n) {
                        break;
                    }
                    if (used.add(candidate.toLowerCase(Locale.ROOT))) {
                        list.add(candidate);
                    }
                }
            }
            for (String text : list) {
                queries.add(new SearchQuery(String.format("Q%02d", queries.size() + 1), in.getId(), in.getBucket(),
                    text, SearchQueryStatus.PENDING, 0));
            }
        }
        return new SearchPlan(template.getQueryBudget(),
            modelCount > 0 ? QueryExpansionMode.MODEL : QueryExpansionMode.TEMPLATE_FALLBACK,
            template.getBuckets(), template.getIntents(), queries);
    }
}
