package me.cortex.nvidium.sodiumCompat;

import me.cortex.nvidium.Nvidium;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Far-LOD mods we do not copy. When one is loaded, this fork yields fog and GPU keep
 * so that mod can draw the horizon. Persist still saves full Sodium meshes.
 *
 * Distant Horizons is LGPL. Voxy is All-Rights-Reserved — detect only, never copy.
 */
public final class FarLodMods {
    public static final boolean DISTANT_HORIZONS = FabricLoader.getInstance().isModLoaded("distanthorizons");
    public static final boolean VOXY = FabricLoader.getInstance().isModLoaded("voxy");
    public static final boolean LOADED = DISTANT_HORIZONS || VOXY;

    static {
        if (LOADED) {
            Nvidium.LOGGER.info("{} detected: far fog/keep capped so the LOD mod owns the horizon. Persist still saves full meshes.",
                    label());
        }
    }

    private FarLodMods() {}

    public static String label() {
        if (DISTANT_HORIZONS && VOXY) {
            return "DH+Voxy";
        }
        if (DISTANT_HORIZONS) {
            return "DH";
        }
        if (VOXY) {
            return "Voxy";
        }
        return "none";
    }
}
