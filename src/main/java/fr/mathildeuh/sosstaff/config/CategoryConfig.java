package fr.mathildeuh.sosstaff.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.regex.Pattern;

public record CategoryConfig(
        String id,
        String displayName,
        Material icon,
        String colorHex,
        CreationMode mode,
        List<String> presets,
        String pingRoleId,
        String promptKey
) {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public static CategoryConfig fromSection(String id, ConfigurationSection section) throws ConfigValidationException {
        String displayName = section.getString("display-name", "").trim();
        if (displayName.isEmpty()) {
            throw new ConfigValidationException("categories." + id + ".display-name is required");
        }

        String iconName = section.getString("icon", "");
        Material icon = Material.matchMaterial(iconName);
        if (icon == null) {
            throw new ConfigValidationException("categories." + id + ".icon '" + iconName + "' is not a valid Material");
        }

        String colorHex = section.getString("color", "");
        if (!HEX_COLOR.matcher(colorHex).matches()) {
            throw new ConfigValidationException("categories." + id + ".color '" + colorHex + "' must be a #RRGGBB hex value");
        }

        CreationMode mode = null;
        String modeRaw = section.getString("mode");
        if (modeRaw != null) {
            try {
                mode = CreationMode.valueOf(modeRaw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConfigValidationException("categories." + id + ".mode '" + modeRaw + "' must be one of PRESET, FREE_INPUT, BOTH");
            }
        }

        List<String> presets = section.getStringList("presets");
        String pingRoleId = section.getString("ping-role");
        String promptKey = section.getString("prompt-key");

        return new CategoryConfig(id, displayName, icon, colorHex, mode, List.copyOf(presets), pingRoleId, promptKey);
    }

    public CreationMode resolvedMode(CreationMode globalDefault) {
        return mode != null ? mode : globalDefault;
    }
}
