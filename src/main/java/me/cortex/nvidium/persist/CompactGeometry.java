package me.cortex.nvidium.persist;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Prefixes geometry with a codec byte so unpack never mistakes raw verts for
 * compact quads. Compact form is used only when all 4 verts share extras.
 */
public final class CompactGeometry {
    private static final int VERTEX = 16;
    private static final int RAW_QUAD = 64;
    public static final byte CODEC_RAW = (byte) 0xC0;
    public static final byte CODEC_COMPACT = (byte) 0xC1;

    private CompactGeometry() {}

    public static byte[] pack(byte[] geometry, int stride, int quads) {
        if (geometry == null || geometry.length == 0) {
            return new byte[]{CODEC_RAW};
        }
        if (stride != VERTEX || quads <= 0 || geometry.length < quads * RAW_QUAD) {
            return prefix(CODEC_RAW, geometry);
        }
        try {
            ByteBuffer src = ByteBuffer.wrap(geometry).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer dst = ByteBuffer.allocate(2 + quads * (1 + RAW_QUAD)).order(ByteOrder.LITTLE_ENDIAN);
            dst.put(CODEC_COMPACT);
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
            byte[] roundtrip = unpack(packed, stride, quads);
            if (!java.util.Arrays.equals(roundtrip, java.util.Arrays.copyOf(geometry, quads * RAW_QUAD))) {
                return prefix(CODEC_RAW, geometry);
            }
            return packed;
        } catch (Throwable ignored) {
            return prefix(CODEC_RAW, geometry);
        }
    }

    public static byte[] unpack(byte[] packed, int stride, int quads) {
        if (packed == null || packed.length == 0) {
            return new byte[0];
        }
        byte codec = packed[0];
        if (codec == CODEC_RAW) {
            byte[] geometry = new byte[packed.length - 1];
            System.arraycopy(packed, 1, geometry, 0, geometry.length);
            return geometry;
        }
        if (codec != CODEC_COMPACT || quads <= 0) {
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
            int[] p0 = getPos(src);
            int[] p1 = getPos(src);
            int[] p2 = getPos(src);
            int[] p3 = getPos(src);
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
            writeVertex(dst, p0[0], p0[1], p0[2], material, blockLight, skyLight, color, uv0);
            writeVertex(dst, p1[0], p1[1], p1[2], material, blockLight, skyLight, color, uv1);
            writeVertex(dst, p2[0], p2[1], p2[2], material, blockLight, skyLight, color, uv2);
            writeVertex(dst, p3[0], p3[1], p3[2], material, blockLight, skyLight, color, uv3);
        }
        return geometry;
    }

    private static byte[] prefix(byte codec, byte[] geometry) {
        byte[] out = new byte[1 + geometry.length];
        out[0] = codec;
        System.arraycopy(geometry, 0, out, 1, geometry.length);
        return out;
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
        return ((a[1] ^ b[1]) & 0xFFFF0000) == 0 && a[2] == b[2];
    }

    private static void putPos(ByteBuffer dst, int[] v) {
        dst.putShort((short) v[0]);
        dst.putShort((short) (v[0] >>> 16));
        dst.putShort((short) v[1]);
    }

    private static int[] getPos(ByteBuffer src) {
        return new int[]{src.getShort() & 0xFFFF, src.getShort() & 0xFFFF, src.getShort() & 0xFFFF};
    }

    private static void writeVertex(ByteBuffer dst, int x, int y, int z, int material, int blockLight, int skyLight, int color, int uv) {
        dst.putInt((x & 0xFFFF) | ((y & 0xFFFF) << 16));
        dst.putInt((z & 0xFFFF) | ((material & 0xFF) << 16) | ((blockLight & 0xFF) << 24));
        dst.putInt((color & 0x00FFFFFF) | ((skyLight & 0xFF) << 24));
        dst.putInt(uv);
    }
}
