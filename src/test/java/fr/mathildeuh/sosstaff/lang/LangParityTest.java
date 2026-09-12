package fr.mathildeuh.sosstaff.lang;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every shipped lang/*.yml must define exactly the same set of keys as en_US.yml, the pivot
 * language (config.yml's language.default). A file with a missing key falls back silently at
 * runtime via LangManager, and an extra key is dead weight left over from a rename - CI catches
 * both before they reach a release, per the project's parity requirement across the five
 * shipped languages.
 */
class LangParityTest {

    private static final String PIVOT = "en_US";
    private static final String[] LANGUAGES = {"en_US", "fr_FR", "es_ES", "ru_RU", "de_DE"};

    @Test
    void everyShippedLanguageHasExactlyThePivotsKeys() {
        Set<String> pivotKeys = keysOf(PIVOT);

        for (String language : LANGUAGES) {
            if (language.equals(PIVOT)) {
                continue;
            }
            Set<String> keys = keysOf(language);

            Set<String> missing = new TreeSet<>(pivotKeys);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(pivotKeys);

            if (!missing.isEmpty() || !extra.isEmpty()) {
                fail(language + ".yml is out of sync with " + PIVOT + ".yml - missing: " + missing + ", extra: " + extra);
            }
        }
    }

    private static Set<String> keysOf(String language) {
        try (InputStream input = LangParityTest.class.getResourceAsStream("/lang/" + language + ".yml")) {
            Map<String, Object> root = new Yaml().load(input);
            Set<String> keys = new LinkedHashSet<>();
            flatten("", root, keys);
            return keys;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load lang/" + language + ".yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> node, Set<String> out) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> child) {
                flatten(key, (Map<String, Object>) child, out);
            } else {
                out.add(key);
            }
        }
    }
}
