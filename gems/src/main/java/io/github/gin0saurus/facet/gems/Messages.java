package io.github.gin0saurus.facet.gems;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.github.gin0saurus.facet.engine.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player-facing text from lang/en.yml. Missing keys fall back to the copy inside the jar, so
 * an old lang file never shows blank messages after an update.
 */
public final class Messages {

    private YamlConfiguration lang = new YamlConfiguration();
    private String prefix = "";

    public void load(JavaPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "lang/en.yml");
        if (!f.exists()) plugin.saveResource("lang/en.yml", false);
        lang = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("lang/en.yml");
        if (in != null) lang.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        prefix = lang.getString("prefix", "");
    }

    public String raw(String key) { return lang.getString(key, key); }

    public String get(String key, String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Text.fill(raw(key), m);
    }

    public void send(CommandSender to, String key, String... kv) {
        String s = get(key, kv);
        if (s.isBlank()) return;
        to.sendMessage(Text.parse(prefix + s));
    }
}
