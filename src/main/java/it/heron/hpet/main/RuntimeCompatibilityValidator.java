package it.heron.hpet.main;

import it.heron.hpet.modules.pets.pettypes.CustomModelPetType;
import it.heron.hpet.modules.pets.pettypes.HeadPetType;
import it.heron.hpet.modules.pets.pettypes.StackPetType;
import it.heron.hpet.modules.pets.pettypes.MobPetType;
import it.heron.hpet.modules.messages.ComponentsHelper;
import it.heron.hpet.modules.pets.userpets.HandUserPet;
import it.heron.hpet.modules.pets.userpets.StackUserPet;
import it.heron.hpet.modules.pets.userpets.MobUserPet;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import it.heron.hpet.modules.pets.userpets.abstracts.AbstractUserPet;
import it.heron.hpet.modules.pets.userpets.animations.*;
import it.heron.hpet.modules.pets.userpets.animations.abstracts.IAnimation;
import it.heron.hpet.modules.pets.userpets.nametags.ArmorstandNametag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import it.heron.hpet.modules.DatabaseModule;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class RuntimeCompatibilityValidator {

    private RuntimeCompatibilityValidator() {
    }

    static void validate(PetPlugin plugin) {
        if (!plugin.isPacketEventsAvailable()) return;
        World world = plugin.getServer().getWorlds().stream().findFirst().orElse(null);
        if (world == null) throw new IllegalStateException("No world is available for the HPET runtime self-test");
        DatabaseModule databaseModule = (DatabaseModule) plugin.getModulesHandler().moduleByName("database");
        if (databaseModule == null || databaseModule.getDatabase() == null
                || databaseModule.getDatabase().getConnectionSource() == null) {
            throw new IllegalStateException("The HPET database is not available");
        }

        StackPetType petType = plugin.getApi().enabledPetTypes().stream()
                .filter(HeadPetType.class::isInstance)
                .map(HeadPetType.class::cast)
                .max(Comparator.comparingInt(type -> type.getSkins().length))
                .map(StackPetType.class::cast)
                .orElseGet(() -> plugin.getApi().enabledPetTypes().stream()
                        .filter(StackPetType.class::isInstance)
                        .map(StackPetType.class::cast)
                        .max(Comparator.comparingInt(type -> type.getSkins().length))
                        .orElseThrow(() -> new IllegalStateException("No valid pet type was loaded")));

        assertLegacyColors();
        assertPaperArmorStandMetadata();
        assertOriginalMovementProfiles(world);
        Inventory home = plugin.getPetGui().createHome();
        if (home.getSize() != 54 || home.getItem(49) == null) {
            throw new IllegalStateException("The main pet GUI was not generated correctly");
        }

        Entity owner = testEntity(world);
        UserPet pet = plugin.getApi().selectPet(owner, petType);
        if (!pet.isSpawned()) throw new IllegalStateException("The selected pet was not spawned");
        assertOriginalYaw(pet, owner.getLocation(), plugin);
        assertActiveNametagColors(pet, petType);
        if (!(pet.getNametag() instanceof ArmorstandNametag)) {
            throw new IllegalStateException("The pet nametag is not using the original armor stand renderer");
        }
        if (pet instanceof HandUserPet handPet && handPet.usesSmallArmorStand()) {
            throw new IllegalStateException("The main pet is using a small armor stand");
        }
        if (pet instanceof HandUserPet handPet
                && (handPet.getVisualScale() <= 1d || handPet.getVisualScale() > 1.2d)) {
            throw new IllegalStateException("The head pet visual scale is outside the expected range");
        }
        if (petType instanceof HeadPetType && pet instanceof HandUserPet handPet) {
            assertPaperHeadNametagCenter(handPet);
        }
        if (pet instanceof HandUserPet handPet && petType.getSkins().length > 1) {
            for (int tick = 0; tick < 13; tick++) handPet.tick();
            if (handPet.getCurrentSkinIndex() != 0) {
                throw new IllegalStateException("Animated pet skins change too quickly");
            }
            handPet.tick();
            if (handPet.getCurrentSkinIndex() != 1) {
                throw new IllegalStateException("Animated pet skins did not change after 14 ticks");
            }
        }
        assertNametagFollows(pet, world);
        pet.rename("&c&lColorTest");
        String renamed = LegacyComponentSerializer.legacySection().serialize(pet.getNametag().getName());
        if (!renamed.contains("\u00A7c\u00A7lColorTest")) {
            throw new IllegalStateException("Colored pet rename was not rendered correctly: " + renamed);
        }
        String plainRenamed = PlainTextComponentSerializer.plainText().serialize(pet.getNametag().getName());
        if (!plainRenamed.equals(plainRenamed.strip())) {
            throw new IllegalStateException("The active pet nametag contains leading or trailing spaces");
        }
        assertIconColors(petType.generateGuiIcon(null));
        plugin.getApi().removePet(pet);
        if (pet.isSpawned()) throw new IllegalStateException("The selected pet was not despawned");

        plugin.getApi().enabledPetTypes().stream()
                .filter(MobPetType.class::isInstance)
                .map(MobPetType.class::cast)
                .findFirst()
                .ifPresent(mobType -> {
                    UserPet mobPet = new MobUserPet(mobType, owner, 0);
                    mobPet.spawn();
                    if (!mobPet.isSpawned()) throw new IllegalStateException("The mob pet was not spawned");
                    mobPet.despawn();
                    assertIconColors(mobType.generateGuiIcon(null));
                });
        plugin.getLogger().info("Paper 26.2 runtime self-test passed (database selection, legacy colors, GUI, nametag, metadata, equipment and mob pets)");
    }

    private static void assertLegacyColors() {
        String serialized = LegacyComponentSerializer.legacySection()
                .serialize(ComponentsHelper.simpleParse("&4&lNzTMaldrak"));
        if (!serialized.contains("§4§lNzTMaldrak") || serialized.contains("&4&l")) {
            throw new IllegalStateException("Legacy GUI colors were not converted: " + serialized);
        }
    }

    private static void assertPaperArmorStandMetadata() {
        assertNmsMetadataIndex("DATA_CLIENT_FLAGS", 15);
        assertNmsMetadataIndex("DATA_LEFT_ARM_POSE", 18);
        assertNmsMetadataIndex("DATA_RIGHT_ARM_POSE", 19);
    }

    private static void assertNmsMetadataIndex(String fieldName, int expected) {
        try {
            Class<?> armorStand = Class.forName("net.minecraft.world.entity.decoration.ArmorStand");
            Field field = armorStand.getDeclaredField(fieldName);
            Object accessor = field.get(null);
            Method id = accessor.getClass().getMethod("id");
            int actual = (int) id.invoke(accessor);
            if (actual != expected) {
                throw new IllegalStateException(fieldName + " uses metadata index " + actual
                        + " instead of " + expected);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not verify Paper 26.2 armor stand metadata", exception);
        }
    }

    private static void assertOriginalMovementProfiles(World world) {
        if (AbstractUserPet.movementIntervalTicks() != 2) {
            throw new IllegalStateException("Pet movement is not running at the original 2-tick interval");
        }
        Location location = world.getSpawnLocation();
        assertAnimationHeight(new GlideAnimation(), location, 0.2d, "GLIDE");
        assertAnimationHeight(new SlowGlideAnimation(), location, 0.2d, "SLOW_GLIDE");
        assertAnimationHeight(new BounceAnimation(), location, 0.2d, "BOUNCE");
        assertAnimationHeight(new GlitchAnimation(), location, 0.2d, "GLITCH");
        assertAnimationHeight(new SideAnimation(), location, -0.9d, "SIDE");
        assertAnimationHeight(new NoAnimation(), location, 0.5d, "NONE");
        assertAnimationHeight(new FollowAnimation(), location, 0.5d, "FOLLOW");
        double walkHeight = new WalkAnimation().relativeLocation(location).getY();
        if (!Double.isFinite(walkHeight)) {
            throw new IllegalStateException("WALK produced an invalid ground offset");
        }
    }

    private static void assertOriginalYaw(UserPet pet, Location ownerLocation, PetPlugin plugin) {
        if (AbstractUserPet.originalBaseYaw() != 200f) {
            throw new IllegalStateException("The original fixed 200 degree pet rotation is missing");
        }
        float expected = ownerLocation.getYaw() + 200f + pet.getPetType().getYaw()
                + (float) plugin.getConfig().getDouble("fix.yawCalibration", 0d);
        if (Math.abs(pet.getLocation().getYaw() - expected) > 0.001f) {
            throw new IllegalStateException("Per-pet yaw does not include the original base rotation");
        }
    }

    private static void assertActiveNametagColors(UserPet pet, StackPetType type) {
        if (type.getDisplayName() == null) return;
        String configured = LegacyComponentSerializer.legacySection().serialize(type.getDisplayName());
        String active = LegacyComponentSerializer.legacySection().serialize(pet.getNametag().getName());
        if (configured.indexOf('\u00A7') >= 0 && active.indexOf('\u00A7') < 0) {
            throw new IllegalStateException("The active pet nametag lost its configured colors");
        }
    }

    private static void assertAnimationHeight(IAnimation animation, Location location,
                                              double expected, String name) {
        animation.nextStep();
        double actual = animation.relativeLocation(location).getY();
        if (Math.abs(actual - expected) > 0.0001d) {
            throw new IllegalStateException(name + " does not match the original movement profile");
        }
    }

    private static void assertIconColors(ItemStack icon) {
        if (icon == null || !icon.hasItemMeta() || icon.getItemMeta().displayName() == null) {
            throw new IllegalStateException("A pet GUI icon has no display name");
        }
        String serialized = LegacyComponentSerializer.legacySection().serialize(icon.getItemMeta().displayName());
        if (serialized.contains("&0") || serialized.contains("&1") || serialized.contains("&2")
                || serialized.contains("&3") || serialized.contains("&4") || serialized.contains("&5")
                || serialized.contains("&6") || serialized.contains("&7") || serialized.contains("&8")
                || serialized.contains("&9") || serialized.contains("&a") || serialized.contains("&b")
                || serialized.contains("&c") || serialized.contains("&d") || serialized.contains("&e")
                || serialized.contains("&f") || serialized.contains("&l")) {
            throw new IllegalStateException("A pet GUI icon still contains literal legacy color codes: " + serialized);
        }
    }

    private static void assertNametagFollows(UserPet pet, World world) {
        if (!pet.getNametag().isShown()) return;
        Location first = pet.getNametag().getLocation();
        if (first == null) throw new IllegalStateException("The pet nametag has no location");
        Location moved = pet.getLocation().clone().add(10, 5, -10);
        moved.setYaw(90);
        moved.setPitch(0);
        pet.teleport(moved);
        Location second = pet.getNametag().getLocation();
        if (second == null || first.distanceSquared(second) < 1d) {
            throw new IllegalStateException("The pet nametag did not follow the pet");
        }
        if (!world.equals(second.getWorld()) || second.distanceSquared(pet.getLocation()) > 16d) {
            throw new IllegalStateException("The pet nametag is not positioned over the pet");
        }
    }

    private static void assertPaperHeadNametagCenter(HandUserPet pet) {
        Vector yaw0 = pet.getNametagHorizontalOffset(0f);
        if (!Double.isFinite(yaw0.getX()) || !Double.isFinite(yaw0.getZ())
                || yaw0.lengthSquared() < 0.01d) {
            throw new IllegalStateException("The Paper 26.2 head transform produced an invalid centre");
        }
        // Rotating the entity by a quarter turn must rotate the calculated
        // centre by exactly the same quarter turn; no fitted angle is involved.
        assertHorizontalOffset(pet.getNametagHorizontalOffset(90f),
                -yaw0.getZ(), yaw0.getX(), "yaw 90");
        assertHorizontalOffset(pet.getNametagHorizontalOffset(180f),
                -yaw0.getX(), -yaw0.getZ(), "yaw 180");
        assertHorizontalOffset(pet.getNametagHorizontalOffset(270f),
                yaw0.getZ(), -yaw0.getX(), "yaw 270");
    }

    private static void assertHorizontalOffset(Vector actual, double expectedX,
                                               double expectedZ, String direction) {
        if (Math.abs(actual.getX() - expectedX) > 0.002d
                || Math.abs(actual.getZ() - expectedZ) > 0.002d) {
            throw new IllegalStateException("The Paper 26.2 head nametag is not centred at "
                    + direction + ": " + actual);
        }
    }

    private static Entity testEntity(World world) {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000060");
        Location location = world.getSpawnLocation().clone();
        return (Entity) Proxy.newProxyInstance(
                RuntimeCompatibilityValidator.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> location.clone();
                    case "getWorld" -> world;
                    case "getName" -> "HPET-SelfTest";
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "toString" -> "HPET-SelfTest-Entity";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == Optional.class) return Optional.empty();
            if (type == List.class || type == Collection.class) return List.of();
            if (type == Set.class) return Set.of();
            if (type == Map.class) return Map.of();
            if (type == Component.class) return Component.empty();
            return null;
        }
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
