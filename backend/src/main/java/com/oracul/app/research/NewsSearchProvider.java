package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Runs all planned queries of a run (stage SEARCHING). */
public interface NewsSearchProvider {

    /** Outcome of one query. */
    record QueryResult(SearchQueryStatus status, List<NewsProvider.Article> articles) {
    }

    /** One result per text, in the same order; a null text is EMPTY without a request. */
    List<QueryResult> search(List<String> texts, HorizonCode horizon, SearchBudget budget, BooleanSupplier mayStart)
        throws InterruptedException;
}
