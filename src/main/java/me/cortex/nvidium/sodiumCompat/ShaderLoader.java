package me.cortex.nvidium.sodiumCompat;

import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.config.StatisticsLoggingLevel;
import me.cortex.nvidium.config.TranslucencySortingLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderLoader {
    public static String parse(Identifier path) {
        return parse(path, ShaderDefines.builder());
    }

    public static String parse(Identifier path, ShaderDefines.Builder builder) {
        if (Nvidium.IS_DEBUG) {
            builder.define("DEBUG");
        }

        for (int i = 1; i <= Nvidium.config.statistics_level.ordinal(); i++) {
            builder.define("STATISTICS_"+StatisticsLoggingLevel.values()[i].name());
        }


        if (Nvidium.config.translucency_sorting_level.ordinal() >= TranslucencySortingLevel.SECTIONS.ordinal()) {
            builder.define("TRANSLUCENCY_SORTING_SECTIONS");
        }
        if (Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.QUADS) {
            builder.define("TRANSLUCENCY_SORTING_QUADS");
        }
        if (Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.SODIUM) {
            builder.define("TRANSLUCENCY_SORTING_SODIUM");
        }

        if (Nvidium.config.render_fog) {
            builder.define("RENDER_FOG");
        }

        if (Nvidium.config.use_sodium_vertex_format) {
            builder.define("USE_SODIUM_VERTEX_FORMAT");
        }
        if (Nvidium.config.cull_degenerate_triangles) {
            builder.define("CULL_DEGENERATE_TRIANGLES");
        }
        if (Nvidium.config.use_nv_fragment_shader_barycentric) {
            builder.define("USE_NV_FRAGMENT_SHADER_BARYCENTRIC");
        }

        builder.define("TEXTURE_MAX_SCALE", String.valueOf(NvidiumCompactChunkVertex.TEXTURE_MAX_VALUE));

        String source = ShaderLoader.resolve(path);
        source = processMojImports(source);
        source = injectAfterVersion(source, builder.build().asSourceDirectives());

        return source;
    }

    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\b[^\\n]*\\n");
    private static final Pattern MOJ_IMPORT = Pattern.compile("^\\s*#moj_import\\s+(?:<([^>]+)>|\"([^\"]+)\")\\s*$", Pattern.MULTILINE);

    private static String injectAfterVersion(String source, String defines) {
        if (defines == null || defines.isBlank()) {
            return source;
        }
        Matcher matcher = VERSION.matcher(source);
        if (matcher.find()) {
            return source.substring(0, matcher.end()) + defines + source.substring(matcher.end());
        }
        return defines + source;
    }

    private static String processMojImports(String source) {
        Matcher matcher = MOJ_IMPORT.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String imported = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = processMojImports(ShaderLoader.resolve(Identifier.parse(imported)));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String resolve(Identifier id) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();

        Optional<Resource> res = rm.getResource(id.withPrefix("shaders/"));
        if (res.isEmpty()) {
            throw new IllegalStateException("Failed to find shader " + id.getPath());
        }

        try {
            Reader reader = res.get().openAsReader();
            String source = "#error shader didn't load";
            try {
                source = IOUtils.toString(reader);
            } catch (IOException e) {
                System.out.println("Nvidium shader reader error");
            }

            if (reader != null) {
                reader.close();
            }

            return source;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open resource reader, wtf is going on");
        }
    }
}
