package com.oracul.app.scenario;

import com.oracul.app.api.ScenarioApi;
import com.oracul.app.api.model.ScenarioCatalogue;
import com.oracul.app.api.model.ScenarioConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScenarioController implements ScenarioApi {

    @Override
    public ResponseEntity<ScenarioCatalogue> getScenarioCatalogue() {
        return ResponseEntity.ok(ScenarioCatalogueData.catalogue());
    }

    @Override
    public ResponseEntity<ScenarioConfiguration> getScenarioConfiguration() {
        return ResponseEntity.ok(ScenarioCatalogueData.defaults());
    }
}
