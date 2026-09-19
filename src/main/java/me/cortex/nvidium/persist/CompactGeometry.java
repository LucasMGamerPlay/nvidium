package me.cortex.nvidium.persist;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Packs Nvidium terrain quads from 64 bytes (4 vertices) down to ~46 when
 * colour/light/material are uniform, which is the Sodium terrain case.
 */
public final class CompactGeometry {
    private static final int VERTEX = 16;
    private static final int RAW_QUAD = 64;
    private static final byte FLAG_COMPACT = 1;

    private CompactGeometry() {}

    public static byte[] pack(byte[] geometry, int stride, int quads) {
        if (stride != VERTEX || quads <= 0 || geometry.length < quads * RAW_QUAD) {
            return geometry;
        }
        ByteBuffer src = ByteBuffer.wrap(geometry).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dst = ByteBuffer.allocate(1 + quads * RAW_QUAD).order(ByteOrder.LITTLE_ENDIAN);
        dst.put(FLAG_COMPACT);
        for (int q = 0; q < quads; q++) {
            int base = q * RAW_QUAD;
            int[] v0 = readVertex(src, base);
            int[] v1 = readVertex(src, base + VERTEX);
            int[] v2 = readVertex(src, base + VERTEX * 2);
            int[] v3 = readVertex(src, base + VERTEX * 3);
            if (uniformExtras(v0, v1) && uniformExtras(v0, v2) && uniformExtras(v0, v3)) {
                dst.put((byte) 1);
                putPos(dst, v0);
                putPos(dst, v1);
                putPos(dst, v2);
                putPos(dst, v3);
                dst.putInt(v0[3]);
                dst.putInt(v1[3]);
                dst.putInt(v2[3]);
                dst.putInt(v3[3]);
                dst.put((byte) ((v0[1] >>> 16) & 0xFF));
                dst.put((byte) ((v0[1] >>> 24) & 0xFF));
                dst.put((byte) ((v0[2] >>> 24) & 0xFF));
                dst.put((byte) v0[2]);
                dst.put((byte) (v0[2] >>> 8));
                dst.put((byte) (v0[2] >>> 16));
            } else {
                dst.put((byte) 0);
                dst.put(geometry, base, RAW_QUAD);
            }
        }
        byte[] packed = new byte[dst.position()];
        System.arraycopy(dst.array(), 0, packed, 0, packed.length);
        return packed;
    }

    public static byte[] unpack(byte[] packed, int stride, int quads) {
        if (packed.length == 0 || packed[0] != FLAG_COMPACT) {
            return packed;
        }
        ByteBuffer src = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
        src.position(1);
        byte[] geometry = new byte[quads * RAW_QUAD];
        ByteBuffer dst = ByteBuffer.wrap(geometry).order(ByteOrder.LITTLE_ENDIAN);
        for (int q = 0; q < quads; q++) {
            int kind = src.get() & 0xFF;
            if (kind == 0) {
                src.get(geometry, q * RAW_QUAD, RAW_QUAD);
                dst.position((q + 1) * RAW_QUAD);
                continue;
            }
            int[][] pos = {getPos(src), getPos(src), getPos(src), getPos(src)};
            int uv0 = src.getInt();
            int uv1 = src.getInt();
            int uv2 = src.getInt();
            int uv3 = src.getInt();
            int material = src.get() & 0xFF;
            int blockLight = src.get() & 0xFF;
            int skyLight = src.get() & 0xFF;
            int r = src.get() & 0xFF;
            int g = src.get() & 0xFF;
            int b = src.get() & 0xFF;
            int color = r | (g << 8) | (b << 16);
            int[] uvs = {uv0, uv1, uv2, uv3};
            for (int i = 0; i < 4; i++) {
                writeVertex(dst, pos[i][0], pos[i][1], pos[i][2], material, blockLight, skyLight, color, uvs[i]);
            }
        }
        return geometry;
    }

    private static int[] readVertex(ByteBuffer src, int offset) {
        return new int[]{
                src.getInt(offset),
                src.getInt(offset + 4),
                src.getInt(offset + 8),
                src.getInt(offset + 12)
        };
    }

    private static boolean uniformExtras(int[] a, int[] b) {
        return ((a[1] ^ b[1]) & 0xFFFF0000) == 0 && ((a[2] ^ b[2]) & 0xFFFFFFFF) == 0;
    }

    private static void putPos(ByteBuffer dst, int[] v) {
        dst.putShort((short) v[0]);
        dst.putShort((short) (v[0] >>> 16));
        dst.putShort((short) v[1]);
    }

    private static int[] getPos(ByteBuffer src) {
        int x = src.getShort() & 0xFFFF;
        int y = src.getShort() & 0xFFFF;
        int z = src.getShort() & 0xFFFF;
        return new int[]{x, y, z};
    }

    private static void writeVertex(ByteBuffer dst, int x, int y, int z, int material, int blockLight, int skyLight, int color, int uv) {
        dst.putInt((x & 0xFFFF) | ((y & 0xFFFF) << 16));
        dst.putInt((z & 0xFFFF) | ((material & 0xFF) << 16) | ((blockLight & 0xFF) << 24));
        dst.putInt((color & 0x00FFFFFF) | ((skyLight & 0xFF) << 24));
        dst.putInt(uv);
    }
}
