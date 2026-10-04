package com.oracul.app.research;

import org.springframework.test.context.TestPropertySource;

/** news-search.md FR-44 "Ranges & invariants": max-requests class 0 (values below 1 are treated as 1). */
// @trace FR-44
@TestPropertySource(properties = "oracul.news.max-requests=0")
class NewsSearchGroupingMax0IT extends NewsSearchGroupingIT {

    @Override
    protected int configuredMaxRequests() {
        return 0;
    }
}
