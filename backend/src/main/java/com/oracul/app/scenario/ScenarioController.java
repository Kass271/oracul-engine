package com.oracul.app.scenario;

import com.oracul.app.api.ScenarioApi;
import com.oracul.app.api.model.ScenarioCatalogue;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.runs.GenerationRunRepository;
import com.oracul.app.session.CurrentSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScenarioController implements ScenarioApi {

    private final ObjectProvider<CurrentSession> session;
    private final ObjectProvider<GenerationRunRepository> runs;

    ScenarioController(ObjectProvider<CurrentSession> session, ObjectProvider<GenerationRunRepository> runs) {
        this.session = session;
        this.runs = runs;
    }

    @Override
    public ResponseEntity<ScenarioCatalogue> getScenarioCatalogue() {
        return ResponseEntity.ok(ScenarioCatalogueData.catalogue());
    }

    @Override
    public ResponseEntity<ScenarioConfiguration> getScenarioConfiguration() {
        CurrentSession current = session.getIfAvailable();
        GenerationRunRepository repo = runs.getIfAvailable();
        if (current == null || repo == null) {
            return ResponseEntity.ok(ScenarioCatalogueData.defaults());
        }
        return ResponseEntity.ok(repo.findNewestConfiguration(current.id()).orElseGet(ScenarioCatalogueData::defaults));
    }
}
