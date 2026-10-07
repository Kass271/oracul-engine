package com.oracul.app.research;

import com.oracul.app.api.model.SearchQueryStatus;
import java.time.Duration;
import java.util.List;

/** Current-news search; one implementation (Google News RSS). */
public interface NewsProvider {

    /** Raw provider article. Fields may be null. */
    record Article(String url, String title, java.time.Instant publishedAt, String sourceName, String sourceUrl) {
    }

    record Result(SearchQueryStatus status, List<Article> articles, boolean rateLimited) {
        public Result(SearchQueryStatus status, List<Article> articles) {
            this(status, articles, false);
        }

        public static Result tooManyRequests() {
            return new Result(SearchQueryStatus.FAILED, List.of(), true);
        }

        public static Result failed() {
            return new Result(SearchQueryStatus.FAILED, List.of());
        }
    }

    /** One request: {@code q} is the complete query string. Never throws for provider problems: they are reported as FAILED. */
    Result search(String q, int maxItems, Duration timeout);
}
