package fr.mathildeuh.sosstaff.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * A best-effort, log-only check against Modrinth's public API for a newer released version than
 * the one currently running. Runs once, asynchronously, on enable; any failure (network issue,
 * unexpected response shape, the project not being published yet) is swallowed and skipped -
 * this is a convenience for server admins, never something that should affect startup.
 */
public final class UpdateChecker {

    // TODO: replace with the real Modrinth project slug once this project has been published.
    private static final String MODRINTH_PROJECT_SLUG = "sos-staff";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Logger logger;

    public UpdateChecker(Logger logger) {
        this.logger = logger;
    }

    public void checkAsync() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version"))
                .timeout(TIMEOUT)
                .header("User-Agent", "SOS-Staff/" + VersionInfo.version() + " update-checker")
                .GET()
                .build();

        // client.close() only runs once the async call actually completes (success or failure) -
        // a try-with-resources block here would close it, and abort the request, immediately.
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(this::handleResponse)
                .exceptionally(throwable -> {
                    logger.fine("Update check failed, skipping: " + throwable);
                    return null;
                })
                .thenRun(client::close);
    }

    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return;
        }
        JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
        if (versions.isEmpty()) {
            return;
        }
        String latest = versions.get(0).getAsJsonObject().get("version_number").getAsString();
        String running = VersionInfo.version();
        if (!latest.equals(running)) {
            logger.info("A newer SOS-Staff version is available: " + latest + " (running " + running + ").");
        }
    }
}
