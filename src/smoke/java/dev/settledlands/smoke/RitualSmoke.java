package dev.settledlands.smoke;
import dev.settledlands.*;
import dev.settledlands.ritual.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
public final class RitualSmoke {
    private static int checks;
    private static void check(boolean condition,String name) {if(!condition)throw new AssertionError(name);System.out.println("RITUAL CHECK OK: "+name);checks++;}
    public static int run(MinecraftServer server) {
        ServerLevel level=server.overworld();level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,server);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true,server);server.setDifficulty(Difficulty.HARD,true);
        BlockPos pos=new BlockPos(40,120,8),other=pos.east(2);level.getChunkAt(pos);level.getChunkAt(pos.east(16));
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(pos).inflate(24)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        BannerBlockEntity banner=place(level,pos),second=place(level,other);
        Zombie z=zombie(level,pos.south()),z2=zombie(level,pos.south(2)),outside=zombie(level,pos.east(16));
        check(z.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof BannerRitualGoal),"ordinary zombie receives ritual goal");
        check(!RitualManager.begin(outside,pos),"zombie in adjacent cell cannot target banner");
        long start=level.getGameTime();
        var goal=(BannerRitualGoal)z.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof BannerRitualGoal).findFirst().orElseThrow().getGoal();
        z.tickCount=500;check(goal.canUse(),"same-cell AI selects holy banner without line-of-sight/pathfinding");goal.start();
        check(RitualManager.active(z)&&z.isNoAi()&&z.getTarget()==null,"AI starts intro and disables ordinary attacks");
        check(!RitualManager.begin(z2,other),"second zombie cannot start even on another banner while global slot busy");
        check(RitualWard.noDrop(banner,start)&&RitualWard.immune(banner,start),"both banner timers start with cult");
        var before=z.position();z.move(MoverType.SELF,new Vec3(1,0,0));check(z.position().equals(before),"ritual zombie cannot be moved by normal travel");
        check(Block.getDrops(banner.getBlockState(),level,pos,banner).isEmpty(),"ritual banner loot suppressed immediately");
        var copy=(BannerBlockEntity)BlockEntity.loadStatic(pos,banner.getBlockState(),banner.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
        check(copy!=null&&RitualWard.noDrop(copy,start+199)&&RitualWard.immune(copy,start+399),"timers survive block entity NBT persistence");
        check(!RitualWard.noDrop(copy,start+200)&&RitualWard.immune(copy,start+200)&&!RitualWard.immune(copy,start+400),"10 and 20 second boundaries are independent");
        z.kill();check(!RitualManager.busy()&&!z.isNoAi(),"killing caster releases global slot and freeze flags");
        check(RitualWard.noDrop(banner,start)&&RitualWard.immune(banner,start),"killing caster does not clear banner timers");
        check(!RitualManager.begin(z2,pos),"same banner cannot be retried during 20 second immunity");
        server.getWorldData().overworldData().setGameTime(start+200);check(!Block.getDrops(banner.getBlockState(),level,pos,banner).isEmpty(),"normal banner loot returns after 10 seconds");
        server.getWorldData().overworldData().setGameTime(start+400);check(RitualManager.begin(z2,pos),"banner can be targeted again after 20 seconds");
        RitualManager.abort();
        // Destruction by player/scripting while marked: no item, immediate abort on next state check.
        banner=place(level,pos);Zombie breakerCase=zombie(level,pos.south());check(RitualManager.begin(breakerCase,pos),"fresh banner can start ritual");
        level.destroyBlock(pos,true,FakePlayerFactory.getMinecraft(level));tick(server);
        check(!RitualManager.busy()&&!breakerCase.isNoAi(),"removing target aborts ritual and restores caster AI");
        check(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5),e->e.getItem().getItem() instanceof BannerItem).isEmpty(),"breaking marked banner yields no dropped banner");
        breakerCase.discard();z2.discard();outside.discard();
        banner=place(level,pos);Zombie caster=zombie(level,pos.south());
        long fullStart=level.getGameTime();check(RitualManager.begin(caster,pos),"completion case starts");
        for(int t=1;t<40;t++) {server.getWorldData().overworldData().setGameTime(fullStart+t);tick(server);}
        check(!caster.hasGlowingTag()&&level.getBlockEntity(pos)==banner,"cult phase lasts 40 ticks without completing");
        server.getWorldData().overworldData().setGameTime(fullStart+40);tick(server);check(caster.hasGlowingTag(),"cultIDE starts with spectral-style glow");
        // Damage does not interrupt; killing is required.
        caster.hurt(level.damageSources().generic(),1);tick(server);check(RitualManager.active(caster),"nonlethal damage does not cancel ritual");
        for(int t=41;t<140;t++) {server.getWorldData().overworldData().setGameTime(fullStart+t);tick(server);}
        check(RitualManager.active(caster)&&level.getBlockEntity(pos)==banner,"banner survives through tick 139");
        // Lightning neighbours, a creeper and a durable mob, within vanilla lightning range.
        Creeper creeper=EntityType.CREEPER.create(level);creeper.setPos(caster.getX()+1,caster.getY(),caster.getZ());creeper.setNoAi(true);level.addFreshEntity(creeper);
        IronGolem victim=EntityType.IRON_GOLEM.create(level);victim.setPos(caster.getX()-1,caster.getY(),caster.getZ());victim.setNoAi(true);level.addFreshEntity(victim);float health=victim.getHealth();
        server.getWorldData().overworldData().setGameTime(fullStart+140);tick(server);
        check(!RitualManager.busy()&&caster.isRemoved()&&level.getBlockEntity(pos)==null,"at tick 140 banner destroyed and zombie removed");
        var strays=level.getEntitiesOfClass(Stray.class,new AABB(pos).inflate(5));check(strays.size()==1,"exactly one stray replaces caster");
        check(strays.getFirst().getMainHandItem().is(Items.BOW),"replacement stray receives its vanilla bow");
        var bolts=level.getEntitiesOfClass(LightningBolt.class,new AABB(pos).inflate(5));check(bolts.size()==1,"one real lightning entity is spawned");
        LightningBolt bolt=bolts.getFirst();float strayHealth=strays.getFirst().getHealth();
        for(int i=0;i<20&&!bolt.isRemoved();i++)bolt.tick();
        check(creeper.isPowered(),"ritual lightning charges nearby creeper");
        check(victim.getHealth()<health,"ritual lightning damages nearby mob");
        check(strays.getFirst().getHealth()==strayHealth&&!strays.getFirst().isOnFire(),"own replacement is protected from ritual lightning");
        check(bolt.getBlocksSetOnFire()==0,"ritual lightning creates no block fire even with doFireTick=true and HARD");
        check(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5),e->e.getItem().getItem() instanceof BannerItem).isEmpty(),"successful ritual also respects no-drop timer");
        // Restore interrupted entities loaded with saved freeze state; no permanently frozen zombies.
        Zombie saved=EntityType.ZOMBIE.create(level);saved.setPos(40,120,12);saved.setNoAi(true);saved.setGlowingTag(true);
        saved.getPersistentData().putBoolean(RitualManager.FROZEN,true);saved.getPersistentData().putBoolean(RitualManager.OLD_GLOW,false);level.addFreshEntity(saved);
        check(!saved.isNoAi()&&!saved.hasGlowingTag()&&!saved.getPersistentData().getBoolean(RitualManager.FROZEN),"loading an interrupted caster restores AI and previous glow flag");
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(pos).inflate(24)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        System.out.println("RITUAL SMOKE PASSED: "+checks+" checks");return checks;
    }
    private static void tick(MinecraftServer s) {new RitualManager().tick(new ServerTickEvent.Post(()->true,s));}
    private static Zombie zombie(ServerLevel level,BlockPos pos) {
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());Zombie z=EntityType.ZOMBIE.create(level);z.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);z.setOnGround(true);level.addFreshEntity(z);return z;
    }
    private static BannerBlockEntity place(ServerLevel level,BlockPos pos) {
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        var player=FakePlayerFactory.getMinecraft(level);ItemStack stack=new ItemStack(Items.WHITE_BANNER);
        stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Sanctity.KEY),1);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));
        return (BannerBlockEntity)level.getBlockEntity(pos);
    }
}
