package it.heron.hpet.modules.pets.userpets.animations;

import it.heron.hpet.modules.pets.userpets.animations.abstracts.IAnimation;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class WalkAnimation implements IAnimation {
    @Override
    public void nextStep() {
    }

    @Override
    public Vector relativeLocation(Location ownerLocation) {
        int groundY = ownerLocation.getWorld().getHighestBlockYAt(
                ownerLocation.getBlockX(), ownerLocation.getBlockZ()) + 1;
        if (ownerLocation.getBlockY() + 5 < groundY) groundY = ownerLocation.getBlockY();
        return new Vector(0, groundY - ownerLocation.getY(), 0);
    }

    @Override
    public String name() {
        return "walk";
    }
}
