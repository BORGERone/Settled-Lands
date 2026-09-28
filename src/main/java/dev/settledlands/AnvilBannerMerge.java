package dev.settledlands;

import net.minecraft.core.Holder;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Banner enchanting on the anvil, with explicit prices.
 * <p>Vanilla refuses both cases below: its anvil path only accepts two identical <b>damageable</b>
 * items, or an enchanted book as the addition. Banners are not damageable, and no Sanctity book can
 * exist because the enchantment is limited to banners. So this handler owns the two banner actions:
 * <ul>
 *   <li>a plain banner plus an enchanted banner of the same kind receives the enchantment
 *       — Sanctity I for {@code sanctityLevelOneCost} levels, Sanctity II for {@code sanctityLevelTwoCost};</li>
 *   <li>two enchanted banners of the same kind merge by the vanilla rule (equal levels climb by one,
 *       different levels keep the higher one) — the same, now level specific, price.</li>
 * </ul>
 * Everything else stays vanilla: plain with plain, or a banner with an unrelated item, produces nothing.
 */
public final class AnvilBannerMerge {
    @SubscribeEvent public void update(AnvilUpdateEvent event) {
        ItemStack left=event.getLeft(),right=event.getRight();
        if(!(left.getItem() instanceof BannerItem)||left.getItem()!=right.getItem())return;
        if(left.getCount()!=1||right.getCount()!=1)return;
        ItemEnchantments received=EnchantmentHelper.getEnchantmentsForCrafting(right);
        if(received.isEmpty())return;
        ItemEnchantments.Mutable merged=new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(left));
        boolean changed=false;
        for(var entry:received.entrySet()) {
            Holder<Enchantment> enchantment=entry.getKey();
            int given=entry.getIntValue(),current=merged.getLevel(enchantment);
            // An empty banner simply receives what the sacrifice carries; two enchanted banners merge.
            int level=current==0?given
                :current==given?Math.min(current+1,enchantment.value().getMaxLevel())
                :Math.max(current,given);
            if(level>current) {merged.set(enchantment,level);changed=true;}
        }
        if(!changed)return;
        ItemEnchantments result=merged.toImmutable();
        int cost=0;
        for(var entry:result.entrySet())cost+=price(entry.getKey(),entry.getIntValue());
        ItemStack output=left.copy();
        EnchantmentHelper.setEnchantments(output,result);
        event.setOutput(output);
        event.setCost(Math.max(1,cost));
        event.setMaterialCost(0);
    }
    /** Sanctity has a fixed price per level; anything else a banner carries keeps vanilla pricing. */
    private static int price(Holder<Enchantment> enchantment,int level) {
        if(enchantment.is(Sanctity.KEY))return level>=2?Settings.COST_LEVEL_II.get():Settings.COST_LEVEL_I.get();
        return 1+enchantment.value().getAnvilCost()*level;
    }
}
