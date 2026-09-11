package fr.mathildeuh.sosstaff.lang;

public enum Message {
    CREATION_REPORT_PROMPT("creation.report.prompt"),
    GENERAL_RELOAD_SUCCESS("general.reload-success"),
    GENERAL_RELOAD_FAILURE("general.reload-failure"),
    GENERAL_ERROR_GENERIC("general.error.generic");

    private final String key;

    Message(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
