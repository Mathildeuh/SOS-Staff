package fr.mathildeuh.sosstaff.util;

/**
 * The project's signature palette, as plain hex strings ready to drop into a MiniMessage tag
 * (e.g. {@code "<color:" + Colors.ACCENT + ">"}) or a Discord embed color. Kept as bare strings,
 * matching how category colors from config.yml are already used, rather than pre-parsed
 * TextColor/int values nothing else needs.
 *
 * <p>Most of this palette is actually applied through the {@code lang/*.yml} MiniMessage tags
 * and config.yml defaults rather than from Java directly (YAML obviously can't reference these
 * constants) - they're kept here anyway as the one place documenting the full palette, so a
 * future color change only needs updating in one spot conceptually, even though most call sites
 * are hardcoded hex in resource files today.
 */
public final class Colors {

    /** The SOS-Staff signature color - alerts, headers, anything that should stand out. */
    public static final String SIGNATURE = "#FF5A36";

    /** Discord-violet accent, used for neutral chrome (arrows, secondary labels). */
    public static final String ACCENT = "#5865F2";

    /** Positive/success status color. */
    public static final String POSITIVE = "#57F287";

    /** Negative/error status color. */
    public static final String NEGATIVE = "#ED4245";

    private Colors() {
    }
}
