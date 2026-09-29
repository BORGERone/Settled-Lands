package dev.settledlands;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Banner tooltip lines describing each Sanctity level. The event only fires on the client. */
public final class SanctityTooltip {
    /** Translation key for the level description; null when the level has none. */
    public static String descKey(int level) {
        return switch(level) {
            case 1 -> "enchantment.settledlands.sanctity.desc.1";
            case 2 -> "enchantment.settledlands.sanctity.desc.2";
            default -> null;
        };
    }
    @SubscribeEvent public void tooltip(ItemTooltipEvent event) {
        ItemStack stack=event.getItemStack();
        if(!(stack.getItem() instanceof BannerItem))return;
        ItemEnchantments enchantments=stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(var entry:enchantments.entrySet()) {
            if(!entry.getKey().is(Sanctity.KEY))continue;
            String key=descKey(entry.getIntValue());
            if(key!=null)event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
