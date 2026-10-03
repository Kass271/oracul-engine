package com.oracul.app.result;

import java.util.List;

/** What a STORY_WRITING call is for; attempt 1 has no errors. */
public record StoryRequest(int attempt, List<String> storyErrors) {
}
