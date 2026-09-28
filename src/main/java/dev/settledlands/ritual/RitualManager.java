package dev.settledlands.ritual;

import dev.settledlands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One server-thread-owned state for the entire server, across dimensions. */
public final class RitualManager {
    public static final String FROZEN="settledlands_ritual_frozen",OLD_GLOW="settledlands_ritual_old_glow";
    public static final String BOLT="settledlands_ritual_bolt",PROTECTED="settledlands_ritual_replacement";
    public record State(Zombie zombie,ServerLevel level,BannerBlockEntity banner,BlockPos target,Vec3 anchor,long started,boolean oldGlow) {
        public int age() {return (int)Math.max(0,level.getGameTime()-started);}
    }
    private static State current;
    public static State state() {return current;}
    public static boolean busy() {return current!=null;}
    public static boolean active(Entity e) {return current!=null&&current.zombie==e;}
    public static boolean begin(Zombie zombie,BlockPos target) {
        if(busy()||target==null||!Settings.RITUALS.get()||zombie.getType()!=EntityType.ZOMBIE||!zombie.isAlive()||zombie.isNoAi()||zombie.isPassenger())return false;
        if(!(zombie.level() instanceof ServerLevel level)||!level.hasChunkAt(target)||!EventHooks.canEntityGrief(level,zombie))return false;
        if(Cell.at(zombie.blockPosition())!=Cell.at(target))return false;
        if(!(level.getBlockEntity(target) instanceof BannerBlockEntity banner)||!Sanctity.holy(banner)||RitualWard.immune(banner,level.getGameTime()))return false;
        current=new State(zombie,level,banner,target.immutable(),zombie.position(),level.getGameTime(),zombie.hasGlowingTag());
        RitualWard.mark(banner,level.getGameTime());
        zombie.getPersistentData().putBoolean(FROZEN,true);zombie.getPersistentData().putBoolean(OLD_GLOW,current.oldGlow);
        zombie.setTarget(null);zombie.setAggressive(false);zombie.getNavigation().stop();zombie.setDeltaMovement(Vec3.ZERO);zombie.setNoAi(true);
        zombie.setYRot((float)Math.toDegrees(Math.atan2(-(target.getX()+0.5-zombie.getX()),target.getZ()+0.5-zombie.getZ())));
        zombie.yBodyRot=zombie.getYRot();zombie.yHeadRot=zombie.getYRot();
        sound(current,SoundEvents.EVOKER_PREPARE_ATTACK,0.8f,0.7f);RitualPayload.broadcast(current,true);return true;
    }
    private static void sound(State s,SoundEvent sound,float volume,float pitch) {s.level.playSound(null,s.zombie.blockPosition(),sound,SoundSource.HOSTILE,volume,pitch);}
    public static void abort() {
        State s=current;if(s==null)return;current=null;
        s.zombie.setNoAi(false);s.zombie.setGlowingTag(s.oldGlow);s.zombie.setDeltaMovement(Vec3.ZERO);
        s.zombie.getPersistentData().remove(FROZEN);s.zombie.getPersistentData().remove(OLD_GLOW);
        RitualPayload.broadcast(s,false);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        State s=current;if(s==null)return;
        Zombie z=s.zombie;
        if(!z.isAlive()||z.isRemoved()||z.level()!=s.level||z.isPassenger()||!Settings.RITUALS.get()
            ||!s.level.hasChunkAt(s.target)||s.level.getBlockEntity(s.target)!=s.banner||!Sanctity.holy(s.banner)
            ||Cell.at(z.blockPosition())!=Cell.at(s.target)||!EventHooks.canEntityGrief(s.level,z)) {abort();return;}
        z.getNavigation().stop();z.setDeltaMovement(Vec3.ZERO);z.setTarget(null);z.setAggressive(false);
        z.setPos(s.anchor);z.yHeadRot=z.yBodyRot=z.getYRot();
        int age=s.age();
        if(age==RitualRules.INTRO)sound(s,SoundEvents.EVOKER_CAST_SPELL,1f,0.65f);
        if(age>=RitualRules.INTRO)z.setGlowingTag(true);
        if(age%20==0)RitualPayload.broadcast(s,true);
        if(RitualRules.finished(age))finish(s);
    }
    private static void finish(State s) {
        // Abort if vanilla (or a cooperating mod) refuses destruction. No conversion without loss of banner.
        if(!s.level.destroyBlock(s.target,true,s.zombie)) {abort();return;}
        Zombie z=s.zombie;
        var stray=EntityType.STRAY.create(s.level);
        var bolt=EntityType.LIGHTNING_BOLT.create(s.level);
        if(stray==null||bolt==null) {abort();return;}
        stray.moveTo(z.getX(),z.getY(),z.getZ(),z.getYRot(),0);
        stray.finalizeSpawn(s.level,s.level.getCurrentDifficultyAt(z.blockPosition()),MobSpawnType.CONVERSION,null);
        stray.setPersistenceRequired();
        if(z.hasCustomName()) {stray.setCustomName(z.getCustomName());stray.setCustomNameVisible(z.isCustomNameVisible());}
        // Real damaging lightning; suppress only block fire and hits on its own newborn replacement.
        bolt.moveTo(z.position());bolt.setVisualOnly(false);bolt.getPersistentData().putBoolean(BOLT,true);
        bolt.getPersistentData().putUUID(PROTECTED,stray.getUUID());
        if(!s.level.addFreshEntity(stray)) {abort();return;}
        abort();z.discard();s.level.addFreshEntity(bolt);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void death(LivingDeathEvent e) {if(active(e.getEntity()))abort();}
    @SubscribeEvent public void leave(EntityLeaveLevelEvent e) {if(active(e.getEntity()))abort();}
    @SubscribeEvent public void join(EntityJoinLevelEvent e) {
        if(!(e.getLevel() instanceof ServerLevel)||!(e.getEntity() instanceof Zombie z)||z.getType()!=EntityType.ZOMBIE)return;
        // In-progress rituals do not resume after unloading/restart; restore only our own flags.
        if(z.getPersistentData().getBoolean(FROZEN)) {
            z.setNoAi(false);z.setGlowingTag(z.getPersistentData().getBoolean(OLD_GLOW));
            z.getPersistentData().remove(FROZEN);z.getPersistentData().remove(OLD_GLOW);
        }
        if(z.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof BannerRitualGoal))z.goalSelector.addGoal(0,new BannerRitualGoal(z));
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent e) {abort();}
}
