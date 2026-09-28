package dev.settledlands.ritual;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
public final class RitualWard {
    public static final String DROP="settledlands_no_drop_until",IMMUNE="settledlands_ritual_immune_until";
    public static void mark(BannerBlockEntity banner,long now) {
        var tag=banner.getPersistentData();
        tag.putLong(DROP,Math.max(tag.getLong(DROP),now+RitualRules.NO_DROP));
        tag.putLong(IMMUNE,Math.max(tag.getLong(IMMUNE),now+RitualRules.IMMUNE));banner.setChanged();
    }
    public static boolean noDrop(BannerBlockEntity b,long now) {return RitualRules.locked(now,b.getPersistentData().getLong(DROP));}
    public static boolean immune(BannerBlockEntity b,long now) {return RitualRules.locked(now,b.getPersistentData().getLong(IMMUNE));}
    private RitualWard() {}
}
