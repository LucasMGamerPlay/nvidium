package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.persist.PersistDirty;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void nvidium$dirtyPersist(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (!Nvidium.IS_ENABLED || oldState == newState) {
            return;
        }
        PersistDirty.onBlockChanged(pos);
    }
}
