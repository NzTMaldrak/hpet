package it.heron.hpet.modules.pets.userpets.abstracts;

import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.userpets.fakeentities.FakeEntity;
import it.heron.hpet.modules.pets.userpets.fakeentities.AbstractFakeEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Objects;

public abstract class FakeEntitiesUserPet extends AbstractUserPet {

    protected FakeEntity fakeEntity;

    public FakeEntitiesUserPet(PetType petType, Entity owner, int level) {
        super(petType, owner, level);
    }


    @Override
    public void onSpawn() {
        this.fakeEntity.spawn(this.location);
        this.id = fakeEntity.getId();
    }

    @Override
    public void onDespawn() {
        this.fakeEntity.despawn();
        this.id = -1;
    }

    @Override
    public void teleport(Location location) {
        super.teleport(location);
        boolean petShown = fakeEntity != null && fakeEntity.isSpawned();
        boolean nameShown = nametag != null && nametag.isShown();
        World targetWorld = location.getWorld();
        boolean canBundle = petShown && nameShown
                && targetWorld != null
                && fakeEntity.getLocation() != null
                && nametag.getLocation() != null
                && Objects.equals(fakeEntity.getLocation().getWorld(), targetWorld)
                && Objects.equals(nametag.getLocation().getWorld(), targetWorld);

        if (canBundle) AbstractFakeEntity.sendBundleDelimiter(targetWorld);
        try {
            if (petShown) fakeEntity.teleport(location, false);
            if (nameShown) nametag.teleport(getNametagLocation(location));
        } finally {
            if (canBundle) AbstractFakeEntity.sendBundleDelimiter(targetWorld);
        }
    }

}
