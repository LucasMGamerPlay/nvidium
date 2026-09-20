package me.cortex.nvidium.persist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;

public final class PersistentWorldPaths {
    private PersistentWorldPaths() {}

    public static Path resolve(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        String dimension = dimensionFolder(level);
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            Path worldRoot = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return worldRoot.resolve("nvidium-persistent").resolve(dimension);
        }
        return mc.gameDirectory.toPath()
                .resolve("nvidium-persistent")
                .resolve("mp")
                .resolve(serverFolder(mc))
                .resolve(dimension);
    }

    public static String describe(ClientLevel level) {
        if (level == null) {
            return "none";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return "sp/" + dimensionFolder(level);
        }
        return "mp/" + serverFolder(mc) + "/" + dimensionFolder(level);
    }

    private static String serverFolder(Minecraft mc) {
        ServerData data = mc.getCurrentServer();
        if (data == null) {
            return "unknown";
        }
        String ip = data.ip == null || data.ip.isBlank() ? "unknown" : data.ip.trim();
        String name = data.name == null ? "" : data.name.trim();
        String key = ip;
        if (!name.isEmpty() && !name.equalsIgnoreCase(ip)) {
            key = name + "_" + ip;
        }
        if (data.isLan()) {
            key = "lan_" + key;
        }
        return sanitize(key);
    }

    private static String dimensionFolder(ClientLevel level) {
        Identifier id = level.dimension().identifier();
        return sanitize(id.getNamespace() + "_" + id.getPath());
    }

    private static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        String cleaned = out.toString().toLowerCase(Locale.ROOT);
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        return cleaned.isEmpty() ? "world" : cleaned;
    }
}
