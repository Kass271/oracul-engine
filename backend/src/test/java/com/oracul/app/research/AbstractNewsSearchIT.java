package com.oracul.app.research;

import com.oracul.app.TestcontainersConfiguration;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Driver of the FR-44 tests that call the search stage directly ({@code SourceRetrieval.search(plan, horizon)}, a seam that
 * exists today) without a run: the plan is built here, the GDELT stub records every request. No ChatGPT involved.
 * Subclasses pin the oracul.news.* properties of their context with {@code @TestPropertySource}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
abstract class AbstractNewsSearchIT {

    @Autowired
    protected SourceRetrieval retrieval;

    protected final StubGdelt gdelt = StubGdelt.INSTANCE;

    @DynamicPropertySource
    static void gdeltBaseUrl(DynamicPropertyRegistry r) {
        r.add("oracul.news.gdelt.base-url", StubGdelt.INSTANCE::baseUrl);
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * Isolation: runs left over by earlier test classes of this JVM (same database) would keep sending requests to the
     * shared stub. Wait for them to end (up to 30 s), force the rest to a terminal state, let in-flight requests land,
     * then reset the stub.
     */
    @BeforeEach
    void resetGdelt() throws Exception {
        String active = "select count(*) from generation_run where status in ('QUEUED','RUNNING')";
        long end = System.currentTimeMillis() + 30_000;
        while (jdbc.queryForObject(active, Integer.class) > 0 && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        if (jdbc.queryForObject(active, Integer.class) > 0) {
            jdbc.update("update generation_run set status = 'FAILED' where status in ('QUEUED','RUNNING')");
        }
        Thread.sleep(1500); // a request already on the wire of a just-ended run
        gdelt.reset();
    }

    /** A plan with one planned query per text: Q01...Qn, all in the first intent of the template plan. */
    protected static SearchPlan planOf(List<String> texts) {
        SearchPlan template = PlanSupport.plan(PlanSupport.cfgA(), 20);
        SearchQuery first = template.getQueries().get(0);
        List<SearchQuery> queries = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            queries.add(new SearchQuery(String.format("Q%02d", i + 1), first.getIntentId(), first.getBucket(), texts.get(i),
                SearchQueryStatus.EMPTY, 0));
        }
        return new SearchPlan(template.getQueryBudget(), template.getExpansionMode(), template.getBuckets(), template.getIntents(), queries);
    }

    /** n plan queries whose texts are two-word phrases "alpha<i> beta<i>" (so every element is a quoted phrase). */
    protected static SearchPlan planOfSize(int n) {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= n; i++) texts.add("alpha" + i + " beta" + i);
        return planOf(texts);
    }

    protected SourceRetrieval.SearchOutcome search(SearchPlan plan) throws InterruptedException {
        return retrieval.search(plan, HorizonCode._1Y);
    }

    protected static String article(String url, String title) {
        return StubGdelt.article(url, title, "reuters.com", "English", StubGdelt.seendate(java.time.Instant.now().minusSeconds(3600)));
    }
}
