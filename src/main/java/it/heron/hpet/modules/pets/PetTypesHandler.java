package it.heron.hpet.modules.pets;

import it.heron.hpet.modules.abstracts.AbstractModule;
import it.heron.hpet.modules.abstracts.DefaultInstanceModule;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.pettypes.CustomModelPetType;
import it.heron.hpet.modules.pets.pettypes.HeadPetType;
import it.heron.hpet.modules.pets.pettypes.MobPetType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PetTypesHandler extends DefaultInstanceModule {

    private File PETS_FILE;
    private HashMap<String, PetType> loadedPetTypes = new HashMap<>();

    public PetTypesHandler(JavaPlugin plugin) {
        super(plugin);
    }

    private void loadPetTypes(YamlConfiguration yamlConfiguration) {
        loadedPetTypes.clear();
        for (String key : yamlConfiguration.getKeys(false)) {
            ConfigurationSection section = yamlConfiguration.getConfigurationSection(key);
            if (section == null || !section.isList("skins")) continue;

            List<String> skins = section.getStringList("skins");
            if (skins.isEmpty()) {
                Bukkit.getLogger().warning("Skipping pet '" + key + "': skins must be a non-empty list");
                continue;
            }

            try {
                boolean mob = skins.getFirst().startsWith("MOB:");
                boolean customModel = skins.stream().allMatch(skin -> skin.matches("[A-Z0-9_]+(?:\\s+|:)\\d+"));
                PetType petType = mob
                        ? new MobPetType(yamlConfiguration, key)
                        : customModel
                            ? new CustomModelPetType(yamlConfiguration, key)
                            : new HeadPetType(yamlConfiguration, key);
                loadedPetTypes.put(key.toLowerCase(Locale.ROOT), petType);
            } catch (RuntimeException exception) {
                Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not load pet type '" + key + "'", exception);
            }
        }
    }

    public PetType petType(String name) {
        return name == null ? null : this.loadedPetTypes.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<PetType> loadedPetTypes() {
        return this.loadedPetTypes.values();
    }

    @Override
    public String name() {
        return "PetsLoader";
    }

    @Override
    protected void onLoad() {
        PETS_FILE = new File(plugin.getDataFolder()+File.separator+"pets.yml");
        migrateBrokenDefaultPetsFile();
        loadPetTypes(YamlConfiguration.loadConfiguration(PETS_FILE));
    }

    private void migrateBrokenDefaultPetsFile() {
        final String broken = "- \"CONSOLE_COMMAND:/summon potion ~ ~1 ~ {Potion:{id:\"minecraft:healing\"}}:10s\"";
        final String fixed = "- 'CONSOLE_COMMAND:/summon potion ~ ~1 ~ {Potion:{id:\"minecraft:healing\"}}:10s'";
        try {
            String contents = Files.readString(PETS_FILE.toPath(), StandardCharsets.UTF_8);
            if (!contents.contains(broken)) return;

            File backup = new File(PETS_FILE.getParentFile(), "pets.yml.pre-26.2.bak");
            if (!backup.exists()) {
                Files.copy(PETS_FILE.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            Files.writeString(PETS_FILE.toPath(), contents.replace(broken, fixed), StandardCharsets.UTF_8);
            plugin.getLogger().info("Repaired the invalid potion command in pets.yml (backup: "
                    + backup.getName() + ")");
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not validate pets.yml", exception);
        }
    }

    @Override
    protected void onUnload() {
        loadedPetTypes.clear();
    }
}
