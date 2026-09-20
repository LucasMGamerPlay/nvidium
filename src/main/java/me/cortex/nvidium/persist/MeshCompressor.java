package me.cortex.nvidium.persist;

import me.cortex.nvidium.Nvidium;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Zstd when natives load; otherwise Deflate BEST_COMPRESSION.
 */
public final class MeshCompressor {
    private static final boolean ZSTD;

    static {
        boolean ok = false;
        try {
            Class.forName("com.github.luben.zstd.Zstd");
            com.github.luben.zstd.Zstd.compress(new byte[]{1, 2, 3, 4}, 1);
            ok = true;
            Nvidium.LOGGER.info("Persistent mesh compressor: zstd");
        } catch (Throwable t) {
            Nvidium.LOGGER.warn("zstd unavailable ({}), using deflate BEST_COMPRESSION", t.toString());
        }
        ZSTD = ok;
    }

    public static String codecName() {
        return ZSTD ? "zstd" : "deflate";
    }

    private MeshCompressor() {}

    public static byte[] compress(byte[] input) {
        if (input.length == 0) {
            return input;
        }
        if (ZSTD) {
            return com.github.luben.zstd.Zstd.compress(input, 3);
        }
        return deflate(input);
    }

    public static byte[] decompress(byte[] input, int rawLen) throws IOException {
        if (rawLen == 0) {
            return new byte[0];
        }
        if (ZSTD) {
            try {
                byte[] out = new byte[rawLen];
                long n = com.github.luben.zstd.Zstd.decompress(out, input);
                if (n != rawLen) {
                    throw new IOException("zstd size mismatch: " + n + " vs " + rawLen);
                }
                return out;
            } catch (Exception first) {
                try {
                    byte[] out = com.github.luben.zstd.Zstd.decompress(input, rawLen);
                    if (out.length != rawLen) {
                        throw new IOException("zstd size mismatch: " + out.length + " vs " + rawLen);
                    }
                    return out;
                } catch (Exception second) {
                    throw new IOException("zstd decompress failed", first);
                }
            }
        }
        return inflate(input, rawLen);
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(input);
        deflater.finish();
        byte[] buf = new byte[Math.max(1024, input.length / 4)];
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 3);
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            if (n > 0) {
                out.write(buf, 0, n);
            }
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] input, int rawLen) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(input);
        byte[] output = new byte[rawLen];
        try {
            int got = 0;
            while (got < rawLen && !inflater.finished()) {
                int n = inflater.inflate(output, got, rawLen - got);
                if (n == 0) {
                    break;
                }
                got += n;
            }
            if (got != rawLen) {
                throw new IOException("Inflated " + got + " bytes, expected " + rawLen);
            }
        } catch (DataFormatException e) {
            throw new IOException("Failed to inflate mesh", e);
        } finally {
            inflater.end();
        }
        return output;
    }
}
