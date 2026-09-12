package fr.mathildeuh.sosstaff.lang;

public enum Message {
    CREATION_REPORT_PROMPT("creation.report.prompt"),
    GENERAL_RELOAD_SUCCESS("general.reload-success"),
    GENERAL_RELOAD_FAILURE("general.reload-failure"),
    GENERAL_ERROR_GENERIC("general.error.generic"),

    TICKET_CREATE_SUCCESS("ticket.create.success"),
    TICKET_CREATE_UNKNOWN_CATEGORY("ticket.create.unknown-category"),
    TICKET_CREATE_REJECTED_TOO_MANY_OPEN("ticket.create.rejected.too-many-open"),
    TICKET_CREATE_REJECTED_COOLDOWN("ticket.create.rejected.cooldown"),
    TICKET_STATUS_NONE("ticket.status.none"),
    TICKET_STATUS_ACTIVE("ticket.status.active"),
    TICKET_LIST_EMPTY("ticket.list.empty"),
    TICKET_LIST_ENTRY("ticket.list.entry"),
    TICKET_CLOSE_NONE_ACTIVE("ticket.close.none-active"),
    TICKET_CLOSE_SUCCESS("ticket.close.success"),
    TICKET_CANCEL_NONE_ACTIVE("ticket.cancel.none-active"),
    TICKET_CANCEL_SUCCESS("ticket.cancel.success"),
    CHAT_STAFF_REPLY("chat.staff-reply"),

    CREATION_DEFAULT_PROMPT("creation.default.prompt"),
    CREATION_PROMPT_TIMED_OUT("creation.prompt-timed-out"),
    GUI_CREATION_TITLE("gui.creation.title"),
    GUI_CREATION_WRITE_YOUR_OWN("gui.creation.write-your-own"),
    GUI_ADMIN_PANEL_TITLE("gui.admin-panel.title"),
    GUI_ADMIN_PANEL_TICKET_LORE("gui.admin-panel.ticket-lore"),
    GUI_ADMIN_PANEL_FILTER_ALL("gui.admin-panel.filter-all"),
    ADMIN_ATTACHED_TO_TICKET("admin.attached-to-ticket"),
    ADMIN_NO_PERMISSION("admin.no-permission"),
    ADMIN_GDPR_ERASED("admin.gdpr.erased");

    private final String key;

    Message(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
