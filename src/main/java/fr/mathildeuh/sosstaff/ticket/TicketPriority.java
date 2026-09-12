package fr.mathildeuh.sosstaff.ticket;

public enum TicketPriority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent");

    private final String label;

    TicketPriority(String label) {
        this.label = label;
    }

    /** Human-readable form for chat/embeds, e.g. "Urgent" instead of "URGENT". */
    public String label() {
        return label;
    }
}
