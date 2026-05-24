package com.heartvault.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

public final class Text {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final FileConfiguration messages;

    public Text(FileConfiguration messages) {
        this.messages = messages;
    }

    public Component raw(String miniMsg) {
        return miniMessage.deserialize(miniMsg == null ? "" : miniMsg);
    }

    public Component msg(String path, TagResolver... resolvers) {
        String prefix = messages.getString("prefix", "");
        String line = messages.getString(path, "");
        return miniMessage.deserialize(prefix + line, TagResolver.resolver(resolvers));
    }

    public Component msgNoPrefix(String path, TagResolver... resolvers) {
        String line = messages.getString(path, "");
        return miniMessage.deserialize(line, TagResolver.resolver(resolvers));
    }

    public static TagResolver p(String key, String value) {
        return Placeholder.parsed(key, value == null ? "" : value);
    }
}
