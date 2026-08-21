package it.heron.hpet.modules.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.util.LinkedList;
import java.util.List;

public class ComponentsHelper {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static Component simpleParse(String text) {
        if(text == null) {
            text = "null";
            Bukkit.getLogger().severe("Hyper Pets just prevented a NullPointerException, please make sure you included all required values into your configurations");
        }
        if (text.indexOf('&') >= 0) {
            return LEGACY.deserialize(text);
        }
        return MiniMessage.miniMessage().deserialize(text);
    }

    public static Component legacyParse(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static List<Component> listParse(List<String> text) {
        if(text == null || text.isEmpty()) {
            text = List.of("null");
            Bukkit.getLogger().severe("Hyper Pets just prevented a NullPointerException, please make sure you included all required values into your configurations");
        }
        List<Component> list = new LinkedList<>();
        for(String string : text) {
            list.add(simpleParse(string));
        }
        return list;
    }

}
