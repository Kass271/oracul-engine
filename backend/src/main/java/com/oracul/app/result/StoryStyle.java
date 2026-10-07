package com.oracul.app.result;

import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.StoryLanguage;
import com.oracul.app.style.Language;
import com.oracul.app.style.WritingStyleProfile;
import com.oracul.app.style.WritingStyles;

/** The effective writing style and language of a run (FR-65). */
public record StoryStyle(WritingStyleProfile profile, Language language) {

    private static final String OFFICIAL = "official-briefing";

    public static StoryStyle of(ScenarioConfiguration configuration, WritingStyles styles) {
        var output = configuration == null ? null : configuration.getOutput();
        var selection = output == null ? null : output.getWritingStyle();
        WritingStyleProfile profile = selection == null ? null : styles.find(selection.getProfileId()).orElse(null);
        if (profile == null) {
            profile = styles.find(OFFICIAL).orElseThrow();
        }
        StoryLanguage stored = output == null ? null : output.getLanguage();
        Language language = stored == StoryLanguage.UK ? Language.UK : Language.EN;
        return new StoryStyle(profile, language);
    }
}
