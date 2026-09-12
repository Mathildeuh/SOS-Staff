package fr.mathildeuh.sosstaff.ticket;

public enum TicketStatus {
    OPEN("Open"),
    CLAIMED("Claimed"),
    WAITING_PLAYER("Waiting for player"),
    WAITING_STAFF("Waiting for staff"),
    CLOSED("Closed"),
    ARCHIVED("Archived");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    /** Human-readable form for chat/embeds, e.g. "Waiting for player" instead of "WAITING_PLAYER". */
    public String label() {
        return label;
    }
}
