package it.heron.hpet.modules.combat;

import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.abstracts.DefaultInstanceModule;
import it.heron.hpet.modules.exceptions.RefusedLoadException;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Disables active pets during player-versus-player combat tracked by CombatLogX. */
public final class CombatLogXHook extends DefaultInstanceModule implements Listener {

    private final Map<UUID, PetType> suspendedPets = new HashMap<>();
    private final Set<UUID> playersInCombat = new HashSet<>();

    public CombatLogXHook(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "CombatLogX";
    }

    @Override
    protected void onLoad() {
        if (!plugin.getConfig().getBoolean("combatLogXHook", true)
                || !plugin.getServer().getPluginManager().isPluginEnabled("CombatLogX")) {
            throw new RefusedLoadException();
        }
        try {
            registerCombatEvent(
                    "com.github.sirblobman.combatlogx.api.event.PlayerTagEvent", this::handleTag);
            registerCombatEvent(
                    "com.github.sirblobman.combatlogx.api.event.PlayerReTagEvent", this::handleTag);
            registerCombatEvent(
                    "com.github.sirblobman.combatlogx.api.event.PlayerUntagEvent", this::handleUntag);
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        } catch (ClassNotFoundException exception) {
            throw new RefusedLoadException("Unsupported CombatLogX API: combat events are unavailable");
        }
    }

    @Override
    protected void onUnload() {
        HandlerList.unregisterAll(this);
        suspendedPets.clear();
        playersInCombat.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        suspendedPets.remove(event.getPlayer().getUniqueId());
        playersInCombat.remove(event.getPlayer().getUniqueId());
    }

    private void registerCombatEvent(String className, CombatEventHandler handler)
            throws ClassNotFoundException {
        Class<? extends Event> eventClass = Class.forName(className).asSubclass(Event.class);
        plugin.getServer().getPluginManager().registerEvent(
                eventClass, this, EventPriority.NORMAL,
                (listener, event) -> handler.handle(event), plugin, true);
    }

    private void handleTag(Event event) {
        Object enemy = invoke(event, "getEnemy");
        if (!(enemy instanceof Player)) return;

        Player player = playerFrom(event);
        if (player == null) return;
        if (player.hasPermission("pet.bypass.combat")) {
            playersInCombat.remove(player.getUniqueId());
            restorePet(player);
            return;
        }
        playersInCombat.add(player.getUniqueId());
        suspendPet(player);
    }

    private void handleUntag(Event event) {
        Player player = playerFrom(event);
        if (player == null) return;
        playersInCombat.remove(player.getUniqueId());
        restorePet(player);
    }

    private Player playerFrom(Event event) {
        Object player = invoke(event, "getPlayer");
        return player instanceof Player result ? result : null;
    }

    private Object invoke(Event event, String methodName) {
        try {
            return event.getClass().getMethod(methodName).invoke(event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read CombatLogX event " + event.getEventName(), exception);
            return null;
        }
    }

    private void suspendPet(Player player) {
        UserPet userPet = PetPlugin.getApi().userPet(player);
        if (userPet == null) return;

        suspendedPets.put(player.getUniqueId(), userPet.getPetType());
        PetPlugin.getApi().removePet(userPet);
    }

    public boolean preventsPetSelection(Player player) {
        return playersInCombat.contains(player.getUniqueId())
                && !player.hasPermission("pet.bypass.combat");
    }

    private void restorePet(Player player) {
        PetType petType = suspendedPets.remove(player.getUniqueId());
        if (petType == null || !player.isOnline()) return;
        if (PetPlugin.getApi().hasUserPet(player)) return;
        if (!player.hasPermission("pet.command") || !petType.isUnlocked(player)) return;
        if (!PetPlugin.getInstance().isPetWorldAllowed(player)) return;

        PetPlugin.getApi().selectPet(player, petType);
    }

    @FunctionalInterface
    private interface CombatEventHandler {
        void handle(Event event);
    }
}
