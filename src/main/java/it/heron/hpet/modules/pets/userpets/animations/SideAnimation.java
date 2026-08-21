package it.heron.hpet.modules.pets.userpets.animations;

import it.heron.hpet.modules.pets.userpets.animations.abstracts.IAnimation;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class SideAnimation implements IAnimation {
    @Override
    public void nextStep() {
    }

    @Override
    public Vector relativeLocation(Location ownerLocation) {
        return new Vector(0, -0.9, 0);
    }

    @Override
    public String name() {
        return "side";
    }
}
