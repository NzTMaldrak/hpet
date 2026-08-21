package it.heron.hpet.modules.pets.pettypes;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MobPetType extends AbstractPetType {

    private final EntityType entityType;

    public MobPetType(YamlConfiguration configuration, String key) {
        super(configuration, key);
        List<String> skins = configuration.getStringList(key + ".skins");
        if (skins.isEmpty() || !skins.getFirst().startsWith("MOB:")) {
            throw new IllegalArgumentException("Pet " + key + " does not define a MOB skin");
        }
        this.entityType = EntityType.valueOf(skins.getFirst().substring("MOB:".length()).toUpperCase());
    }

    public EntityType getEntityType() {
        return entityType;
    }

    @Override
    public ItemStack generateGuiIcon(Player viewer) {
        Material material = Material.matchMaterial(entityType.name() + "_SPAWN_EGG");
        ItemStack icon = new ItemStack(material == null ? Material.LEAD : material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(renderGuiComponent(viewer,
                getDisplayName() == null ? Component.text(getName()) : getDisplayName()));
        List<Component> lore = new ArrayList<>();
        if (getDescription() != null) lore.addAll(renderGuiComponents(viewer, getDescription()));
        lore.add(Component.empty());
        lore.add(localizedGuiComponent(viewer, "gui.pet.click_to_select"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @Override
    public boolean canBuy(Player player) {
        return getPrice() != null && !bought(player);
    }
}
