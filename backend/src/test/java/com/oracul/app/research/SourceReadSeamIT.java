package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.Source;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * FR-53 at the {@code SourceRetrieval} seam (article-retrieval.md "SourceRetrieval"): the new {@code read(SearchOutcome,
 * HorizonCode, BooleanSupplier)} returns {@code Read(sources, plan, articlesConsidered)}. A plan WITHOUT pipelines (the phase-01
 * shape that only {@code PlanSupport.legacyPlan} builds) is read as one implicit pipeline per planned query: its sources get no
 * pipelineIds, the returned plan is the input plan, and the selection still keeps at most 4 usable items per query. The method
 * and the record do not exist before the slice is built, so they are reached reflectively.
 */
// @trace FR-53
class SourceReadSeamIT extends AbstractNewsSearchIT {

    private record Read(List<Source> sources, SearchPlan plan, int articlesConsidered) {
    }

    @SuppressWarnings("unchecked")
    private Read read(SourceRetrieval.SearchOutcome outcome) throws Exception {
        Object result;
        try {
            Method m = SourceRetrieval.class.getMethod("read", SourceRetrieval.SearchOutcome.class, HorizonCode.class, BooleanSupplier.class);
            result = m.invoke(retrieval, outcome, HorizonCode._1Y, (BooleanSupplier) () -> true);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("SourceRetrieval.read(SearchOutcome, HorizonCode, BooleanSupplier) is missing (FR-53)");
        } catch (InvocationTargetException e) {
            throw new AssertionError("read failed: " + e.getTargetException(), e.getTargetException());
        }
        List<Object> stored = (List<Object>) accessor(result, "sources");
        List<Source> sources = new ArrayList<>();
        for (Object s : stored) sources.add((Source) accessor(s, "source"));
        return new Read(sources, (SearchPlan) accessor(result, "plan"), ((Number) accessor(result, "articlesConsidered")).intValue());
    }

    private static Object accessor(Object target, String name) throws Exception {
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(target.getClass().getName() + " has no accessor " + name + "()");
        }
    }

    private String item(String name) {
        return StubNews.rssItem("Story " + name, news.baseUrl() + "/rss/articles/" + name,
            StubNews.pubDate(java.time.Instant.now().minusSeconds(3600)), "Reuters", "https://www.reuters.com");
    }

    @Test
    void aLegacyPlanIsReadAsOneImplicitPipelinePerQueryAndKeepsFourPerQuery() throws Exception {
        news.responder = req -> switch (req.elements().get(0)) {
            case "alpha1 beta1" -> StubNews.rss(item("a"), item("b"), item("c"), item("d"), item("e"), item("f"));
            case "alpha2 beta2" -> StubNews.rss(item("c"), item("g"));
            default -> StubNews.rss();
        };
        SearchPlan input = planOfSize(3);
        SourceRetrieval.SearchOutcome outcome = search(input);
        Read read = read(outcome);

        // Q01 has 6 usable items: its implicit pipeline selects the first 4 (a, b, c, d); Q02 selects c (already kept) and g
        assertThat(read.sources().stream().map(s -> s.getUrl().toString().substring(s.getUrl().toString().lastIndexOf('/') + 1)).toList())
            .as("Evidence order: Q01's group, then Q02's").containsExactly("a", "b", "c", "d", "g");
        assertThat(read.articlesConsidered()).as("distinct usable links over all implicit pipelines").isEqualTo(7);
        assertThat(read.sources().stream().map(Source::getId).toList()).containsExactly("S001", "S002", "S003", "S004", "S005");
        Source c = read.sources().get(2);
        assertThat(c.getQueryIds()).as("c was returned by Q01 and Q02").isEqualTo(List.of("Q01", "Q02"));
        assertThat(read.sources()).allSatisfy(s -> assertThat(s.getPipelineIds()).as("no pipelineIds for a legacy plan").isNullOrEmpty());
        assertThat(read.plan()).as("the returned plan is the input plan (no pipeline fields)").isEqualTo(outcome.plan());
        assertThat(read.plan().getPipelines()).isNullOrEmpty();
    }

    @Test
    void readSourcesKeepsReturningTheSourcesOfRead() throws Exception {
        news.responder = req -> StubNews.rss(item("x-" + req.elements().get(0).charAt(5)));
        SourceRetrieval.SearchOutcome outcome = search(planOfSize(3));
        Read read = read(outcome);
        List<String> viaRead = read.sources().stream().map(s -> s.getUrl().toString()).toList();
        List<String> viaReadSources = retrieval.readSources(outcome, HorizonCode._1Y).stream().map(s -> s.source().getUrl().toString()).toList();
        assertThat(viaReadSources).as("readSources(...) returns read(...).sources()").isEqualTo(viaRead);
        assertThat(viaRead).hasSize(3);
    }
}
