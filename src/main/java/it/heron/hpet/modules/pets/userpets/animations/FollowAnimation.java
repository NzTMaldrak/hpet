package it.heron.hpet.modules.pets.userpets.animations; // Assuming this is the correct package

import it.heron.hpet.modules.pets.userpets.animations.abstracts.AbstractAnimation;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class FollowAnimation extends AbstractAnimation {

    @Override
    public String name() {
        return "follow";
    }

    @Override
    protected int runEvery() {
        return 1;
    }

    /**
     * Calculates the desired pet location relative to the owner.
     * The pet will try to stay behind and to the side of the owner.
     *
     * @param ownerLocation The current location of the owner.
     * @return A Vector representing the desired offset from the owner's location.
     */
    @Override
    public Vector relativeLocation(Location ownerLocation) {
        return new Vector(0, 0.5, 0);
    }
}
