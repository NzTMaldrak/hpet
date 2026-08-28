/*
 * This file is part of HPET - Packet Based Pet Plugin
 *
 * TOS (Terms of Service)
 * You are not allowed to decompile, or redestribuite part of this code if not authorized by the original author.
 * You are not allowed to claim this resource as yours.
 */


package it.heron.hpet.main;

import it.heron.hpet.api.events.HPETReloadPluginEvent;

import it.heron.hpet.main.commands.PetCommand;
import it.heron.hpet.modules.ModulesHandler;
import it.heron.hpet.modules.pets.PetTypesHandler;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import it.heron.hpet.modules.pets.userpets.fakeentities.armorstandmetadatahandlers.ArmorStandMetadataHandler;
import it.heron.hpet.modules.pets.userpets.fakeentities.armorstandmetadatahandlers.versions.Metadata1_21;
import it.heron.hpet.gui.PetGui;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import it.heron.hpet.api.PetAPI;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class PetPlugin extends JavaPlugin {

    @Getter
    private static PetPlugin instance;

    @Getter
    private static PetAPI api = new PetAPI();

    @Getter
    private ArmorStandMetadataHandler armorStandMetadataHandler;

    @Getter
    private boolean packetEventsAvailable;

    @Getter
    private PetGui petGui;

    @Getter
    private PetCommand petCommand;

    @Getter
    private List<String> disabledWorlds = new ArrayList<>();

    @Getter
    private final PetTypesHandler petTypesHandler = new PetTypesHandler(this);

    private YamlConfiguration config;

    private boolean reloading;

    private final Set<String> registeredCommandAliases = new LinkedHashSet<>();

    @Getter
    private final ModulesHandler modulesHandler = new ModulesHandler(this);

    @Override
    public FileConfiguration getConfig() {
        return config;
    }


    @Override
    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(new File(getDataFolder()+File.separator+"config.yml"));
    }



    @Override
    public void saveResource(String resource, boolean overwrite) {
        if(new File(getDataFolder()+File.separator+resource).exists()) return;
        super.saveResource(resource, overwrite);
    }

    @Override
    public void onEnable() {
        load();
    }

    @Override
    public void onDisable() {
        unload();
    }

    /** Keeps the original public API used by hooks and external add-ons. */
    public void reload() {
        reloadAll();
    }

    public synchronized boolean reloadAll() {
        if (reloading) {
            getLogger().warning("Ignored an HPET reload because another reload is already running.");
            return false;
        }

        reloading = true;
        try {
            // unload() removes spawned pets and unloads every module. Cancelling
            // all remaining tasks also covers delayed ability/listener work.
            unload();
            Bukkit.getScheduler().cancelTasks(this);
            load();
            Bukkit.getPluginManager().callEvent(new HPETReloadPluginEvent());
            getLogger().info("Reloaded HPET configuration, pets, modules, hooks, GUI and online pets.");
            return true;
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not fully reload HPET", exception);
            return false;
        } finally {
            reloading = false;
        }
    }

    private void unload() {
        for(UserPet userPet : new ArrayList<>(PetPlugin.getApi().spawnedPets())) {
            PetPlugin.getApi().removePet(userPet);
        }
        HandlerList.unregisterAll(this);
        this.modulesHandler.unloadModules();
    }

    private void load() {
        instance = this;
        armorStandMetadataHandler = new Metadata1_21();
        packetEventsAvailable = Bukkit.getPluginManager().isPluginEnabled("packetevents");
        if (!packetEventsAvailable) {
            getLogger().severe("PacketEvents 2.13.0+ is not enabled. HPET will load, but pets cannot be spawned until PacketEvents is installed and enabled.");
        }
        saveResource("config.yml", false);
        saveResource("pets.yml", false);
        reloadConfig();
        loadWorldRestrictions();

        // Load modules first
        this.modulesHandler.loadModules();
        petGui = new PetGui(this);
        Bukkit.getPluginManager().registerEvents(petGui, this);
        RuntimeCompatibilityValidator.validate(this);

        for(Player p : Bukkit.getOnlinePlayers()) {
            PetPlugin.getApi().spawnDatabasePet(p);
        }
        
        // Log module loading issues but continue
        if (modulesHandler.moduleByName("Messages") == null) {
            getLogger().severe("MessagesHandler module failed to load - messages will not work properly");
        }

        // Initialize commands
        try {
            petCommand = new PetCommand();
            PluginCommand hpetCommand = Objects.requireNonNull(
                    getCommand("hpet"), "Command hpet is missing from plugin.yml");
            hpetCommand.setExecutor(petCommand);
            hpetCommand.setTabCompleter(petCommand);
            Objects.requireNonNull(getCommand("reload"), "Command reload is missing from plugin.yml")
                    .setExecutor(petCommand);
            configureCommandAliases(hpetCommand);
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadWorldRestrictions() {
        this.disabledWorlds = getConfig().getStringList("disabledWorlds").stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /** Returns whether pets and their abilities may be active in this world. */
    public boolean isPetWorldAllowed(World world) {
        if (world == null) return false;
        return !disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    /** Applies the disabled-world list while allowing an explicit player bypass. */
    public boolean isPetWorldAllowed(Player player) {
        return player != null
                && (player.hasPermission("pet.bypass.worlds") || isPetWorldAllowed(player.getWorld()));
    }

    /** Applies the disabled-world list to any pet owner; only players may bypass it. */
    public boolean isPetWorldAllowed(org.bukkit.entity.Entity owner) {
        if (owner instanceof Player player) return isPetWorldAllowed(player);
        return owner != null && isPetWorldAllowed(owner.getWorld());
    }

    private void configureCommandAliases(PluginCommand hpetCommand) {
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        Map<String, Command> knownCommands = commandMap.getKnownCommands();
        String namespace = getName().toLowerCase(Locale.ROOT) + ":";

        for (String alias : registeredCommandAliases) {
            knownCommands.remove(alias, hpetCommand);
            knownCommands.remove(namespace + alias, hpetCommand);
        }
        registeredCommandAliases.clear();

        List<String> configured = new ArrayList<>(getConfig().getStringList("alias"));
        if (configured.isEmpty()) configured.addAll(getConfig().getStringList("aliases"));
        boolean enabled = getConfig().contains("useAliases")
                ? getConfig().getBoolean("useAliases")
                : !configured.isEmpty();

        if (enabled) {
            for (String rawAlias : configured) {
                String alias = normalizeAlias(rawAlias);
                if (alias == null || alias.equals("hpet") || alias.equals("reload")) continue;

                Command existing = knownCommands.get(alias);
                if (existing != null && existing != hpetCommand) {
                    getLogger().warning("Cannot register /" + alias
                            + " as an HPET alias because another command already uses it.");
                    continue;
                }

                knownCommands.put(alias, hpetCommand);
                knownCommands.putIfAbsent(namespace + alias, hpetCommand);
                registeredCommandAliases.add(alias);
            }
        }

        hpetCommand.setAliases(new ArrayList<>(registeredCommandAliases));
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        if (!registeredCommandAliases.isEmpty()) {
            getLogger().info("Registered HPET aliases: " + registeredCommandAliases.stream()
                    .map(alias -> "/" + alias)
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    private String normalizeAlias(String rawAlias) {
        if (rawAlias == null) return null;
        String alias = rawAlias.strip().toLowerCase(Locale.ROOT);
        if (alias.startsWith("/")) alias = alias.substring(1);
        if (alias.isBlank() || !alias.matches("[a-z0-9_-]+")) {
            getLogger().warning("Ignoring invalid HPET command alias: " + rawAlias);
            return null;
        }
        return alias;
    }


}
