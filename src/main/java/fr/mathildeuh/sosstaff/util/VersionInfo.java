package fr.mathildeuh.sosstaff.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class VersionInfo {

    private static final Properties PROPERTIES = load();

    private VersionInfo() {
    }

    public static String version() {
        return PROPERTIES.getProperty("version", "unknown");
    }

    public static String commitHash() {
        return PROPERTIES.getProperty("commit", "unknown");
    }

    public static String buildDate() {
        return PROPERTIES.getProperty("build-date", "unknown");
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = VersionInfo.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
            // Falls back to "unknown" defaults below.
        }
        return properties;
    }
}
