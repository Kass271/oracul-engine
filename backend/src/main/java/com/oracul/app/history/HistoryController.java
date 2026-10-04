package com.oracul.app.history;

import com.oracul.app.api.HistoryApi;
import com.oracul.app.api.model.RecentRunList;
import com.oracul.app.api.model.RecentRunSummary;
import com.oracul.app.runs.GenerationRunRepository;
import com.oracul.app.session.CurrentSession;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoryController implements HistoryApi {

    private static final int LIMIT = 20;

    private final ObjectProvider<CurrentSession> session;
    private final ObjectProvider<GenerationRunRepository> runs;

    HistoryController(ObjectProvider<CurrentSession> session, ObjectProvider<GenerationRunRepository> runs) {
        this.session = session;
        this.runs = runs;
    }

    @Override
    public ResponseEntity<RecentRunList> listRecentRuns() {
        List<RecentRunSummary> items = runs.getObject().findRecent(session.getObject().id(), LIMIT).stream()
            .map(r -> new RecentRunSummary(r.id(), r.generationId(), r.kind(), r.createdAt(),
                r.configuration()).status(r.status())
                .headline(r.status() == com.oracul.app.api.model.RunStatus.COMPLETED ? r.headline() : null)
                .completedAt(r.completedAt()))
            .toList();
        return ResponseEntity.ok(new RecentRunList(items));
    }
}
