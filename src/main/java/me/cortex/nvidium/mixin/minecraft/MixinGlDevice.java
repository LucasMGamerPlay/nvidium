package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice")
public class MixinGlDevice {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;", shift = At.Shift.AFTER), remap = false)
    private void init(CallbackInfo ci) {
        Nvidium.checkSystemIsCapable();
    }
}
