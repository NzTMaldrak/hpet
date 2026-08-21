package it.heron.hpet.modules.pets.userpets;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import it.heron.hpet.modules.pets.pettypes.MobPetType;
import it.heron.hpet.modules.pets.userpets.abstracts.FakeEntitiesUserPet;
import it.heron.hpet.modules.pets.userpets.fakeentities.FakeMobEntity;
import org.bukkit.entity.Entity;

public final class MobUserPet extends FakeEntitiesUserPet {

    public MobUserPet(MobPetType petType, Entity owner, int level) {
        super(petType, owner, level);
        this.fakeEntity = new FakeMobEntity(SpigotConversionUtil.fromBukkitEntityType(petType.getEntityType()));
    }
}
