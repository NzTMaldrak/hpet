package it.heron.hpet.modules;

import it.heron.hpet.modules.abilities.AbilitiesHandler;
import it.heron.hpet.modules.abstracts.Module;
import it.heron.hpet.modules.combat.CombatLogXHook;
import it.heron.hpet.modules.exceptions.InvalidLoadException;
import it.heron.hpet.modules.exceptions.RefusedLoadException;
import it.heron.hpet.modules.hooks.HeadDatabaseModule;
import it.heron.hpet.modules.hooks.ItemsAdderModule;
import it.heron.hpet.modules.hooks.PapiModule;
import it.heron.hpet.modules.hooks.VaultHook;
import it.heron.hpet.modules.invisibilityintegration.InvisibilityHandler;
import it.heron.hpet.modules.messages.MessagesHandler;
import it.heron.hpet.modules.pets.PetTypesHandler;
import it.heron.hpet.modules.pets.PetsHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import it.heron.hpet.main.PetPlugin;
import java.util.logging.Level;

import java.util.*;

public class ModulesHandler {

    private JavaPlugin plugin;
    // Module order matters: the pet and persistence modules use the database
    // while they are being initialized. Keep insertion order on load/reload.
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModulesHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasModule(String moduleName) {
        return this.modules.containsKey(moduleName);
    }

    public Module moduleByName(String moduleName) {
        return this.modules.get(moduleName.toLowerCase());
    }

    public void loadModules() {
        for(Module module : validModules()) {
            addModule(module);
        }
        loadAddedModules();
    }

    public void unloadModules() {
        new ArrayList<>(modules.values()).forEach(this::removeModule);
    }

    private Collection<Module> validModules() {
        List<Module> modules = new ArrayList<>();
        modules.add(new DatabaseModule(plugin));
        modules.add(PetPlugin.getInstance().getPetTypesHandler());
        modules.add(new PetsHandler(plugin));
        modules.add(new AbilitiesHandler(plugin));
        modules.add(new CombatLogXHook(plugin));

        modules.add(new PapiModule(plugin));
        modules.add(new VaultHook(plugin));
        modules.add(new ItemsAdderModule(plugin));
        modules.add(new HeadDatabaseModule(plugin));
        modules.add(new InvisibilityHandler(plugin));
        modules.add(new MessagesHandler(plugin));
        return modules;
    }

    private void addModule(Module module) {
        this.modules.put(module.name().toLowerCase(), module);
    }

    private void removeModule(Module module) {
        module.unload();
        modules.remove(module.name().toLowerCase(Locale.ROOT));
    }

    private void loadAddedModules() {
        for(Module module : modules.values()) {
            try {
                module.load();
                Bukkit.getLogger().info("Loaded module "+module.name());
            } catch (InvalidLoadException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load module " + module.name(), e);
            } catch (RefusedLoadException ignored) {}
            catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Module " + module.name() + " failed to load; HPET will continue with that module disabled",
                        exception);
            }
        }
    }


}
