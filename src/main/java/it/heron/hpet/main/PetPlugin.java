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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import it.heron.hpet.api.PetAPI;

import java.io.File;
import java.util.*;

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
    private List<String> disabledWorlds = new ArrayList<>();

    @Getter
    private final PetTypesHandler petTypesHandler = new PetTypesHandler(this);

    private YamlConfiguration config;

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

    public void reload() {
        PetPlugin.getInstance().reloadConfig();
        unload();
        load();
        Bukkit.getPluginManager().callEvent(new HPETReloadPluginEvent());
        Bukkit.getLogger().info("Reloaded HPET!");
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
            PetCommand petCommand = new PetCommand();
            Objects.requireNonNull(getCommand("hpet"), "Command hpet is missing from plugin.yml")
                    .setExecutor(petCommand);
            Objects.requireNonNull(getCommand("hpet"))
                    .setTabCompleter(petCommand);
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
