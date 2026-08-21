package it.heron.hpet.modules.pets.userpets.fakeentities;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import java.util.List;

public final class FakeMobEntity extends AbstractFakeEntity {

    private final EntityType entityType;
    private boolean glow;

    public FakeMobEntity(EntityType entityType) {
        this.entityType = entityType;
    }

    @Override
    protected void onSpawn() {
        updateMetadata();
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
        updateMetadata();
    }

    private void updateMetadata() {
        if (!isSpawned()) return;
        byte flags = glow ? (byte) 0x40 : 0;
        sendPacket(new WrapperPlayServerEntityMetadata(
                this.id, List.of(new EntityData<>(0, EntityDataTypes.BYTE, flags))));
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
