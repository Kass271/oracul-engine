package com.oracul.app.research;

import org.springframework.test.context.TestPropertySource;

/** news-search.md FR-44 "Ranges & invariants": max-requests class 10 (values below 1 are treated as 1). */
// @trace FR-44
@TestPropertySource(properties = "oracul.news.max-requests=10")
class NewsSearchGroupingMax10IT extends NewsSearchGroupingIT {

    @Override
    protected int configuredMaxRequests() {
        return 10;
    }
}
