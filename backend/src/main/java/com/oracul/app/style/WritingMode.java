package com.oracul.app.style;

public enum WritingMode {
    OFFICIAL("Official / Briefing"),
    NARRATIVE("Narrative");

    private final String label;

    WritingMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
