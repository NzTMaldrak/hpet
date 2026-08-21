package it.heron.hpet.modules.pets.pettypes;

import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.messages.ComponentsHelper;
import it.heron.hpet.utils.heads.CustomHead;
import it.heron.hpet.utils.heads.HDBHead;
import it.heron.hpet.utils.heads.PlayerHead;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;


public abstract class StackPetType extends AbstractPetType {

    @Getter
    protected ItemStack[] skins;

    public StackPetType(YamlConfiguration configuration, String key) {
        super(configuration, key);
        List<String> skins = configuration.getStringList(absolutePath("skins"));
        this.skins = new ItemStack[skins.size()];
        for (int i = 0; i < this.skins.length; i++) {
            this.skins[i] = makeSkin(skins.get(i));
        }

    }

    @Override
    public ItemStack generateGuiIcon(Player viewer) {
        ItemStack icon = skins.length == 0 || skins[0] == null
                ? new ItemStack(Material.PAPER)
                : skins[0].clone();
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(getDisplayName() == null ? Component.text(getName()) : getDisplayName());
        List<Component> lore = new ArrayList<>();
        if (getDescription() != null) lore.addAll(getDescription());
        lore.add(Component.empty());
        lore.add(ComponentsHelper.simpleParse("&aClicca per selezionare"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @Override
    public boolean canBuy(Player player) {
        return false;
    }

    protected abstract ItemStack makeSkin(String skinName);
}
