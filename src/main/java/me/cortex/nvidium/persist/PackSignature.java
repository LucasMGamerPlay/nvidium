package me.cortex.nvidium.persist;

import me.cortex.nvidium.mixin.minecraft.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Fingerprint of the selected resource packs and block atlas size.
 * Persisted meshes store atlas UVs, so a pack/atlas change must drop the cache.
 */
public final class PackSignature {
    private PackSignature() {}

    public static String compute() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder(256);
        try {
            for (String id : mc.getResourcePackRepository().getSelectedIds()) {
                sb.append(id).append('\n');
            }
        } catch (Exception e) {
            sb.append("packs?\n");
        }
        sb.append("atlas=");
        try {
            Object tex = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            if (tex instanceof TextureAtlasAccessor atlas) {
                sb.append(atlas.nvidium$getWidth()).append('x').append(atlas.nvidium$getHeight());
            } else {
                sb.append(tex == null ? "none" : tex.getClass().getName());
            }
        } catch (Exception e) {
            sb.append("err");
        }
        return sha1(sb.toString());
    }

    private static String sha1(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8 && i < digest.length; i++) {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
