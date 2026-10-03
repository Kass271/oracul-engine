package com.oracul.app.reasoning;

import com.oracul.app.api.model.CriticIssue;
import com.oracul.app.api.model.GuardViolation;
import com.oracul.app.api.model.ScenarioAttemptReason;
import java.util.List;

/** What a SCENARIO_GENERATION call is for; the lists are empty when not used. */
public record GenerationRequest(int attempt, ScenarioAttemptReason reason, List<String> schemaErrors,
                                List<GuardViolation> guardViolations, List<CriticIssue> criticIssues) {
}
