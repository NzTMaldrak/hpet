package it.heron.hpet.modules.abilities;

import it.heron.hpet.database.tables.DailyAbilityUsage;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class PetAbilityRuntime {

    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();
    private static final long SAVE_INTERVAL_MILLIS = 30_000L;

    private final UserPet userPet;
    private final List<State> states;
    private boolean active;

    public PetAbilityRuntime(UserPet userPet, List<AbilityDefinition> definitions) {
        this.userPet = userPet;
        List<State> createdStates = new ArrayList<>();
        for (AbilityDefinition definition : definitions) {
            createdStates.add(new State(definition, userPet.getOwner(), userPet.getPetType().getName()));
        }
        this.states = List.copyOf(createdStates);
    }

    public void activate() {
        if (active) return;
        active = true;
        long now = System.currentTimeMillis();
        for (State state : states) state.reset(now);
    }

    public void deactivate() {
        if (!active) return;
        long now = System.currentTimeMillis();
        active = false;
        for (State state : states) {
            state.updateDailyUsage(now);
            state.cleanup(now);
            state.saveDailyUsage();
        }
    }

    public void tick() {
        if (!active) return;
        long now = System.currentTimeMillis();
        updateDailyUsage(now);
        expireEffects(now);
        trigger(AbilityTrigger.TIME, now);
    }

    public void trigger(AbilityTrigger trigger) {
        if (!active) return;
        long now = System.currentTimeMillis();
        updateDailyUsage(now);
        expireEffects(now);
        trigger(trigger, now);
    }

    public boolean protectsFrom(AbilityType type) {
        if (!active || (type != AbilityType.NO_FALL_DAMAGE && type != AbilityType.NO_KNOCKBACK)) return false;
        long now = System.currentTimeMillis();
        return states.stream().anyMatch(state -> {
            state.updateDailyUsage(now);
            return state.definition.type() == type
                    && state.hasDailyTime()
                    && userPet.getLevel() >= state.definition.requiredLevel();
        });
    }

    private void trigger(AbilityTrigger trigger, long now) {
        for (State state : states) {
            AbilityDefinition definition = state.definition;
            if (definition.trigger() != trigger || now < state.nextEligibleAt) continue;
            if (state.cleanupAt > now) continue;
            if (!state.hasDailyTime()) continue;
            if (userPet.getLevel() < definition.requiredLevel()) continue;

            state.nextEligibleAt = now + definition.cooldownMillis();
            if (!state.firstAttemptMade) {
                state.firstAttemptMade = true;
                if (definition.skipFirstRun()) continue;
            }
            if (ThreadLocalRandom.current().nextDouble(100d) >= definition.chancePercent()) continue;

            try {
                state.cleanup(now);
                AbilityExecutionResult result = AbilityActions.execute(
                        definition, userPet, state.remainingDailyMillis());
                state.cleanup = result.cleanup();
                state.cleanupAt = result.cleanupAfterMillis() > 0L ? now + result.cleanupAfterMillis() : -1L;
                state.startDailyUsage(now);
                if (state.cleanupAt > 0L) {
                    state.nextEligibleAt = state.cleanupAt + definition.cooldownMillis();
                } else if (result.cleanupAfterMillis() == 0L) {
                    state.nextEligibleAt = Long.MAX_VALUE;
                }
            } catch (RuntimeException exception) {
                Bukkit.getLogger().log(Level.SEVERE,
                        "Could not execute " + definition.type() + " for pet "
                                + userPet.getPetType().getName() + " owned by " + userPet.getOwner(),
                        exception);
            }
        }
    }

    private void expireEffects(long now) {
        for (State state : states) {
            if (state.cleanupAt > 0L && now >= state.cleanupAt) state.cleanup(now);
        }
    }

    private void updateDailyUsage(long now) {
        for (State state : states) state.updateDailyUsage(now);
    }

    private static String abilityKey(AbilityDefinition definition) {
        String canonical = definition.type() + "|" + String.join("\u001f", definition.arguments())
                + "|" + definition.trigger() + "|" + definition.requiredLevel();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static final class State {
        private final AbilityDefinition definition;
        private long nextEligibleAt;
        private long cleanupAt = -1L;
        private boolean firstAttemptMade;
        private Runnable cleanup = () -> { };
        private final DailyAbilityUsage dailyUsage;
        private boolean consumingDailyTime;
        private long lastUsageUpdateAt;
        private long lastUsageSaveAt;

        private State(AbilityDefinition definition, UUID owner, String petType) {
            this.definition = definition;
            this.dailyUsage = definition.hasDailyAllowance()
                    ? DailyAbilityUsage.loadOrCreate(
                            owner, petType, abilityKey(definition), LocalDate.now(SERVER_ZONE))
                    : null;
        }

        private void reset(long now) {
            cleanup(now);
            firstAttemptMade = false;
            lastUsageUpdateAt = now;
            resetDailyUsageIfNeeded(now);
            nextEligibleAt = hasDailyTime()
                    ? (definition.trigger() == AbilityTrigger.TIME ? now : 0L)
                    : Long.MAX_VALUE;
        }

        private void cleanup(long now) {
            try {
                cleanup.run();
            } catch (RuntimeException exception) {
                Bukkit.getLogger().log(Level.WARNING, "Could not clean up pet ability " + definition.type(), exception);
            } finally {
                cleanup = () -> { };
                cleanupAt = -1L;
                consumingDailyTime = false;
                lastUsageUpdateAt = now;
            }
        }

        private void startDailyUsage(long now) {
            if (dailyUsage == null) return;
            consumingDailyTime = true;
            lastUsageUpdateAt = now;
        }

        private void updateDailyUsage(long now) {
            if (dailyUsage == null) return;
            boolean reset = resetDailyUsageIfNeeded(now);
            if (reset && !consumingDailyTime) nextEligibleAt = now;

            if (consumingDailyTime) {
                long effectiveNow = cleanupAt > 0L ? Math.min(now, cleanupAt) : now;
                long elapsed = Math.max(0L, effectiveNow - lastUsageUpdateAt);
                if (elapsed > 0L) {
                    dailyUsage.setUsedMillis(Math.min(
                            definition.dailyAllowanceMillis(),
                            dailyUsage.getUsedMillis() + elapsed));
                }
                lastUsageUpdateAt = effectiveNow;
            } else {
                lastUsageUpdateAt = now;
            }

            if (!hasDailyTime()) {
                boolean newlyExhausted = consumingDailyTime;
                cleanup(now);
                nextEligibleAt = Long.MAX_VALUE;
                if (newlyExhausted) saveDailyUsage();
            } else if (now - lastUsageSaveAt >= SAVE_INTERVAL_MILLIS) {
                saveDailyUsage();
            }
        }

        private boolean resetDailyUsageIfNeeded(long now) {
            if (dailyUsage == null) return false;
            boolean reset = dailyUsage.resetIfNewDay(LocalDate.now(SERVER_ZONE));
            if (reset) {
                lastUsageUpdateAt = now;
                saveDailyUsage();
            }
            return reset;
        }

        private boolean hasDailyTime() {
            return dailyUsage == null
                    || dailyUsage.getUsedMillis() < definition.dailyAllowanceMillis();
        }

        private long remainingDailyMillis() {
            if (dailyUsage == null) return -1L;
            return Math.max(0L, definition.dailyAllowanceMillis() - dailyUsage.getUsedMillis());
        }

        private void saveDailyUsage() {
            if (dailyUsage == null) return;
            dailyUsage.save();
            lastUsageSaveAt = System.currentTimeMillis();
        }
    }
}
