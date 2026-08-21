package it.heron.hpet.modules.pets.userpets.fakeentities;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;

public final class FakeMobEntity extends AbstractFakeEntity {

    private final EntityType entityType;

    public FakeMobEntity(EntityType entityType) {
        this.entityType = entityType;
    }

    @Override
    protected void onSpawn() {
    }

    @Override
    protected void onDespawn() {
    }

    @Override
    public int requiredVersionProtcol() {
        return 47;
    }

    @Override
    public EntityType entityType() {
        return entityType;
    }
}
