package it.heron.hpet.modules.pets.userpets.nametags;

import it.heron.hpet.main.PetPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import it.heron.hpet.modules.messages.ComponentsHelper;

public class NametagGenerator {

    public static INametag getFormattedNametag(String text) {
        return getFormattedNametag(text, 0, "");
    }

    public static INametag getFormattedNametag(String text, int level, String ownerName) {
        return getFormattedNametag(ComponentsHelper.legacyParse(text), level, ownerName);
    }

    public static INametag getFormattedNametag(Component name, int level, String ownerName) {
        Component safeName = name == null ? Component.empty() : name;
        INametag nametag = getNametag(safeName);
        changeNametagFormatted(nametag, safeName, level, ownerName);
        return nametag;
    }

    public static void changeNametagFormatted(INametag nametag, String text) {
        changeNametagFormatted(nametag, text, 0, "");
    }

    public static void changeNametagFormatted(INametag nametag, String text, int level, String ownerName) {
        changeNametagFormatted(nametag, ComponentsHelper.legacyParse(text), level, ownerName);
    }

    public static void changeNametagFormatted(INametag nametag, Component name, int level, String ownerName) {
        Component safeName = name == null ? Component.empty() : name;
        Component player = Component.text(ownerName == null ? "" : ownerName);
        Component levelText = Component.text(String.valueOf(level));
        String format = PetPlugin.getInstance().getConfig().getString("nametags.format", "{name} {level}");
        format = format == null ? "{name} {level}" : format.strip();
        Component formatted = ComponentsHelper.simpleParse(format);
        formatted = replace(formatted, "{name}", safeName);
        formatted = replace(formatted, "%name%", safeName);
        formatted = replace(formatted, "{level}", levelText);
        formatted = replace(formatted, "%level%", levelText);
        formatted = replace(formatted, "{player}", player);
        formatted = replace(formatted, "%player%", player);
        nametag.setName(formatted);
    }

    private static Component replace(Component input, String token, Component replacement) {
        return input.replaceText(TextReplacementConfig.builder()
                .matchLiteral(token)
                .replacement(replacement)
                .build());
    }

    private static INametag getNametag(Component text) {
        if(!PetPlugin.getInstance().getConfig().getBoolean("nametags.enable", true)) {
            return new NoNametag();
        }
        return new ArmorstandNametag(text);

    }


}
