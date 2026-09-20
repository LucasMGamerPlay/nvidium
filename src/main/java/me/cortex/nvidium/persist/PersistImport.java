package me.cortex.nvidium.persist;

import me.cortex.nvidium.Nvidium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Singleplayer import: generate (or load) chunks around the player so Sodium
 * meshes them and persist saves the meshes to disk.
 *
 * IntegratedServer resets view distance every tick to the video-settings
 * render distance, so this job teleports the player through the area instead
 * of trying to dump packets into a tiny client chunk cache.
 */
public final class PersistImport {
    private static final int EXISTING_PER_TICK = 2;
    private static final int WAIT_TICKS_AFTER = 40;
    private static final int WAIT_TICKS_TELEPORT = 12;
    private static final int IMPORT_RENDER_DISTANCE = 12;
    private static final int PROGRESS_EVERY = 64;

    private static volatile boolean running;
    private static volatile boolean scheduled;
    private static volatile String status = "idle";
    private static int total;
    private static int done;
    private static int skipped;
    private static int generated;
    private static int wait;
    private static int cooldown;
    private static int oldRenderDistance = -1;
    private static boolean bumpedRenderDistance;
    private static double homeX, homeY, homeZ;
    private static boolean teleported;
    private static boolean generateMissing;
    private static final Deque<ChunkPos> queue = new ArrayDeque<>();

    private PersistImport() {}

    public static String statusLine() {
        if (!running) {
            return status;
        }
        return "import " + done + "/" + total + " gen=" + generated + " skip=" + skipped + " q=" + queue.size();
    }

    public static boolean running() {
        return running;
    }

    public static int startRadius(int radius) {
        return begin(radius, true);
    }

    public static int startAllRegions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer()) {
            return -1;
        }
        if (running) {
            return -2;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        ServerLevel level = server.getLevel(mc.level.dimension());
        if (level == null) {
            return -1;
        }
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        var dim = level.dimension().identifier().getPath();
        Path regionDir;
        if ("the_nether".equals(dim)) {
            regionDir = root.resolve("DIM-1").resolve("region");
        } else if ("the_end".equals(dim)) {
            regionDir = root.resolve("DIM1").resolve("region");
        } else {
            regionDir = root.resolve("region");
        }
        queue.clear();
        if (Files.isDirectory(regionDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.*.mca")) {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    String[] p = name.substring(0, name.length() - 4).split("\\.");
                    if (p.length != 3) {
                        continue;
                    }
                    int rx = Integer.parseInt(p[1]);
                    int rz = Integer.parseInt(p[2]);
                    for (int lz = 0; lz < 32; lz++) {
                        for (int lx = 0; lx < 32; lx++) {
                            queue.add(new ChunkPos((rx << 5) + lx, (rz << 5) + lz));
                        }
                    }
                }
            } catch (Exception e) {
                Nvidium.LOGGER.error("Failed to scan region files", e);
                return -3;
            }
        }
        total = queue.size();
        if (total == 0) {
            status = "no regions";
            return 0;
        }
        generateMissing = false;
        arm(mc);
        Nvidium.LOGGER.info("Persist import all regions: {} chunk slots", total);
        return total;
    }

    private static int begin(int radius, boolean generate) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer()) {
            return -1;
        }
        if (running) {
            return -2;
        }
        int r = Math.max(8, Math.min(256, radius));
        int pcx = mc.player.chunkPosition().x();
        int pcz = mc.player.chunkPosition().z();
        queue.clear();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) > r) {
                    continue;
                }
                queue.add(new ChunkPos(pcx + dx, pcz + dz));
            }
        }
        total = queue.size();
        generateMissing = generate;
        arm(mc);
        Nvidium.LOGGER.info("Persist import started: {} chunks radius {} generate={}", total, r, generate);
        return total;
    }

    private static void arm(Minecraft mc) {
        done = 0;
        skipped = 0;
        generated = 0;
        wait = 0;
        cooldown = 0;
        homeX = mc.player.getX();
        homeY = mc.player.getY();
        homeZ = mc.player.getZ();
        teleported = false;
        bumpRenderDistance(mc);
        running = true;
        scheduled = false;
        status = "starting";
    }

    private static void bumpRenderDistance(Minecraft mc) {
        int current = mc.options.renderDistance().get();
        oldRenderDistance = current;
        bumpedRenderDistance = false;
        if (current < IMPORT_RENDER_DISTANCE) {
            mc.options.renderDistance().set(IMPORT_RENDER_DISTANCE);
            bumpedRenderDistance = true;
            Nvidium.LOGGER.info("Persist import: render distance {} -> {}", current, IMPORT_RENDER_DISTANCE);
        }
    }

    private static void restoreRenderDistance() {
        Minecraft mc = Minecraft.getInstance();
        if (!bumpedRenderDistance || oldRenderDistance < 0) {
            oldRenderDistance = -1;
            bumpedRenderDistance = false;
            return;
        }
        int restore = oldRenderDistance;
        oldRenderDistance = -1;
        bumpedRenderDistance = false;
        mc.execute(() -> {
            if (mc.options == null) {
                return;
            }
            int current = mc.options.renderDistance().get();
            if (current == IMPORT_RENDER_DISTANCE) {
                mc.options.renderDistance().set(restore);
            }
        });
    }

    public static void stop() {
        if (!running) {
            restoreRenderDistance();
            return;
        }
        running = false;
        queue.clear();
        restoreRenderDistance();
        status = "stopped " + done + "/" + total;
    }

    public static void tick() {
        if (!running || scheduled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer()) {
            stop();
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        scheduled = true;
        server.execute(() -> {
            try {
                serverTick();
            } finally {
                scheduled = false;
            }
        });
    }

    private static void serverTick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            running = false;
            restoreRenderDistance();
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
        ServerLevel level = player == null ? null : player.level();
        if (player == null || level == null) {
            running = false;
            restoreRenderDistance();
            return;
        }

        status = "loading";

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (queue.isEmpty()) {
            if (wait++ >= WAIT_TICKS_AFTER) {
                finish(player);
            }
            return;
        }

        int view = currentView(player);
        int stay = Math.max(2, view - 2);
        int n = 0;
        while (n < EXISTING_PER_TICK && !queue.isEmpty() && running) {
            ChunkPos pos = queue.pollFirst();
            if (chebyshev(player, pos) > stay) {
                queue.addFirst(pos);
                teleportNear(player, level, pos);
                cooldown = WAIT_TICKS_TELEPORT;
                return;
            }

            var now = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            boolean present = now instanceof LevelChunk;
            if (!present && !generateMissing) {
                skipped++;
                done++;
                n++;
                continue;
            }

            try {
                var access = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, true);
                if (!(access instanceof LevelChunk)) {
                    skipped++;
                } else if (!present) {
                    generated++;
                    n = EXISTING_PER_TICK;
                } else {
                    n++;
                }
            } catch (Exception e) {
                Nvidium.LOGGER.error("Failed to import chunk {} {}", pos.x(), pos.z(), e);
                skipped++;
                n++;
            }
            done++;
        }

        if (done > 0 && done % PROGRESS_EVERY == 0) {
            tell(statusLine());
        }
    }

    private static int currentView(ServerPlayer player) {
        int view = Math.max(2, player.requestedViewDistance());
        try {
            Integer rd = Minecraft.getInstance().options.renderDistance().get();
            if (rd != null) {
                view = Math.max(view, rd);
            }
        } catch (Exception ignored) {
        }
        return view;
    }

    private static int chebyshev(ServerPlayer player, ChunkPos pos) {
        ChunkPos here = player.chunkPosition();
        return Math.max(Math.abs(here.x() - pos.x()), Math.abs(here.z() - pos.z()));
    }

    private static void teleportNear(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        int tx = (pos.x() << 4) + 8;
        int tz = (pos.z() << 4) + 8;
        int hy;
        try {
            hy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) + 2;
        } catch (Exception e) {
            hy = (int) homeY;
        }
        double y = Math.max(homeY, hy);
        player.teleportTo(tx + 0.5, y, tz + 0.5);
        teleported = true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level != null) {
                mc.level.getChunkSource().updateViewCenter(pos.x(), pos.z());
            }
        });
    }

    private static void finish(ServerPlayer player) {
        if (teleported) {
            player.teleportTo(homeX, homeY, homeZ);
        }
        running = false;
        restoreRenderDistance();
        status = "done " + done + "/" + total + " gen=" + generated + " skip=" + skipped;
        Nvidium.LOGGER.info("Persist import finished: {}", status);
        tellChat("Nvidium persist import: " + status);
    }

    private static void tell(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal(msg));
            }
        });
    }

    private static void tellChat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(msg));
            }
        });
    }
}
