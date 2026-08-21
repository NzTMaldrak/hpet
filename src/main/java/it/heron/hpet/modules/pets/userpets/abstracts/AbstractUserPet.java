package it.heron.hpet.modules.pets.userpets.abstracts;

import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.invisibilityintegration.InvisibilityHandler;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.userpets.animations.abstracts.IAnimation;
import it.heron.hpet.modules.pets.userpets.nametags.INametag;
import it.heron.hpet.modules.pets.userpets.nametags.NametagGenerator;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.UUID;
import it.heron.hpet.modules.pets.userpets.animations.*;
import java.util.Locale;

public abstract class AbstractUserPet implements UserPet {

    private static final int UNSPAWNED_ID = -1;
    private static final int MOVEMENT_INTERVAL_TICKS = 2;
    private static final float ORIGINAL_BASE_YAW = 200f;

    @Getter
    protected Location location;

    @Getter
    protected UUID owner;

    @Getter @Setter
    protected int level;
    @Getter
    protected PetType petType;
    @Getter
    protected boolean visible = true; // doesn't affect vanish, this value will be true if pet is vanished
    @Getter
    protected boolean vanished = false;
    @Getter
    protected int id = -1;
    @Getter
    protected IAnimation animation;
    @Getter
    protected INametag nametag;

    private boolean currentVisibilityState = true; // current visibility state, shouldn't be used externally
    private int movementTicks = 0;

    public AbstractUserPet(@NonNull PetType petType, @NonNull Entity owner, int level) {
        this.petType = petType;
        this.owner = owner.getUniqueId();
        this.level = level;
        net.kyori.adventure.text.Component displayName = getPetType().getDisplayName();
        if (displayName == null) displayName = net.kyori.adventure.text.Component.text(getPetType().getName());
        this.nametag = NametagGenerator.getFormattedNametag(displayName, level, owner.getName());
        this.animation = createAnimation(petType.getAnimationName());
        this.location = positionFromOwner(owner.getLocation(), new Vector());
    }

    @Override
    public void teleport(Location location) {
        if(!currentVisibilityState) return;
        if(location == null) return;
        this.location = location.clone();
    }

    @Override
    public boolean isSpawned() {
        return this.id != UNSPAWNED_ID;
    }

    @Override
    public void spawn() {
        if(isSpawned()) despawn();
        onSpawn();
        nametag.show(getNametagLocation(location));
        if(this.id == UNSPAWNED_ID) throw new RuntimeException("There was an error while spawning the Pet");
    }

    @Override
    public void despawn() {
        if(!isSpawned()) return;
        nametag.hide();
        onDespawn();
        this.id = UNSPAWNED_ID;
    }

    @Override
    public void tick() {
        Entity ownerEntity = Bukkit.getEntity(this.owner);
        if (ownerEntity == null || !ownerEntity.isValid()) return;
        InvisibilityHandler handler = (InvisibilityHandler) PetPlugin.getInstance().getModulesHandler().moduleByName("Vanish");
        this.vanished = handler.isInvisible(ownerEntity);

        applyVisibilityState(!this.vanished && this.visible);
        movementTicks++;
        if (movementTicks >= MOVEMENT_INTERVAL_TICKS) {
            movementTicks = 0;
            teleport(getNextLocation());
        }

        if(petType.getAbility() != null) {
            petType.getAbility().execute(this);
        }
    }

    @Override
    public void rename(String name) {
        Entity ownerEntity = Bukkit.getEntity(owner);
        NametagGenerator.changeNametagFormatted(nametag, name, level,
                ownerEntity == null ? "" : ownerEntity.getName());
    }

    @Override
    public void setVisible(boolean state) {
        this.visible = state;
        applyVisibilityState(this.visible && !this.vanished);
    }

    protected Location getNextLocation() {
        animation.nextStep();
        Location ownerLocation = Bukkit.getEntity(owner).getLocation();
        return positionFromOwner(ownerLocation, animation.relativeLocation(ownerLocation));
    }

    private Location positionFromOwner(Location ownerLocation, Vector animationOffset) {
        Vector relativeLocation = petType.getRelativeLocation().clone().add(animationOffset);
        Location next = ownerLocation.clone().add(relativeLocation);
        float globalCalibration = (float) PetPlugin.getInstance().getConfig()
                .getDouble("fix.yawCalibration", 0d);
        next.setYaw(ownerLocation.getYaw() + ORIGINAL_BASE_YAW + petType.getYaw() + globalCalibration);
        return next;
    }

    protected Location getNametagLocation(Location petLocation) {
        return petLocation.clone()
                .add(0, petType.getNameHeight(), 0)
                .add(petType.getNametagRelativeLocation());
    }

    private IAnimation createAnimation(String animationName) {
        String name = animationName == null ? "follow" : animationName.toLowerCase(Locale.ROOT);
        return switch (name) {
            case "bounce" -> new BounceAnimation();
            case "glide" -> new GlideAnimation();
            case "slow_glide" -> new SlowGlideAnimation();
            case "glitch" -> new GlitchAnimation();
            case "side" -> new SideAnimation();
            case "walk" -> new WalkAnimation();
            case "follow" -> new FollowAnimation();
            case "none" -> new NoAnimation();
            default -> new GlideAnimation();
        };
    }

    private void applyVisibilityState(boolean state) {
        if(this.currentVisibilityState == state) return;
        if(state) {
            spawn();
            this.currentVisibilityState = true;
        } else {
            despawn();
            this.currentVisibilityState = false;
        }
    }

    protected abstract void onSpawn();
    protected abstract void onDespawn();

    public static int movementIntervalTicks() {
        return MOVEMENT_INTERVAL_TICKS;
    }

    public static float originalBaseYaw() {
        return ORIGINAL_BASE_YAW;
    }

}
