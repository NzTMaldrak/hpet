package it.heron.hpet.modules.abilities;

import java.util.List;

public record AbilityDefinition(
        AbilityType type,
        List<String> arguments,
        double chancePercent,
        long cooldownMillis,
        int requiredLevel,
        AbilityTrigger trigger,
        boolean skipFirstRun,
        long dailyAllowanceMillis
) {
    public AbilityDefinition {
        arguments = List.copyOf(arguments);
        if (!Double.isFinite(chancePercent) || chancePercent < 0d || chancePercent > 100d) {
            throw new IllegalArgumentException("chance must be between 0 and 100");
        }
        if (cooldownMillis < 50L) throw new IllegalArgumentException("cooldown must be at least 50ms");
        if (requiredLevel < 0) throw new IllegalArgumentException("required level cannot be negative");
        if (dailyAllowanceMillis != -1L
                && (dailyAllowanceMillis < 1_000L || dailyAllowanceMillis > 86_400_000L)) {
            throw new IllegalArgumentException("daily allowance must be between 1 second and 24 hours");
        }
    }

    public boolean hasDailyAllowance() {
        return dailyAllowanceMillis > 0L;
    }
}
