package me.cortex.nvidium.persist;

import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Block edits already remesh in Sodium; persistBuildResult rewrites the store.
 * This tracks dirty events and drops stale GPU-only copies of neighbour sections
 * so lighting changes do not keep an old snapshot.
 */
public final class PersistDirty {
    private static final AtomicInteger EVENTS = new AtomicInteger();

    private PersistDirty() {}

    public static int events() {
        return EVENTS.get();
    }

    public static void onBlockChanged(BlockPos pos) {
        EVENTS.incrementAndGet();
        var renderer = currentRenderer();
        if (renderer == null) {
            return;
        }
        var store = renderer.getPersistentStore();
        if (store == null) {
            return;
        }
        int sx = SectionPos.blockToSectionCoord(pos.getX());
        int sy = SectionPos.blockToSectionCoord(pos.getY());
        int sz = SectionPos.blockToSectionCoord(pos.getZ());
        store.touchDirty(SectionPos.asLong(sx, sy, sz));
    }

    private static me.cortex.nvidium.NvidiumWorldRenderer currentRenderer() {
        try {
            SodiumWorldRenderer sodium = SodiumWorldRenderer.instance();
            if (sodium instanceof INvidiumWorldRendererGetter getter) {
                return getter.getRenderer();
            }
        } catch (Throwable t) {
            Nvidium.LOGGER.debug("Persist dirty skipped", t);
        }
        return null;
    }
}
