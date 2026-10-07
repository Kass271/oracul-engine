package com.oracul.app.style;

import com.oracul.app.api.WritingStylesApi;
import com.oracul.app.api.model.StoryLanguage;
import com.oracul.app.api.model.StoryLanguageOption;
import com.oracul.app.api.model.WritingModeOption;
import com.oracul.app.api.model.WritingStyleCatalogue;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WritingStylesController implements WritingStylesApi {

    private final ObjectProvider<WritingStyles> styles;

    WritingStylesController(ObjectProvider<WritingStyles> styles) {
        this.styles = styles;
    }

    @Override
    public ResponseEntity<WritingStyleCatalogue> getWritingStyles() {
        List<com.oracul.app.api.model.WritingStyleProfile> profiles = styles.getObject().profiles().stream()
            .map(p -> new com.oracul.app.api.model.WritingStyleProfile(p.id(),
                com.oracul.app.api.model.WritingMode.fromValue(p.mode().name()), p.label(), p.characteristics(),
                WritingStyles.OFFICIAL_ID.equals(p.id()),
                WritingStyles.OFFICIAL_ID.equals(p.id()) || WritingStyles.NEUTRAL_NARRATIVE_ID.equals(p.id())))
            .toList();
        List<WritingModeOption> modes = List.of(
            new WritingModeOption(com.oracul.app.api.model.WritingMode.OFFICIAL, "Official / Briefing", true,
                WritingStyles.OFFICIAL_ID),
            new WritingModeOption(com.oracul.app.api.model.WritingMode.NARRATIVE, "Narrative", false,
                WritingStyles.NEUTRAL_NARRATIVE_ID));
        List<StoryLanguageOption> languages = List.of(
            new StoryLanguageOption(StoryLanguage.EN, "English", true),
            new StoryLanguageOption(StoryLanguage.UK, "Ukrainian", false));
        return ResponseEntity.ok(new WritingStyleCatalogue(modes, profiles, languages));
    }
}
