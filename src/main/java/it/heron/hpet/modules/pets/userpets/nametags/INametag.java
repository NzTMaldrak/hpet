package it.heron.hpet.modules.pets.userpets.nametags;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;

public interface INametag {

    void setName(Component name);
    Component getName();
    Location getLocation();

    void teleport(Location location);

    boolean isShown();
    void show(Location location);
    void hide();
}
