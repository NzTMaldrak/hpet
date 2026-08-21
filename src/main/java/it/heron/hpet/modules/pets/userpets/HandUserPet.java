package it.heron.hpet.modules.pets.userpets;

import it.heron.hpet.modules.pets.pettypes.StackPetType;
import it.heron.hpet.modules.pets.pettypes.HeadPetType;
import it.heron.hpet.modules.pets.userpets.abstracts.FakeEntitiesUserPet;
import it.heron.hpet.modules.pets.userpets.fakeentities.CanHaveItemInHand;
import it.heron.hpet.modules.pets.userpets.fakeentities.CanHaveItemOnHead;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import it.heron.hpet.modules.pets.userpets.fakeentities.FakeArmostand;
import net.kyori.adventure.text.Component;

public class HandUserPet extends FakeEntitiesUserPet {

    private static final int SKIN_CHANGE_INTERVAL_TICKS = 14;
    private static final double HEAD_SCALE = 1.15d;
    private static final double CUSTOM_MODEL_SCALE = 1.08d;
    private static final double HAND_CENTER_HEIGHT = 1.35d;
    /*
     * Horizontal centre of a player-head item rendered in an adult armor stand's
     * right hand on the 26.2 client. This comes from the actual client transform:
     * right-arm pivot/pose -> third-person right-hand transform -> skull model.
     * Keeping these values separate from the configurable custom-model fallback
     * prevents a fixed radial correction from being right at one yaw and wrong at
     * another one.
     */
    private static final double CLIENT_HEAD_CENTER_RADIUS = 0.7914190350952872d;
    private static final double CLIENT_HEAD_CENTER_ANGLE_OFFSET = 45.66186243975972d;
    private final double visualScale;
    private int currentSkinIndex = 0;
    private int ticksSinceSkinChange = 0;

    public HandUserPet(StackPetType petType, Entity owner, int level) {
        super(petType, owner, level);
        this.visualScale = petType instanceof HeadPetType ? HEAD_SCALE : CUSTOM_MODEL_SCALE;
        FakeArmostand armorStand = new FakeArmostand(Component.empty(), false, false);
        armorStand.setScale(visualScale);
        this.fakeEntity = armorStand;
    }

    @Override
    public void onSpawn() {
        super.onSpawn();
        switchStack();
    }

    protected void switchStack() {
        CanHaveItemInHand entity = (CanHaveItemInHand) this.fakeEntity;
        entity.setHeldItem(currentStack());
    }

    @Override
    public void tick() {
        super.tick();
        ItemStack[] skins = ((StackPetType) petType).getSkins();
        if (skins.length <= 1) return;
        ticksSinceSkinChange++;
        if (ticksSinceSkinChange < SKIN_CHANGE_INTERVAL_TICKS) return;
        ticksSinceSkinChange = 0;
        currentSkinIndex = (currentSkinIndex + 1) % skins.length;
        switchStack();
    }

    protected ItemStack currentStack() {
        ItemStack[] skins = ((StackPetType)petType).getSkins();
        return skins[currentSkinIndex];
    }

    @Override
    protected org.bukkit.Location getNametagLocation(org.bukkit.Location petLocation) {
        Vector horizontalOffset = getNametagHorizontalOffset(petLocation.getYaw());
        double verticalScaleCompensation = (visualScale - 1d) * HAND_CENTER_HEIGHT;
        return petLocation.clone()
                .add(horizontalOffset)
                .add(0, getPetType().getNameHeight() + verticalScaleCompensation, 0)
                .add(getPetType().getNametagRelativeLocation());
    }

    public Vector getNametagHorizontalOffset(float petYaw) {
        boolean playerHead = getPetType() instanceof HeadPetType;
        double angleOffset = playerHead ? CLIENT_HEAD_CENTER_ANGLE_OFFSET : 35d;
        double distance = playerHead
                ? CLIENT_HEAD_CENTER_RADIUS * visualScale
                : getPetType().getDistance() * visualScale;
        double angle = Math.toRadians(petYaw - angleOffset);
        return new Vector(-Math.cos(angle) * distance, 0, -Math.sin(angle) * distance);
    }

    public boolean usesSmallArmorStand() {
        return ((FakeArmostand) fakeEntity).isSmall();
    }

    public int getCurrentSkinIndex() {
        return currentSkinIndex;
    }

    public double getVisualScale() {
        return visualScale;
    }

}
