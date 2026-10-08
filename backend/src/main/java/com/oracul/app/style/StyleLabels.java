package com.oracul.app.style;

import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.StoryLanguage;
import org.springframework.stereotype.Component;

/** Display labels of a run's stored style and language (phase-04 FR-68); one helper for result and history. */
@Component
public class StyleLabels {

    public static final String LEGACY = "Official / Briefing (legacy)";

    private final WritingStyles styles;

    StyleLabels(WritingStyles styles) {
        this.styles = styles;
    }

    public String style(ScenarioConfiguration cfg) {
        var output = cfg == null ? null : cfg.getOutput();
        var selection = output == null ? null : output.getWritingStyle();
        if (selection == null || selection.getProfileId() == null) {
            return LEGACY;
        }
        return styles.find(selection.getProfileId()).map(WritingStyleProfile::label).orElse(selection.getProfileId());
    }

    public String language(ScenarioConfiguration cfg) {
        var output = cfg == null ? null : cfg.getOutput();
        return output != null && output.getLanguage() == StoryLanguage.UK ? "Ukrainian" : "English";
    }
}
