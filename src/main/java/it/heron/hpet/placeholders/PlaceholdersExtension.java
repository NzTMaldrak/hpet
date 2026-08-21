package it.heron.hpet.placeholders;

import it.heron.hpet.modules.abilities.AbilityType;
import it.heron.hpet.modules.abilities.PetAbilityRuntime;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import it.heron.hpet.main.PetPlugin;

import java.util.Locale;
import java.util.function.Supplier;

public class PlaceholdersExtension extends PlaceholderExpansion {

    private final ThreadLocal<PetType> requestedPetType = new ThreadLocal<>();

    public <T> T withPetContext(PetType petType, Supplier<T> action) {
        PetType previous = requestedPetType.get();
        requestedPetType.set(petType);
        try {
            return action.get();
        } finally {
            if (previous == null) requestedPetType.remove();
            else requestedPetType.set(previous);
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hpet";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Heron4gf";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getVersion() {
        return PetPlugin.getInstance().getDescription().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) return "";
        UserPet userPet;
        if(player.isOnline()) {
            userPet = PetPlugin.getApi().userPet(player.getPlayer());
        } else {
            return "Can't retrieve pet data of a offline player";
        }
        if(identifier.equalsIgnoreCase("isSelected")) {
            return (userPet != null)+"";
        }
        String normalized = identifier.toLowerCase(Locale.ROOT);
        if (normalized.equals("ability_remaining")) {
            return formatDuration(remainingMillis(player.getPlayer(), userPet, null));
        }
        if (normalized.equals("ability_remaining_seconds")) {
            return Long.toString(remainingSeconds(remainingMillis(player.getPlayer(), userPet, null)));
        }

        String formattedPrefix = "ability_remaining_";
        String secondsPrefix = "ability_remaining_seconds_";
        if (normalized.startsWith(secondsPrefix)) {
            AbilityType type = abilityType(identifier.substring(secondsPrefix.length()));
            return type == null ? "" : Long.toString(remainingSeconds(
                    remainingMillis(player.getPlayer(), userPet, type)));
        }
        if (normalized.startsWith(formattedPrefix)) {
            AbilityType type = abilityType(identifier.substring(formattedPrefix.length()));
            return type == null ? "" : formatDuration(
                    remainingMillis(player.getPlayer(), userPet, type));
        }

        if(userPet == null) return "";
        switch(normalized) {
            case "name":
                return userPet.getPetType().getName();
            case "displayname":
                return userPet.getPetType().getDisplayName().insertion();
            case "level":
                return userPet.getLevel()+"";
        }
        return "Invalid placeholder";
    }

    private long remainingMillis(org.bukkit.entity.Player player, UserPet activePet, AbilityType type) {
        PetType requested = requestedPetType.get();
        if (requested == null) {
            return activePet == null ? 0L : activePet.getAbilityRuntime().remainingEffectMillis(type);
        }
        UserPet requestedActivePet = PetPlugin.getApi().userPets(player).stream()
                .filter(candidate -> candidate.getPetType().getName().equalsIgnoreCase(requested.getName()))
                .findFirst()
                .orElse(null);
        if (requestedActivePet != null) return requestedActivePet.getAbilityRuntime().remainingEffectMillis(type);
        return PetAbilityRuntime.remainingDailyMillis(player.getUniqueId(), requested, type);
    }

    private AbilityType abilityType(String value) {
        try {
            return AbilityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private long remainingSeconds(long millis) {
        return millis <= 0L ? 0L : (millis + 999L) / 1_000L;
    }

    private String formatDuration(long millis) {
        long totalSeconds = remainingSeconds(millis);
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
