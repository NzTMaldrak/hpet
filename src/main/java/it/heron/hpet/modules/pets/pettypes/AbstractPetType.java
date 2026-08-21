package it.heron.hpet.modules.pets.pettypes;

import it.heron.hpet.database.AbstractDatabase;
import it.heron.hpet.database.tables.BoughtPets;
import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.abilities.AbilitiesHandler;
import it.heron.hpet.modules.abilities.abstracts.Ability;
import it.heron.hpet.modules.messages.ComponentsHelper;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractPetType implements PetType {

    @NonNull
    private YamlConfiguration configuration;

    @Getter
    private @NonNull String name;
    @Getter
    private @Nullable Ability ability = null;

    @Getter @Setter
    private @Nullable Component displayName;
    @Getter @Setter
    private @Nullable List<Component> description;

    @Getter @Setter
    private @NonNull Vector relativeLocation = new Vector(0, 0, 0);
    @Getter @Setter
    private @NonNull Vector nametagRelativeLocation = new Vector(0, 0, 0);
    @Getter @Setter
    private @Nullable Double price = null;
    @Getter @Setter
    private @NonNull String animationName = "glide";

    @Getter
    // Additional per-pet rotation. The original renderer always adds its fixed 200 degrees.
    private float yaw = 0f;
    @Getter
    private double distance = 1d;
    @Getter
    private double nameHeight = 1d;

    public AbstractPetType(YamlConfiguration configuration, @NonNull String key) {
        this.configuration = configuration;
        this.name = key;
        String configuredDisplayName = configuration.getString(absolutePath("displayname"), key);
        this.displayName = ComponentsHelper.simpleParse(configuredDisplayName.strip());
        this.description = ComponentsHelper.listParse(configuration.getStringList(absolutePath("description")));

        this.nameHeight = configuration.getDouble(absolutePath("y"), nameHeight);
        relativeLocation.setX(configuration.getDouble(absolutePath("x"), 0));
        relativeLocation.setY(nameHeight - 1d);
        relativeLocation.setZ(configuration.getDouble(absolutePath("z"), 0));
        loadVector("nametag", nametagRelativeLocation);
        this.price = getNullableDouble("price");
        this.animationName = configuration.getString(absolutePath("animation"), animationName);
        this.yaw = (float) configuration.getDouble(absolutePath("yaw"), yaw);
        this.distance = configuration.getDouble(absolutePath("distance"), distance);
    }

    @Override
    public boolean canSee(Player player) {
        return player.hasPermission("pet.see."+this.name);
    }

    @Override
    public boolean isUnlocked(Player player) {
        return player.hasPermission("pet.use."+this.name) || bought(player);
    }

    @Override
    public boolean bought(Player player) {
        return BoughtPets.hasBought(player.getUniqueId(), this.name);
    }

    protected void loadVector(String subpath, Vector def) {
        String path = absolutePath(subpath);
        double x = configuration.getDouble(path + ".x", def.getX());
        double y = configuration.getDouble(path + ".y", def.getY());
        double z = configuration.getDouble(path + ".z", def.getZ());
        def.setX(x);
        def.setY(y);
        def.setZ(z);
    }

    protected Double getNullableDouble(String subpath) {
        if(!configuration.contains(absolutePath(subpath))) return null;
        return configuration.getDouble(absolutePath(subpath));
    }

    protected String absolutePath(String subpath) {
        if(subpath == null || subpath.isEmpty()) return this.name;
        return this.name+"."+subpath;
    }

}
