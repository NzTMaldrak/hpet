package it.heron.hpet.modules.messages;

import it.heron.hpet.modules.abstracts.Module;
import it.heron.hpet.modules.hooks.PapiModule;
import net.kyori.adventure.text.Component;
import it.heron.hpet.main.PetPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class MessagesHandler implements Module {
    private static final String ROOT = "messages.";
    private static final String ITALIAN_RESOURCE = "locales/it.yml";
    private static final String EDITABLE_FILE = "messages.yml";

    private final JavaPlugin plugin;
    private boolean loaded;
    private YamlConfiguration messages;
    private PapiModule papiModule;

    public MessagesHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Messages";
    }

    @Override
    public void load() {
        File messagesFile = new File(plugin.getDataFolder(), EDITABLE_FILE);
        try {
            if (!messagesFile.exists()) {
                File previousItalianFile = new File(plugin.getDataFolder(), ITALIAN_RESOURCE);
                if (previousItalianFile.isFile()) {
                    Files.copy(previousItalianFile.toPath(), messagesFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    try (InputStream bundled = plugin.getResource(ITALIAN_RESOURCE)) {
                        if (bundled == null) throw new IllegalStateException("Bundled Italian messages are missing");
                        Files.copy(bundled, messagesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                plugin.getLogger().info("Created editable Italian messages file: " + EDITABLE_FILE);
            }

            messages = YamlConfiguration.loadConfiguration(messagesFile);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create or load " + EDITABLE_FILE, exception);
        }

        try (InputStream bundled = plugin.getResource(ITALIAN_RESOURCE)) {
            if (bundled != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8));
                boolean localeUpdated = false;
                for (String path : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(path) || messages.contains(path)) continue;
                    messages.set(path, defaults.get(path));
                    localeUpdated = true;
                }
                if (localeUpdated) {
                    messages.save(messagesFile);
                    plugin.getLogger().info("Added missing Italian messages to " + EDITABLE_FILE + ".");
                }
                messages.setDefaults(defaults);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not update " + EDITABLE_FILE, exception);
        }

        Module papi = PetPlugin.getInstance().getModulesHandler().moduleByName("PlaceholderAPI");
        if (papi instanceof PapiModule module) papiModule = module;
        loaded = true;
    }

    @Override
    public void unload() {
        loaded = false;
        messages = null;
        papiModule = null;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    public void sendMessage(Player player, String message) {
        sendMessage(player, message, null);
    }

    public void sendMessage(Player player, String messagePath, Map<String, String> placeholders) {
        String raw = getRawString(messagePath);
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("Missing locale message: " + messagePath);
            return;
        }
        String formatted = replacePlaceholders(raw, player, placeholders);
        if (player != null && papiModule != null && papiModule.isLoaded()) {
            formatted = papiModule.parsePlaceholders(player, formatted);
        }
        player.sendMessage(ComponentsHelper.simpleParse(formatted));
    }

    public String getRawString(String messagePath) {
        if (messages == null || messagePath == null) return null;
        String path = messagePath.startsWith(ROOT) ? messagePath : ROOT + messagePath;
        return messages.getString(path);
    }

    public Component component(Player player, String messagePath, Map<String, String> placeholders) {
        String formatted = formattedString(player, messagePath, placeholders);
        return ComponentsHelper.simpleParse(formatted == null ? "" : formatted);
    }

    public List<Component> components(Player player, String messagePath, Map<String, String> placeholders) {
        if (messages == null || messagePath == null) return List.of();
        String path = messagePath.startsWith(ROOT) ? messagePath : ROOT + messagePath;
        return messages.getStringList(path).stream()
                .map(line -> replaceAndParse(player, line, placeholders))
                .toList();
    }

    public String formattedString(Player player, String messagePath, Map<String, String> placeholders) {
        String raw = getRawString(messagePath);
        if (raw == null) return null;
        String formatted = replacePlaceholders(raw, player, placeholders);
        if (player != null && papiModule != null && papiModule.isLoaded()) {
            formatted = papiModule.parsePlaceholders(player, formatted);
        }
        return formatted;
    }

    private Component replaceAndParse(Player player, String line, Map<String, String> placeholders) {
        String formatted = replacePlaceholders(line, player, placeholders);
        if (player != null && papiModule != null && papiModule.isLoaded()) {
            formatted = papiModule.parsePlaceholders(player, formatted);
        }
        return ComponentsHelper.simpleParse(formatted);
    }

    private String replacePlaceholders(String message, Player player, Map<String, String> placeholders) {
        String formatted = message;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                String key = entry.getKey();
                formatted = formatted.replace(key, value);
                if (key.startsWith("{") && key.endsWith("}")) {
                    formatted = formatted.replace("<" + key.substring(1, key.length() - 1) + ">", value);
                }
            }
        }
        if (player == null) return formatted;
        return formatted.replace("{player}", player.getName()).replace("<player>", player.getName());
    }
}
