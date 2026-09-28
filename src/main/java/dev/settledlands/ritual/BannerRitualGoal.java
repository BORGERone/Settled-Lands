package dev.settledlands.ritual;
import java.util.EnumSet;
import dev.settledlands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.EventHooks;
public final class BannerRitualGoal extends Goal {
    private final Zombie zombie;
    private int nextSearch;
    private BlockPos target;
    public BannerRitualGoal(Zombie zombie) {
        this.zombie=zombie;nextSearch=zombie.tickCount+20+zombie.getRandom().nextInt(60);
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));
    }
    @Override public boolean canUse() {
        if(zombie.tickCount<nextSearch)return false;
        nextSearch=zombie.tickCount+60+zombie.getRandom().nextInt(20);
        if(!Settings.RITUALS.get()||RitualManager.busy()||zombie.isNoAi()||zombie.isPassenger()||!zombie.onGround()||!zombie.isAlive())return false;
        if(!(zombie.level() instanceof ServerLevel level)||!EventHooks.canEntityGrief(level,zombie))return false;
        target=Sanctity.ritualTarget(level,zombie.blockPosition());return target!=null;
    }
    @Override public void start() {RitualManager.begin(zombie,target);}
    @Override public boolean canContinueToUse() {return RitualManager.active(zombie);}
    @Override public void stop() {if(RitualManager.active(zombie))RitualManager.abort();nextSearch=zombie.tickCount+80;}
}
