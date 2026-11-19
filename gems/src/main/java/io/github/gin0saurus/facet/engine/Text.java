package io.github.gin0saurus.facet.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** MiniMessage helpers. Placeholders are {name}; values are escaped unless marked raw. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    /** Parses MiniMessage. Item names and lore get italics switched off, like vanilla custom items. */
    public static Component parse(String mini) {
        try {
            return MM.deserialize(mini == null ? "" : mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        } catch (RuntimeException e) {
            // A broken tag in a theme must never break an item: fall back to plain text.
            return Component.text(MM.stripTags(mini == null ? "" : mini));
        }
    }

    /** Replaces {key} placeholders. Values from players (names) are escaped so they cannot inject tags. */
    public static String fill(String template, Map<String, String> values) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public static String escape(String userText) { return MM.escapeTags(userText == null ? "" : userText); }

    public static String plain(String mini) { return MM.stripTags(mini == null ? "" : mini); }

    public static List<Component> lines(List<String> mini) {
        List<Component> out = new ArrayList<>();
        for (String s : mini) out.add(parse(s));
        return out;
    }

    /** Capitalises an id for fallbacks: "hot_sauce" -> "Hot Sauce". */
    public static String title(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("[_\\- ]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
