package com.oracul.app.style;

/** Story language of a run. */
public enum Language {
    EN("en", "English"),
    UK("uk", "Ukrainian");

    private final String code;
    private final String label;

    Language(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
