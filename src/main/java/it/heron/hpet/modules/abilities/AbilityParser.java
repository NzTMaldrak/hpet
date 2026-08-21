package it.heron.hpet.modules.abilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AbilityParser {

    private static final long DEFAULT_COOLDOWN_MILLIS = 10_000L;
    private static final Pattern CHANCE = Pattern.compile("^(\\d+(?:\\.\\d+)?)%$");
    private static final Pattern LEVEL = Pattern.compile("^(\\d+)l$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile(
            "^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$", Pattern.CASE_INSENSITIVE);

    private AbilityParser() {
    }

    public static AbilityDefinition parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("ability is blank");

        List<String> parts = new ArrayList<>(Arrays.asList(input.strip().split(":", -1)));
        AbilityType type;
        try {
            String typeName = parts.removeFirst().strip().toUpperCase(Locale.ROOT);
            if (typeName.equals("LIGHTNING_ON_PLAYER")) typeName = "LIGHNING_ON_PLAYER";
            type = AbilityType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown ability type", exception);
        }

        double chance = 100d;
        long cooldown = DEFAULT_COOLDOWN_MILLIS;
        int requiredLevel = 0;
        AbilityTrigger trigger = AbilityTrigger.TIME;
        boolean skipFirstRun = false;
        long dailyAllowance = -1L;

        boolean changed;
        do {
            changed = false;
            if (parts.isEmpty()) break;
            String candidate = parts.getLast().strip();
            if (candidate.regionMatches(true, 0, "daily=", 0, "daily=".length())) {
                Long parsedDaily = parseDuration(candidate.substring("daily=".length()));
                if (parsedDaily == null) {
                    throw new IllegalArgumentException("daily must use a duration such as 2h or 1h30m");
                }
                if (parsedDaily > 86_400_000L) {
                    throw new IllegalArgumentException("daily cannot exceed 24 hours");
                }
                dailyAllowance = parsedDaily;
                parts.removeLast();
                changed = true;
                continue;
            }
            Matcher matcher = CHANCE.matcher(candidate);
            if (matcher.matches()) {
                chance = Double.parseDouble(matcher.group(1));
                parts.removeLast();
                changed = true;
                continue;
            }
            matcher = LEVEL.matcher(candidate);
            if (matcher.matches()) {
                requiredLevel = Integer.parseInt(matcher.group(1));
                parts.removeLast();
                changed = true;
                continue;
            }
            Long parsedDuration = parseDuration(candidate);
            if (parsedDuration != null) {
                cooldown = parsedDuration;
                parts.removeLast();
                changed = true;
                continue;
            }
            AbilityTrigger parsedTrigger = parseTrigger(candidate);
            if (parsedTrigger != null) {
                trigger = parsedTrigger;
                parts.removeLast();
                changed = true;
                continue;
            }
            if (candidate.regionMatches(true, 0, "skipFirstRun=", 0, "skipFirstRun=".length())) {
                String value = candidate.substring("skipFirstRun=".length());
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("skipFirstRun must be true or false");
                }
                skipFirstRun = Boolean.parseBoolean(value);
                parts.removeLast();
                changed = true;
            }
        } while (changed);

        List<String> arguments = normalizeArguments(type, parts);
        validateArguments(type, arguments);
        return new AbilityDefinition(
                type, arguments, chance, cooldown, requiredLevel, trigger, skipFirstRun, dailyAllowance);
    }

    private static List<String> normalizeArguments(AbilityType type, List<String> parts) {
        List<String> stripped = parts.stream().map(String::strip).toList();
        return switch (type) {
            case TITLE, SUBTITLE, MESSAGE, CONSOLE_LOG, PLAYER_COMMAND, CONSOLE_COMMAND ->
                    stripped.isEmpty() ? List.of() : List.of(String.join(":", stripped));
            default -> stripped;
        };
    }

    private static void validateArguments(AbilityType type, List<String> arguments) {
        int minimum;
        int maximum;
        switch (type) {
            case ADD_HEALTH, ADD_FOOD, DAMAGE, EXP, SET_FIRE, TEMP_FLY, TEMP_GOD, FREEZE,
                    PLAYER_PARTICLE, PET_PARTICLE, VELOCITY, FAKE_HAND, TITLE, SUBTITLE, MESSAGE,
                    CONSOLE_LOG, PLAYER_COMMAND, CONSOLE_COMMAND, CURE -> {
                minimum = 1;
                maximum = type == AbilityType.PLAYER_PARTICLE || type == AbilityType.PET_PARTICLE ? 2 : 1;
            }
            case POTION, PLAY_SOUND, PLAY_SOUND_EVERYONE, EXPLOSION -> {
                minimum = 3;
                maximum = 3;
            }
            case FAKE_ARMOR -> {
                minimum = 2;
                maximum = 3;
            }
            case HEAL, LAUNCH, PUMPKIN -> {
                minimum = 0;
                maximum = 1;
            }
            default -> {
                minimum = 0;
                maximum = 0;
            }
        }
        if (arguments.size() < minimum || arguments.size() > maximum) {
            String expected = minimum == maximum ? Integer.toString(minimum) : minimum + "-" + maximum;
            throw new IllegalArgumentException(type + " expects " + expected + " argument(s), got " + arguments.size());
        }
        if (arguments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(type + " contains an empty argument");
        }
        switch (type) {
            case ADD_HEALTH, DAMAGE -> requireNumber(type, arguments, 0);
            case VELOCITY -> {
                requireNumber(type, arguments, 0);
                if (Double.parseDouble(arguments.getFirst()) <= 0d) {
                    throw new IllegalArgumentException("VELOCITY power must be greater than zero");
                }
            }
            case ADD_FOOD, EXP, SET_FIRE, TEMP_FLY, TEMP_GOD, FREEZE -> requireInteger(type, arguments, 0);
            case PLAYER_PARTICLE, PET_PARTICLE -> requireInteger(type, arguments, 1);
            case HEAL, LAUNCH, PUMPKIN -> {
                if (!arguments.isEmpty()) requireNumber(type, arguments, 0);
            }
            case POTION -> {
                requireInteger(type, arguments, 1);
                requireInteger(type, arguments, 2);
            }
            case PLAY_SOUND, PLAY_SOUND_EVERYONE -> {
                requireNumber(type, arguments, 1);
                requireNumber(type, arguments, 2);
            }
            case EXPLOSION -> {
                requireNumber(type, arguments, 0);
                requireBoolean(type, arguments, 1);
                requireBoolean(type, arguments, 2);
            }
            case FAKE_ARMOR -> {
                if (arguments.size() == 3) requireInteger(type, arguments, 2);
            }
            default -> {
            }
        }
    }

    private static void requireInteger(AbilityType type, List<String> arguments, int index) {
        try {
            Integer.parseInt(arguments.get(index));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(type + " argument " + (index + 1) + " must be an integer");
        }
    }

    private static void requireNumber(AbilityType type, List<String> arguments, int index) {
        try {
            double value = Double.parseDouble(arguments.get(index));
            if (!Double.isFinite(value)) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(type + " argument " + (index + 1) + " must be a number");
        }
    }

    private static void requireBoolean(AbilityType type, List<String> arguments, int index) {
        String value = arguments.get(index);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(type + " argument " + (index + 1) + " must be true or false");
        }
    }

    private static AbilityTrigger parseTrigger(String candidate) {
        String normalized = candidate.toUpperCase(Locale.ROOT);
        try {
            return AbilityTrigger.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            if (!normalized.endsWith("E")) return null;
            try {
                return AbilityTrigger.valueOf(normalized.substring(0, normalized.length() - 1));
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    private static Long parseDuration(String candidate) {
        Matcher matcher = DURATION.matcher(candidate);
        if (!matcher.matches() || (matcher.group(1) == null
                && matcher.group(2) == null && matcher.group(3) == null)) return null;
        try {
            long hours = matcher.group(1) == null ? 0L : Long.parseLong(matcher.group(1));
            long minutes = matcher.group(2) == null ? 0L : Long.parseLong(matcher.group(2));
            long seconds = matcher.group(3) == null ? 0L : Long.parseLong(matcher.group(3));
            long totalSeconds = Math.addExact(
                    Math.addExact(Math.multiplyExact(hours, 3_600L), Math.multiplyExact(minutes, 60L)),
                    seconds);
            if (totalSeconds <= 0L) throw new IllegalArgumentException("duration must be greater than zero");
            return Math.multiplyExact(totalSeconds, 1_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("duration is too large", exception);
        }
    }
}
