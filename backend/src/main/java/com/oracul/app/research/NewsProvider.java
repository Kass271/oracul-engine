package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;

/** Current-news search; one implementation (GDELT) in the MVP. */
public interface NewsProvider {

    /** Raw provider article. Fields may be null. */
    record Article(String url, String title, String domain, String language, String seendate) {
    }

    /** {@code rateLimited}: the provider answered 429 (the one case that is retried). */
    record Result(SearchQueryStatus status, List<Article> articles, boolean rateLimited) {
        public Result(SearchQueryStatus status, List<Article> articles) {
            this(status, articles, false);
        }

        public static Result failed() {
            return new Result(SearchQueryStatus.FAILED, List.of());
        }

        public static Result limited() {
            return new Result(SearchQueryStatus.FAILED, List.of(), true);
        }
    }

    /**
     * One request: {@code query} is the complete GDELT query string (language filter included). The whole answer must
     * arrive within {@code timeout}. Never throws for provider problems: they are reported as FAILED.
     */
    Result search(String query, int maxRecords, HorizonCode horizon, java.time.Duration timeout);
}
