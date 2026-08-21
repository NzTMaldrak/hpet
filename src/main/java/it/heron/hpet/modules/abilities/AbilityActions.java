package it.heron.hpet.modules.abilities;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.messages.ComponentsHelper;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class AbilityActions {

    private AbilityActions() {
    }

    static AbilityExecutionResult execute(
            AbilityDefinition definition, UserPet userPet, long remainingDailyMillis) {
        Player owner = Bukkit.getPlayer(userPet.getOwner());
        if (owner == null || !owner.isOnline()) return AbilityExecutionResult.immediate();
        List<String> args = definition.arguments();

        return switch (definition.type()) {
            case ADD_HEALTH -> addHealth(owner, number(args, 0));
            case GLOW -> {
                userPet.setGlowing(true);
                yield AbilityExecutionResult.persistent(() -> userPet.setGlowing(false));
            }
            case TITLE -> {
                owner.showTitle(net.kyori.adventure.title.Title.title(
                        ComponentsHelper.simpleParse(replace(args.getFirst(), userPet, owner)),
                        net.kyori.adventure.text.Component.empty()));
                yield AbilityExecutionResult.immediate();
            }
            case SUBTITLE -> {
                owner.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.empty(),
                        ComponentsHelper.simpleParse(replace(args.getFirst(), userPet, owner))));
                yield AbilityExecutionResult.immediate();
            }
            case MESSAGE -> {
                owner.sendMessage(ComponentsHelper.simpleParse(replace(args.getFirst(), userPet, owner)));
                yield AbilityExecutionResult.immediate();
            }
            case CONSOLE_LOG -> {
                Bukkit.getLogger().info(replace(args.getFirst(), userPet, owner));
                yield AbilityExecutionResult.immediate();
            }
            case PLAYER_COMMAND -> {
                String command = replace(args.getFirst(), userPet, owner);
                owner.performCommand(command.startsWith("/") ? command.substring(1) : command);
                yield AbilityExecutionResult.immediate();
            }
            case CONSOLE_COMMAND -> {
                String command = replace(args.getFirst(), userPet, owner);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
                yield AbilityExecutionResult.immediate();
            }
            case ADD_FOOD -> {
                owner.setFoodLevel(Math.clamp(owner.getFoodLevel() + integer(args, 0), 0, 20));
                yield AbilityExecutionResult.immediate();
            }
            case SHOOT_ENDERPEARL -> projectile(owner, EnderPearl.class);
            case SHOOT_ARROW -> projectile(owner, Arrow.class);
            case SHOOT_SNOWBALL -> projectile(owner, Snowball.class);
            case FIREBALL -> projectile(owner, Fireball.class);
            case DAMAGE -> {
                owner.damage(number(args, 0));
                yield AbilityExecutionResult.immediate();
            }
            case HEAL -> {
                double health = args.isEmpty() ? owner.getMaxHealth() : owner.getHealth() + number(args, 0);
                owner.setHealth(Math.clamp(health, 0d, owner.getMaxHealth()));
                yield AbilityExecutionResult.immediate();
            }
            case POISON_NEAR -> {
                PotionEffect effect = new PotionEffect(PotionEffectType.POISON, 60, 0);
                nearbyLiving(owner).forEach(entity -> entity.addPotionEffect(effect));
                yield AbilityExecutionResult.immediate();
            }
            case PLAYER_PARTICLE -> {
                owner.spawnParticle(particle(args.get(0)), owner.getLocation(), integer(args, 1));
                yield AbilityExecutionResult.immediate();
            }
            case PET_PARTICLE -> {
                owner.getWorld().spawnParticle(particle(args.get(0)), userPet.getLocation(), integer(args, 1));
                yield AbilityExecutionResult.immediate();
            }
            case LAUNCH -> {
                double power = args.isEmpty() ? 1d : number(args, 0);
                owner.setVelocity(owner.getVelocity().setY(power));
                yield AbilityExecutionResult.immediate();
            }
            case VELOCITY -> {
                double power = number(args, 0);
                yield movementSpeed(owner, power);
            }
            case CURE -> {
                cure(owner, args.getFirst());
                yield AbilityExecutionResult.immediate();
            }
            case DISARM_OPPONENT -> {
                nearestPlayer(owner).ifPresent(player -> player.dropItem(true));
                yield AbilityExecutionResult.immediate();
            }
            case DISARM_SELF -> {
                owner.dropItem(true);
                yield AbilityExecutionResult.immediate();
            }
            case INCREASE_DAMAGE -> potion(owner, PotionEffectType.STRENGTH,
                    millisToTicks(dailyDuration(definition.cooldownMillis() + 100L, remainingDailyMillis)), 0);
            case EXP -> {
                owner.giveExp(integer(args, 0));
                yield AbilityExecutionResult.immediate();
            }
            case EXPLOSION -> {
                float power = (float) number(args, 0);
                boolean destroyBlocks = bool(args, 1);
                boolean incendiary = bool(args, 2);
                owner.getWorld().createExplosion(owner.getLocation(), power, incendiary, destroyBlocks, owner);
                yield AbilityExecutionResult.immediate();
            }
            case EXTINGUISH -> {
                owner.setFireTicks(0);
                yield AbilityExecutionResult.immediate();
            }
            case SET_FIRE -> {
                owner.setFireTicks(integer(args, 0) * 20);
                yield AbilityExecutionResult.immediate();
            }
            case TEMP_FLY -> temporaryFlight(owner,
                    dailyDuration(seconds(args, 0), remainingDailyMillis));
            case FLY -> flight(owner, 0L);
            case GOD -> invulnerability(owner, 0L);
            case TEMP_GOD -> invulnerability(owner,
                    dailyDuration(seconds(args, 0), remainingDailyMillis));
            case FREEZE -> freeze(owner,
                    dailyDuration(seconds(args, 0), remainingDailyMillis));
            case INVISIBLE -> invisibility(owner);
            case LIGHNING_ON_PLAYER -> {
                owner.getWorld().strikeLightning(owner.getLocation());
                yield AbilityExecutionResult.immediate();
            }
            case LIGHTNING_LOOKING -> {
                Location target = owner.getTargetBlockExact(100) == null
                        ? owner.getEyeLocation().add(owner.getEyeLocation().getDirection().multiply(20d))
                        : owner.getTargetBlockExact(100).getLocation();
                owner.getWorld().strikeLightning(target);
                yield AbilityExecutionResult.immediate();
            }
            case POTION -> potion(owner, potionType(args.get(0)),
                    millisToTicks(dailyDuration(integer(args, 1) * 1_000L, remainingDailyMillis)),
                    integer(args, 2));
            case PLAY_SOUND -> {
                owner.playSound(owner.getLocation(), sound(args.get(0)), (float) number(args, 2), (float) number(args, 1));
                yield AbilityExecutionResult.immediate();
            }
            case PLAY_SOUND_EVERYONE -> {
                Sound sound = sound(args.get(0));
                float pitch = (float) number(args, 1);
                float volume = (float) number(args, 2);
                owner.getWorld().getPlayers().forEach(player -> player.playSound(owner.getLocation(), sound, volume, pitch));
                yield AbilityExecutionResult.immediate();
            }
            case PUMPKIN -> fakeEquipment(owner, EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN),
                    dailyDuration(args.isEmpty() ? definition.cooldownMillis() : seconds(args, 0),
                            remainingDailyMillis));
            case SWAP_ITEMS -> {
                List<ItemStack> hotbar = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) hotbar.add(owner.getInventory().getItem(slot));
                Collections.shuffle(hotbar);
                for (int slot = 0; slot < 9; slot++) owner.getInventory().setItem(slot, hotbar.get(slot));
                yield AbilityExecutionResult.immediate();
            }
            case INVISIBLE_ARMOR -> fakeArmor(owner, new ItemStack(Material.AIR));
            case INVISIBLE_HAND -> fakeEquipment(owner, EquipmentSlot.HAND, new ItemStack(Material.AIR), 0L);
            case FAKE_HAND -> fakeEquipment(owner, EquipmentSlot.HAND,
                    new ItemStack(material(args.getFirst())), 0L);
            case FAKE_ARMOR -> {
                EquipmentSlot slot = equipmentSlot(args.get(0));
                ItemStack stack = new ItemStack(material(args.get(1)));
                if (args.size() == 3) {
                    var meta = stack.getItemMeta();
                    meta.setCustomModelData(integer(args, 2));
                    stack.setItemMeta(meta);
                }
                yield fakeEquipment(owner, slot, stack, 0L);
            }
            case FAKE_LOCATION -> fakeLocation(owner, remainingDailyMillis);
            case NO_KNOCKBACK, NO_FALL_DAMAGE -> AbilityExecutionResult.immediate();
        };
    }

    private static AbilityExecutionResult addHealth(Player player, double amount) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) throw new IllegalStateException("Player has no max-health attribute");
        NamespacedKey key = new NamespacedKey(PetPlugin.getInstance(),
                "ability_health_" + UUID.randomUUID().toString().replace("-", ""));
        AttributeModifier modifier = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER);
        attribute.addTransientModifier(modifier);
        return AbilityExecutionResult.persistent(() -> {
            attribute.removeModifier(modifier);
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        });
    }

    private static AbilityExecutionResult movementSpeed(Player player, double multiplier) {
        if (multiplier <= 0d) throw new IllegalArgumentException("VELOCITY power must be greater than zero");
        float previous = player.getWalkSpeed();
        float applied = (float) Math.clamp(previous * multiplier, -1d, 1d);
        player.setWalkSpeed(applied);
        return AbilityExecutionResult.persistent(() -> {
            if (Math.abs(player.getWalkSpeed() - applied) < 0.0001f) player.setWalkSpeed(previous);
        });
    }

    private static AbilityExecutionResult temporaryFlight(Player player, long durationMillis) {
        return flight(player, durationMillis);
    }

    private static AbilityExecutionResult flight(Player player, long durationMillis) {
        boolean previous = player.getAllowFlight();
        player.setAllowFlight(true);
        Runnable cleanup = () -> {
            player.setFlying(false);
            player.setAllowFlight(previous);
        };
        return durationMillis > 0L ? AbilityExecutionResult.temporary(durationMillis, cleanup)
                : AbilityExecutionResult.persistent(cleanup);
    }

    private static AbilityExecutionResult invulnerability(Player player, long durationMillis) {
        boolean previous = player.isInvulnerable();
        player.setInvulnerable(true);
        Runnable cleanup = () -> player.setInvulnerable(previous);
        return durationMillis > 0L ? AbilityExecutionResult.temporary(durationMillis, cleanup)
                : AbilityExecutionResult.persistent(cleanup);
    }

    private static AbilityExecutionResult freeze(Player player, long durationMillis) {
        float previousSpeed = player.getWalkSpeed();
        player.setWalkSpeed(0f);
        player.setFreezeTicks(player.getMaxFreezeTicks());
        return AbilityExecutionResult.temporary(durationMillis, () -> {
            player.setWalkSpeed(previousSpeed);
            player.setFreezeTicks(0);
        });
    }

    private static AbilityExecutionResult invisibility(Player player) {
        boolean previous = player.isInvisible();
        player.setInvisible(true);
        return AbilityExecutionResult.persistent(() -> player.setInvisible(previous));
    }

    private static AbilityExecutionResult potion(Player player, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect previous = player.getPotionEffect(type);
        PotionEffect applied = new PotionEffect(type, Math.max(1, ticks), Math.max(0, amplifier));
        player.addPotionEffect(applied, true);
        return AbilityExecutionResult.temporary(ticks * 50L, () -> {
            PotionEffect current = player.getPotionEffect(type);
            if (current != null && current.getAmplifier() != applied.getAmplifier()) return;
            if (current != null) player.removePotionEffect(type);
            if (previous != null) player.addPotionEffect(previous, true);
        });
    }

    private static AbilityExecutionResult fakeArmor(Player player, ItemStack item) {
        sendEquipment(player, EquipmentSlot.HEAD, item);
        sendEquipment(player, EquipmentSlot.CHEST, item);
        sendEquipment(player, EquipmentSlot.LEGS, item);
        sendEquipment(player, EquipmentSlot.FEET, item);
        return AbilityExecutionResult.persistent(() -> {
            restoreEquipment(player, EquipmentSlot.HEAD);
            restoreEquipment(player, EquipmentSlot.CHEST);
            restoreEquipment(player, EquipmentSlot.LEGS);
            restoreEquipment(player, EquipmentSlot.FEET);
        });
    }

    private static AbilityExecutionResult fakeEquipment(Player player, EquipmentSlot slot, ItemStack item, long durationMillis) {
        sendEquipment(player, slot, item);
        Runnable cleanup = () -> restoreEquipment(player, slot);
        return durationMillis > 0L ? AbilityExecutionResult.temporary(durationMillis, cleanup)
                : AbilityExecutionResult.persistent(cleanup);
    }

    private static AbilityExecutionResult fakeLocation(Player player, long remainingDailyMillis) {
        Location fake = player.getLocation().clone().add(
                Math.random() * 20d - 10d,
                Math.random() * 10d,
                Math.random() * 20d - 10d);
        sendTeleport(player, fake);
        return AbilityExecutionResult.temporary(
                dailyDuration(1_000L, remainingDailyMillis),
                () -> sendTeleport(player, player.getLocation()));
    }

    private static void sendTeleport(Player entity, Location location) {
        var converted = new com.github.retrooper.packetevents.protocol.world.Location(
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        var packet = new WrapperPlayServerEntityTeleport(entity.getEntityId(), converted, entity.isOnGround());
        for (Player viewer : entity.getWorld().getPlayers()) {
            if (!viewer.equals(entity)) PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        }
    }

    private static void sendEquipment(Player entity, EquipmentSlot slot, ItemStack item) {
        Equipment equipment = new Equipment(packetSlot(slot), SpigotConversionUtil.fromBukkitItemStack(item));
        var packet = new WrapperPlayServerEntityEquipment(entity.getEntityId(), List.of(equipment));
        for (Player viewer : entity.getWorld().getPlayers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        }
    }

    private static void restoreEquipment(Player player, EquipmentSlot slot) {
        sendEquipment(player, slot, player.getInventory().getItem(slot));
    }

    private static com.github.retrooper.packetevents.protocol.player.EquipmentSlot packetSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND;
            case FEET -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS;
            case LEGS -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS;
            case CHEST -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE;
            case HEAD -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET;
            case BODY -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BODY;
            case SADDLE -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.SADDLE;
        };
    }

    private static List<LivingEntity> nearbyLiving(Player owner) {
        return owner.getNearbyEntities(5d, 5d, 5d).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(entity -> !entity.equals(owner))
                .toList();
    }

    private static java.util.Optional<Player> nearestPlayer(Player owner) {
        return owner.getNearbyEntities(5d, 5d, 5d).stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(owner.getLocation())));
    }

    private static <T extends org.bukkit.entity.Projectile> AbilityExecutionResult projectile(Player player, Class<T> type) {
        player.launchProjectile(type);
        return AbilityExecutionResult.immediate();
    }

    private static void cure(Player player, String effectName) {
        if (effectName.equalsIgnoreCase("ALL")) {
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            return;
        }
        player.removePotionEffect(potionType(effectName));
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType potionType(String name) {
        String normalized = switch (name.toUpperCase(Locale.ROOT)) {
            case "JUMP" -> "JUMP_BOOST";
            case "SLOW" -> "SLOWNESS";
            case "INCREASE_DAMAGE" -> "STRENGTH";
            default -> name.toUpperCase(Locale.ROOT);
        };
        PotionEffectType type = PotionEffectType.getByName(normalized);
        if (type == null) throw new IllegalArgumentException("Unknown potion effect: " + name);
        return type;
    }

    private static Particle particle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown particle: " + name, exception);
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static Sound sound(String name) {
        Sound sound = Registry.SOUNDS.match(name);
        if (sound == null) sound = Registry.SOUNDS.match(name.toLowerCase(Locale.ROOT));
        if (sound == null) {
            try {
                sound = Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_'));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (sound == null) throw new IllegalArgumentException("Unknown sound: " + name);
        return sound;
    }

    private static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) throw new IllegalArgumentException("Unknown material: " + name);
        return material;
    }

    private static EquipmentSlot equipmentSlot(String name) {
        try {
            return EquipmentSlot.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown equipment slot: " + name, exception);
        }
    }

    private static String replace(String input, UserPet userPet, Player owner) {
        return input.replace("%player%", owner.getName()).replace("%pet%", userPet.getPetType().getName());
    }

    private static int integer(List<String> args, int index) {
        try {
            return Integer.parseInt(args.get(index));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Argument " + (index + 1) + " must be an integer", exception);
        }
    }

    private static double number(List<String> args, int index) {
        try {
            return Double.parseDouble(args.get(index));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Argument " + (index + 1) + " must be a number", exception);
        }
    }

    private static boolean bool(List<String> args, int index) {
        String value = args.get(index);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Argument " + (index + 1) + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static long seconds(List<String> args, int index) {
        return Math.max(50L, Math.round(number(args, index) * 1_000d));
    }

    private static int millisToTicks(long millis) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, millis / 50L));
    }

    private static long dailyDuration(long configuredMillis, long remainingDailyMillis) {
        return remainingDailyMillis > 0L
                ? Math.min(configuredMillis, remainingDailyMillis)
                : configuredMillis;
    }
}
