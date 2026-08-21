package it.heron.hpet.modules.pets.userpets.fakeentities;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import it.heron.hpet.main.PetPlugin;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.List;


public class FakeArmostand extends AbstractFakeEntity implements CanHaveItemOnHead, CanHaveItemInHand {

    @Getter
    private Component name;
    @Getter
    private boolean glow;
    @Getter
    private boolean small;
    @Getter
    private double scale = 1d;

    public void setName(Component name) {
        this.name = name;
        updateMetadata();
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
        updateMetadata();
    }

    public void setSmall(boolean small) {
        this.small = small;
        updateMetadata();
    }

    public void setScale(double scale) {
        if (!Double.isFinite(scale) || scale < 0.0625d || scale > 16d) {
            throw new IllegalArgumentException("Invalid armor stand scale: " + scale);
        }
        this.scale = scale;
        updateScale();
    }

    public FakeArmostand(Component name, boolean glow, boolean small) {
        this.name = name;
        this.glow = glow;
        this.small = small;
    }

    @Override
    protected void onSpawn() {
        updateMetadata();
        updateScale();
    }

    @Override
    protected void onDespawn() {

    }

    @Override
    public void setHeldItem(ItemStack itemStack) {
        sendEquipment(EquipmentSlot.MAIN_HAND, itemStack);
    }

    @Override
    public void setHeadItem(ItemStack itemStack) {
        sendEquipment(EquipmentSlot.HELMET, itemStack);
    }

    private void sendEquipment(EquipmentSlot slot, ItemStack itemStack) {
        if (!isSpawned() || itemStack == null) return;
        Equipment equipment = new Equipment(slot, SpigotConversionUtil.fromBukkitItemStack(itemStack));
        sendPacket(new WrapperPlayServerEntityEquipment(this.id, List.of(equipment)));
    }

    private void updateMetadata() {
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(
                this.id, PetPlugin.getInstance().getArmorStandMetadataHandler().metadata(name, small, glow)
        );
        sendPacket(packet);
    }

    private void updateScale() {
        if (!isSpawned()) return;
        WrapperPlayServerUpdateAttributes.Property property =
                new WrapperPlayServerUpdateAttributes.Property(Attributes.SCALE, scale, List.of());
        sendPacket(new WrapperPlayServerUpdateAttributes(this.id, List.of(property)));
    }

    @Override
    public int requiredVersionProtcol() {
        return 47;
    }

    @Override
    public EntityType entityType() {
        return EntityTypes.ARMOR_STAND;
    }
}
