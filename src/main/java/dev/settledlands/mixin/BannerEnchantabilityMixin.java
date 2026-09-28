package dev.settledlands.mixin;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Modify only banners, leave all other vanilla/modded items unchanged. */
@Mixin(Item.class)
public abstract class BannerEnchantabilityMixin {
    @Inject(method="isEnchantable",at=@At("HEAD"),cancellable=true)
    private void settledlands$enchantable(ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof BannerItem)cir.setReturnValue(stack.getCount()==1);
    }
    @Inject(method="getEnchantmentValue()I",at=@At("HEAD"),cancellable=true)
    private void settledlands$value(CallbackInfoReturnable<Integer> cir) {
        if((Object)this instanceof BannerItem)cir.setReturnValue(10);
    }
}
