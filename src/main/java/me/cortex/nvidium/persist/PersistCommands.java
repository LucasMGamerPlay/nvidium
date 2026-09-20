package me.cortex.nvidium.persist;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PersistCommands {
    private PersistCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(root()));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root() {
        return ClientCommands.literal("nvidium")
                .then(ClientCommands.literal("persist")
                        .then(ClientCommands.literal("import")
                                .executes(ctx -> importRadius(ctx.getSource(), 64))
                                .then(ClientCommands.argument("radius", IntegerArgumentType.integer(8, 256))
                                        .executes(ctx -> importRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))
                                .then(ClientCommands.literal("all")
                                        .executes(ctx -> importAll(ctx.getSource()))))
                        .then(ClientCommands.literal("stop")
                                .executes(ctx -> {
                                    PersistImport.stop();
                                    ctx.getSource().sendFeedback(Component.literal("Nvidium persist import stopped"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("status")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(Component.literal("Nvidium persist: " + PersistImport.statusLine()));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("wipe")
                                .executes(ctx -> wipe(ctx.getSource()))));
    }

    private static int importRadius(FabricClientCommandSource source, int radius) {
        int n = PersistImport.startRadius(radius);
        if (n == -1) {
            source.sendError(Component.literal("Import only works in singleplayer. On a server, walk around — meshes still persist."));
            return 0;
        }
        if (n == -2) {
            source.sendError(Component.literal("Import already running. /nvidium persist stop"));
            return 0;
        }
        source.sendFeedback(Component.literal(
                "Generating and meshing up to " + n + " chunks (radius " + radius
                        + "). May teleport and bump render distance. /nvidium persist stop to cancel."));
        return 1;
    }

    private static int importAll(FabricClientCommandSource source) {
        int n = PersistImport.startAllRegions();
        if (n == -1) {
            source.sendError(Component.literal("Import only works in singleplayer. On a server, walk around — meshes still persist."));
            return 0;
        }
        if (n == -2) {
            source.sendError(Component.literal("Import already running"));
            return 0;
        }
        if (n == 0) {
            source.sendError(Component.literal("No region files found"));
            return 0;
        }
        source.sendFeedback(Component.literal(
                "Loading existing region files (" + n + " slots). May teleport. /nvidium persist stop to cancel."));
        return 1;
    }

    private static int wipe(FabricClientCommandSource source) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            source.sendError(Component.literal("No world loaded"));
            return 0;
        }
        Path root = PersistentWorldPaths.resolve(mc.level);
        if (!mc.hasSingleplayerServer() && root.getParent() != null) {
            root = root.getParent();
        }
        try {
            if (Files.exists(root)) {
                try (var walk = Files.walk(root)) {
                    walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
            source.sendFeedback(Component.literal("Deleted " + root + " — leave and rejoin the world"));
            return 1;
        } catch (Exception e) {
            source.sendError(Component.literal("Wipe failed: " + e.getMessage()));
            return 0;
        }
    }
}
