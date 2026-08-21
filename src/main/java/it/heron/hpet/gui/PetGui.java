package it.heron.hpet.gui;

import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.messages.MessagesHandler;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class PetGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final long RENAME_TIMEOUT_TICKS = 20L * 30L;
    private final PetPlugin plugin;
    private final Map<UUID, Integer> pendingRenames = new ConcurrentHashMap<>();
    private final AtomicInteger renameSessionIds = new AtomicInteger();

    public PetGui(PetPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenPetMenus, 20L, 20L);
    }

    private void refreshOpenPetMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (!(inventory.getHolder() instanceof PetHolder holder)) continue;

            Set<String> activePetTypes = plugin.getApi().userPets(player).stream()
                    .map(userPet -> userPet.getPetType().getName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            for (Map.Entry<Integer, PetType> entry : holder.pets.entrySet()) {
                if (!activePetTypes.contains(entry.getValue().getName().toLowerCase(Locale.ROOT))) continue;
                PetType currentType = plugin.getApi().petType(entry.getValue().getName());
                if (currentType == null) continue;
                entry.setValue(currentType);
                inventory.setItem(entry.getKey(), currentType.generateGuiIcon(player));
            }
        }
    }

    public void openHome(Player player) {
        player.openInventory(createHome(player));
    }

    public Inventory createHome() {
        return createHome(null);
    }

    private Inventory createHome(Player player) {
        HomeHolder holder = new HomeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54,
                localeComponent(player, "gui.home.title", Map.of()));
        holder.inventory = inventory;

        int slot = 10;
        Map<String, List<String>> groups = configuredGroups();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (slot >= 44) break;
            holder.groups.put(slot, entry.getKey());
            inventory.setItem(slot, categoryIcon(player, entry.getKey(), entry.getValue().size()));
            slot = nextDisplaySlot(slot);
        }

        holder.groups.put(49, "*");
        inventory.setItem(49, localeItem(player, Material.NETHER_STAR, "gui.home.all_pets", Map.of()));
        inventory.setItem(47, localeItem(player, Material.NAME_TAG, "gui.home.rename", Map.of()));
        inventory.setItem(50, localeItem(player, Material.LIME_DYE, "gui.home.respawn", Map.of()));
        inventory.setItem(51, localeItem(player, Material.BARRIER, "gui.home.remove", Map.of()));
        return inventory;
    }

    private void openPets(Player player, String group, int page) {
        List<PetType> pets = petsForGroup(player, group);
        int maxPage = Math.max(0, (pets.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        PetHolder holder = new PetHolder(group, safePage);
        String title = group.equals("*")
                ? localeText(player, "gui.pets.all_title", Map.of())
                : prettyName(group);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                localeComponent(player, "gui.pets.title", Map.of("{group}", title)));
        holder.inventory = inventory;

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, pets.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            PetType petType = pets.get(index);
            holder.pets.put(slot, petType);
            inventory.setItem(slot, petType.generateGuiIcon(player));
        }

        inventory.setItem(45, localeItem(player, Material.BARRIER, "gui.pets.back", Map.of()));
        if (safePage > 0) inventory.setItem(48,
                localeItem(player, Material.ARROW, "gui.pets.previous_page", Map.of()));
        if (safePage < maxPage) inventory.setItem(50,
                localeItem(player, Material.ARROW, "gui.pets.next_page", Map.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof HomeHolder)
                && !(inventoryHolder instanceof PetHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        if (inventoryHolder instanceof HomeHolder holder) {
            if (event.getRawSlot() == 47) {
                openRename(player);
                return;
            }
            if (event.getRawSlot() == 50) {
                player.closeInventory();
                player.performCommand("hpet update");
                return;
            }
            if (event.getRawSlot() == 51) {
                removePetSilently(player);
                return;
            }
            String group = holder.groups.get(event.getRawSlot());
            if (group != null) openPets(player, group, 0);
            return;
        }

        PetHolder holder = (PetHolder) inventoryHolder;
        if (event.getRawSlot() == 45) {
            openHome(player);
            return;
        }
        if (event.getRawSlot() == 48 && holder.page > 0) {
            openPets(player, holder.group, holder.page - 1);
            return;
        }
        if (event.getRawSlot() == 50) {
            openPets(player, holder.group, holder.page + 1);
            return;
        }

        PetType petType = holder.pets.get(event.getRawSlot());
        if (petType == null) return;
        if (!petType.isUnlocked(player)) {
            sendLocaleMessage(player, "gui.error.locked");
            return;
        }
        if (!plugin.isPacketEventsAvailable()) {
            sendLocaleMessage(player, "gui.error.packet_events");
            return;
        }

        try {
            plugin.getApi().selectPet(player, petType);
            player.closeInventory();
            sendLocaleMessage(player, "gui.select.success", Map.of("{pet}", petType.getName()));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not select pet '" + petType.getName() + "' for " + player.getName(), exception);
            sendLocaleMessage(player, "gui.error.spawn_failed", Map.of("{pet}", petType.getName()));
        }
    }

    private void openRename(Player player) {
        if (!player.hasPermission("pet.rename")) {
            sendLocaleMessage(player, "gui.error.rename_permission");
            return;
        }
        UserPet userPet = plugin.getApi().userPet(player);
        if (userPet == null) {
            sendLocaleMessage(player, "error.no_active_pet");
            return;
        }

        player.closeInventory();
        int sessionId = renameSessionIds.incrementAndGet();
        pendingRenames.put(player.getUniqueId(), sessionId);
        sendLocaleMessage(player, "command.hpet.rename.chat_prompt");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRenames.remove(player.getUniqueId(), sessionId)) {
                sendLocaleMessage(player, "command.hpet.rename.expired");
            }
        }, RENAME_TIMEOUT_TICKS);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRenameChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (pendingRenames.remove(player.getUniqueId()) == null) return;
        event.setCancelled(true);

        String newName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (newName.equalsIgnoreCase("annulla") || newName.equalsIgnoreCase("cancel")) {
                sendLocaleMessage(player, "command.hpet.rename.cancelled");
                return;
            }
            plugin.getPetCommand().renamePetCommand(player, newName);
        });
    }

    private void removePetSilently(Player player) {
        if (!player.hasPermission("pet.remove")) {
            sendLocaleMessage(player, "gui.error.remove_permission");
            return;
        }
        UserPet userPet = plugin.getApi().userPet(player);
        if (userPet == null) {
            sendLocaleMessage(player, "error.no_active_pet");
            return;
        }
        String petName = userPet.getPetType().getName();
        plugin.getApi().removePet(userPet);
        player.closeInventory();
        sendLocaleMessage(player, "gui.remove.success", Map.of("{pet}", petName));
    }

    private void sendLocaleMessage(Player player, String path) {
        sendLocaleMessage(player, path, Map.of("{player}", player.getName()));
    }

    private void sendLocaleMessage(Player player, String path, Map<String, String> placeholders) {
        MessagesHandler messages = (MessagesHandler) plugin.getModulesHandler().moduleByName("Messages");
        if (messages != null) messages.sendMessage(player, path, placeholders);
    }

    private List<PetType> petsForGroup(Player player, String group) {
        Collection<PetType> loaded = plugin.getApi().enabledPetTypes();
        if (group.equals("*")) {
            return loaded.stream()
                    .filter(pet -> pet.canSee(player))
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
        }
        Set<String> names = configuredGroups().getOrDefault(group, List.of()).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return loaded.stream()
                .filter(pet -> pet.canSee(player))
                .filter(pet -> names.contains(pet.getName().toLowerCase(Locale.ROOT)))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    private Map<String, List<String>> configuredGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("group");
        if (section == null) return groups;
        for (String group : section.getKeys(false)) {
            groups.put(group, section.getStringList(group + ".pets"));
        }
        return groups;
    }

    private ItemStack categoryIcon(Player player, String group, int configuredPets) {
        return localeItem(player, Material.CHEST, "gui.category",
                Map.of("{group}", prettyName(group), "{count}", String.valueOf(configuredPets)));
    }

    private ItemStack localeItem(
            Player player, Material material, String path, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(localeComponent(player, path + ".name", placeholders));
        meta.lore(messages().components(player, path + ".lore", placeholders));
        item.setItemMeta(meta);
        return item;
    }

    private MessagesHandler messages() {
        return (MessagesHandler) plugin.getModulesHandler().moduleByName("Messages");
    }

    private String localeText(Player player, String path, Map<String, String> placeholders) {
        String value = messages().formattedString(player, path, placeholders);
        return value == null ? "" : value;
    }

    private Component localeComponent(Player player, String path, Map<String, String> placeholders) {
        return messages().component(player, path, placeholders);
    }

    private String prettyName(String input) {
        if (input == null || input.isBlank()) return "Pet";
        String normalized = input.replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private int nextDisplaySlot(int slot) {
        int next = slot + 1;
        if (next % 9 == 8) next += 2;
        return next;
    }

    private static final class HomeHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> groups = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PetHolder implements InventoryHolder {
        private final String group;
        private final int page;
        private Inventory inventory;
        private final Map<Integer, PetType> pets = new HashMap<>();

        private PetHolder(String group, int page) {
            this.group = group;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

}
