package me.cortex.nvidium.mixin.minecraft;

import com.mojang.renderpearl.backend.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"com.mojang.renderpearl.backend.opengl.GlCommandEncoder"})
public interface GlCommandEncoderAccessor {
    @Accessor("lastProgram")
    void nvidium$setLastProgram(GlProgram program);
}
