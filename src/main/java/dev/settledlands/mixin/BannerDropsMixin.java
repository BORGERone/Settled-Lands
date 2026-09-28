package dev.settledlands.mixin;
import java.util.List;
import dev.settledlands.Sanctity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** The shared loot path covers explosions too; BlockDropsEvent alone does not. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BannerDropsMixin {
    @Inject(method="getDrops",at=@At("RETURN"),cancellable=true)
    private void settledlands$retainSanctity(LootParams.Builder context,CallbackInfoReturnable<List<ItemStack>> cir) {
        if(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BannerBlockEntity banner) {
            if(dev.settledlands.ritual.RitualWard.noDrop(banner,context.getLevel().getGameTime())) { cir.setReturnValue(List.of()); return; }
            for(ItemStack stack:cir.getReturnValue())Sanctity.copyEnchantments(banner,stack);
        }
    }
}
