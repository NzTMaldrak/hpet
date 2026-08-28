// File: it/heron/hpet/modules/abilities/AbilitiesHandler.java
package it.heron.hpet.modules.abilities;

import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.messages.MessagesHandler;
import it.heron.hpet.modules.abstracts.DefaultInstanceModule;
import it.heron.hpet.modules.pets.PetsHandler;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/** Routes Bukkit events to the per-pet ability runtimes. */
public class AbilitiesHandler extends DefaultInstanceModule implements Listener {

    public AbilitiesHandler(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "abilities";
    }

    @Override
    protected void onLoad() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onUnload() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWalk(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        trigger(event.getPlayer(), AbilityTrigger.WALK);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShift(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) trigger(event.getPlayer(), AbilityTrigger.SHIFT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        trigger(event.getPlayer(), AbilityTrigger.BLOCK_BREAK);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        trigger(event.getPlayer(), AbilityTrigger.BLOCK_PLACE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (protects(event.getPlayer(), AbilityType.NO_KNOCKBACK)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && protects(player, AbilityType.NO_FALL_DAMAGE)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (UserPet userPet : petsHandler().userPets(event.getPlayer().getUniqueId())) {
            petsHandler().removePet(userPet);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (petsHandler().userPets(event.getPlayer().getUniqueId()).isEmpty()) {
                PetPlugin.getApi().spawnDatabasePet(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (PetPlugin.getInstance().isPetWorldAllowed(player.getWorld())) return;

        var activePets = petsHandler().userPets(player.getUniqueId());
        if (activePets.isEmpty()) return;
        for (UserPet userPet : activePets) PetPlugin.getApi().deselectPet(userPet);

        var module = PetPlugin.getInstance().getModulesHandler().moduleByName("Messages");
        if (module instanceof MessagesHandler messages) {
            messages.sendMessage(player, "pet.disabled_world",
                    Map.of("{world}", player.getWorld().getName()));
        }
    }

    private void trigger(Player player, AbilityTrigger trigger) {
        for (UserPet userPet : petsHandler().userPets(player.getUniqueId())) {
            userPet.getAbilityRuntime().trigger(trigger);
        }
    }

    private boolean protects(Player player, AbilityType type) {
        return petsHandler().userPets(player.getUniqueId()).stream()
                .anyMatch(userPet -> userPet.getAbilityRuntime().protectsFrom(type));
    }

    private PetsHandler petsHandler() {
        return (PetsHandler) PetPlugin.getInstance().getModulesHandler().moduleByName("PetsHandler");
    }
}
