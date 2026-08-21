package it.heron.hpet.modules.hooks;

import it.heron.hpet.modules.abstracts.PluginHook;
import org.bukkit.plugin.java.JavaPlugin;
import it.heron.hpet.placeholders.PlaceholdersExtension;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import it.heron.hpet.modules.pets.pettypes.PetType;

public class PapiModule extends PluginHook {

    private PlaceholdersExtension extension;

    public PapiModule(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "PlaceholderAPI";
    }

    public String parsePlaceholders(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public String parsePlaceholders(OfflinePlayer player, String text, PetType petType) {
        return extension.withPetContext(
                petType, () -> PlaceholderAPI.setPlaceholders(player, text));
    }

    @Override
    protected void onLoad() {
        this.extension = new PlaceholdersExtension();
        extension.register();
    }

    @Override
    protected void onUnload() {
        extension.unregister();
    }
}
