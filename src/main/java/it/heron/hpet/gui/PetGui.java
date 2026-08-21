package it.heron.hpet.gui;

import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.messages.ComponentsHelper;
import it.heron.hpet.modules.pets.pettypes.PetType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
import java.util.stream.Collectors;

public final class PetGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private final PetPlugin plugin;

    public PetGui(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHome(Player player) {
        player.openInventory(createHome());
    }

    public Inventory createHome() {
        HomeHolder holder = new HomeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("HPET - Categorie", NamedTextColor.GOLD));
        holder.inventory = inventory;

        int slot = 10;
        Map<String, List<String>> groups = configuredGroups();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (slot >= 44) break;
            holder.groups.put(slot, entry.getKey());
            inventory.setItem(slot, categoryIcon(entry.getKey(), entry.getValue().size()));
            slot = nextDisplaySlot(slot);
        }

        holder.groups.put(49, "*");
        inventory.setItem(49, namedItem(Material.NETHER_STAR, "&6&lTutti i pet",
                List.of("&7Mostra tutti i pet disponibili")));
        inventory.setItem(47, namedItem(Material.NAME_TAG, "&a&lRinomina il pet",
                List.of("&7Clicca e poi usa", "&e/hpet rename <nuovo nome>")));
        inventory.setItem(50, namedItem(Material.LIME_DYE, "&a&lRigenera il pet",
                List.of("&7Ricrea il pet attualmente attivo")));
        inventory.setItem(51, namedItem(Material.BARRIER, "&c&lRimuovi il pet",
                List.of("&7Rimuove il pet attualmente attivo")));
        return inventory;
    }

    private void openPets(Player player, String group, int page) {
        List<PetType> pets = petsForGroup(group);
        int maxPage = Math.max(0, (pets.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        PetHolder holder = new PetHolder(group, safePage);
        String title = group.equals("*") ? "Tutti i pet" : prettyName(group);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("HPET - " + title, NamedTextColor.GOLD));
        holder.inventory = inventory;

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, pets.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            PetType petType = pets.get(index);
            holder.pets.put(slot, petType);
            inventory.setItem(slot, petType.generateGuiIcon(player));
        }

        inventory.setItem(45, namedItem(Material.BARRIER, "&c&lTorna alle categorie", List.of()));
        if (safePage > 0) inventory.setItem(48, namedItem(Material.ARROW, "&c&lPagina precedente", List.of()));
        if (safePage < maxPage) inventory.setItem(50, namedItem(Material.ARROW, "&a&lPagina successiva", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof HomeHolder) && !(inventoryHolder instanceof PetHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        if (inventoryHolder instanceof HomeHolder holder) {
            if (event.getRawSlot() == 47) {
                player.closeInventory();
                player.sendMessage(ComponentsHelper.simpleParse("&eUsa: &c/hpet rename <nuovo nome>"));
                return;
            }
            if (event.getRawSlot() == 50) {
                player.closeInventory();
                player.performCommand("hpet update");
                return;
            }
            if (event.getRawSlot() == 51) {
                player.closeInventory();
                player.performCommand("hpet remove");
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
        if (!plugin.isPacketEventsAvailable()) {
            player.sendMessage(Component.text("PacketEvents 2.13.0 o superiore non è attivo.", NamedTextColor.RED));
            return;
        }

        try {
            plugin.getApi().selectPet(player, petType);
            player.closeInventory();
            player.sendMessage(Component.text("Pet selezionato: " + petType.getName(), NamedTextColor.GREEN));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not select pet '" + petType.getName() + "' for " + player.getName(), exception);
            player.sendMessage(Component.text("Impossibile generare questo pet. Controlla la console.", NamedTextColor.RED));
        }
    }

    private List<PetType> petsForGroup(String group) {
        Collection<PetType> loaded = plugin.getApi().enabledPetTypes();
        if (group.equals("*")) {
            return loaded.stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
        }
        Set<String> names = configuredGroups().getOrDefault(group, List.of()).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return loaded.stream()
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

    private ItemStack categoryIcon(String group, int configuredPets) {
        return namedItem(Material.CHEST, "&a&l" + prettyName(group),
                List.of("", "&7" + configuredPets + " pet configurati", "&a&lClicca per aprire"));
    }

    private ItemStack namedItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ComponentsHelper.simpleParse(name));
        meta.lore(loreLines.stream().map(ComponentsHelper::simpleParse).toList());
        item.setItemMeta(meta);
        return item;
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
