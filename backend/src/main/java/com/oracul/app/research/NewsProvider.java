package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;

/** Current-news search; one implementation (GDELT) in the MVP. */
public interface NewsProvider {

    /** Raw provider article. Fields may be null. */
    record Article(String url, String title, String domain, String language, String seendate) {
    }

    record Result(SearchQueryStatus status, List<Article> articles) {
        public static Result failed() {
            return new Result(SearchQueryStatus.FAILED, List.of());
        }
    }

    /** Never throws for provider problems: they are reported as FAILED. */
    Result search(String queryText, HorizonCode horizon);
}
