package dev.settledlands.mixin;
import dev.settledlands.ritual.RitualManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class RitualEntityMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void settledlands$freeze(MoverType type,Vec3 delta,CallbackInfo ci) {
        Entity e=(Entity)(Object)this;
        if(e.level() instanceof ServerLevel&&RitualManager.active(e)) {e.setDeltaMovement(Vec3.ZERO);ci.cancel();}
    }
    @Inject(method="thunderHit",at=@At("HEAD"),cancellable=true)
    private void settledlands$protectReplacement(ServerLevel level,LightningBolt bolt,CallbackInfo ci) {
        if(bolt.getPersistentData().getBoolean(RitualManager.BOLT)&&bolt.getPersistentData().hasUUID(RitualManager.PROTECTED)
            &&bolt.getPersistentData().getUUID(RitualManager.PROTECTED).equals(((Entity)(Object)this).getUUID()))ci.cancel();
    }
}
