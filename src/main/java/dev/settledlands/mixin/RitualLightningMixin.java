package dev.settledlands.mixin;
import dev.settledlands.ritual.RitualManager;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LightningBolt.class)
public abstract class RitualLightningMixin {
    @Inject(method="spawnFire",at=@At("HEAD"),cancellable=true)
    private void settledlands$noBlockFire(int attempts,CallbackInfo ci) {
        if(((LightningBolt)(Object)this).getPersistentData().getBoolean(RitualManager.BOLT))ci.cancel();
    }
}
