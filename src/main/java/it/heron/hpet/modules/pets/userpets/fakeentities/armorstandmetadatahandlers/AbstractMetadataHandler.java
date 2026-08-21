package it.heron.hpet.modules.pets.userpets.fakeentities.armorstandmetadatahandlers;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.util.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Optional;

public abstract class AbstractMetadataHandler implements ArmorStandMetadataHandler {

    protected abstract EntityData<?> invisible();
    protected abstract EntityData<?> name(Component name);
    protected abstract EntityData<?> small();
    protected abstract EntityData<?> glow();
    protected abstract EntityData<?> marker();
    protected abstract EntityData<?> showArms();
    protected abstract EntityData<?> armData();
    protected abstract Vector3f armPose();

    @Override
    public List<EntityData<?>> metadata(Component name, boolean small, boolean glow) {
        byte entityFlags = (byte) (0x20 | (glow ? 0x40 : 0));
        byte armorStandFlags = (byte) (0x10 | 0x04 | (small ? 0x01 : 0));
        boolean hasVisibleName = name != null
                && !PlainTextComponentSerializer.plainText().serialize(name).isBlank();
        return List.of(
                new EntityData<>(0, EntityDataTypes.BYTE, entityFlags),
                new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT,
                        hasVisibleName ? Optional.of(name) : Optional.empty()),
                new EntityData<>(3, EntityDataTypes.BOOLEAN, hasVisibleName),
                new EntityData<>(15, EntityDataTypes.BYTE, armorStandFlags),
                // Paper 26.2: 18 is the left arm, while MAIN_HAND is rendered by the right arm (19).
                new EntityData<>(19, EntityDataTypes.ROTATION, armPose())
        );
    }
}
