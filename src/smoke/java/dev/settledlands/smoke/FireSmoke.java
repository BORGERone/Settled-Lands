package dev.settledlands.smoke;
import dev.settledlands.*;
import dev.settledlands.ritual.RitualManager;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
/** Sanctity II: undead burn inside their banner's own cell, blocks never catch fire. */
public final class FireSmoke {
    private static int checks;
    private static void check(boolean condition,String name) {if(!condition)throw new AssertionError(name);System.out.println("FIRE CHECK OK: "+name);checks++;}
    public static int run(MinecraftServer server) {
        ServerLevel level=server.overworld();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,server);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true,server);
        server.setDifficulty(Difficulty.HARD,true);
        level.setDayTime(18000);level.setWeatherParameters(6000,0,false,false);
        BlockPos first=new BlockPos(96,120,48),second=first.east(64);
        level.getChunkAt(first);level.getChunkAt(second);
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(first).inflate(96)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        BannerBlockEntity one=place(level,first,1),two=place(level,second,2);
        BlockPos thirdPos=second.east(64);level.getChunkAt(thirdPos);
        check(Sanctity.level(one)==1&&Sanctity.level(two)==2&&SanctityFire.burns(Sanctity.level(two)),"banner keeps the applied level and only II is a burning banner");
        check(Sanctity.holy(two)&&Sanctity.count(level,Cell.at(second))==1,"level II is still a normal Sanctity source for spawn protection");
        check(Sanctity.countFire(level,Cell.at(second))==1&&Sanctity.countFire(level,Cell.at(first))==0,"only the level II cell is indexed for burning");
        // Blocks that would spread fire if any block fire appeared.
        // Never on the banner's own position; the point is flammable ground around it.
        for(int dx=1;dx<=3;dx++)for(int dz=1;dz<=3;dz++)level.setBlockAndUpdate(second.offset(dx,0,dz),Blocks.OAK_PLANKS.defaultBlockState());
        Zombie subject=zombie(level,second.south()),witness=zombie(level,first.south());
        Zombie neighbour=zombie(level,second.east(17));
        Skeleton skeleton=skeleton(level,second.south(3));
        ZombifiedPiglin piglin=(ZombifiedPiglin)EntityType.ZOMBIFIED_PIGLIN.create(level);piglin.setPos(second.getX()+2.5,second.getY(),second.getZ()+2.5);piglin.setNoAi(true);piglin.setRemainingFireTicks(0);level.addFreshEntity(piglin);
        Cow cow=(Cow)EntityType.COW.create(level);cow.setPos(second.getX()+4.5,second.getY(),second.getZ()+4.5);cow.setNoAi(true);cow.setRemainingFireTicks(0);level.addFreshEntity(cow);
        Zombie wet=zombie(level,second.north(3));
        for(int dy=0;dy<2;dy++)level.setBlockAndUpdate(wet.blockPosition().above(dy),Blocks.WATER.defaultBlockState());
        for(int i=0;i<3;i++)Sanctity.burnLevel(level);
        check(subject.isOnFire()&&subject.getRemainingFireTicks()>0,"undead standing in the level II cell catches fire");
        check(!witness.isOnFire()&&!neighbour.isOnFire(),"level I cell and the neighbouring cell stay unlit");
        check(!cow.isOnFire(),"non-undead mobs are unaffected");
        check(skeleton.isOnFire(),"all undead types are affected, not only zombies");
        check(!piglin.isOnFire(),"fire-immune undead stay unlit");
        check(!wet.isOnFire(),"water still extinguishes, as in vanilla");
        // Our refresh must never add time: it only re-ignites after the fire burned out.
        subject.setRemainingFireTicks(50);
        Sanctity.burnLevel(level);Sanctity.burnLevel(level);
        check(subject.getRemainingFireTicks()==50,"burning is not extended while the mob is still alight");
        subject.setRemainingFireTicks(0);
        Sanctity.burnLevel(level);
        check(subject.getRemainingFireTicks()==SanctityFire.FIRE_TICKS,"an extinguished undead is re-ignited for exactly one vanilla fire");
        // Vanilla damage over one fire window; night is forced so sun burning cannot interfere.
        level.setDayTime(18000);
        subject.setHealth(subject.getMaxHealth());subject.setRemainingFireTicks(SanctityFire.FIRE_TICKS);
        float before=subject.getHealth();
        for(int i=0;i<40;i++) {subject.tick();if(i%5==0)Sanctity.burnLevel(level);}
        check(subject.getHealth()<before,"burning deals real vanilla fire damage");
        int fires=0;BlockPos min=Cell.min(Cell.at(second));
        for(BlockPos p:BlockPos.betweenClosed(min,min.offset(15,7,15)))if(level.getBlockState(p).getBlock() instanceof BaseFireBlock)fires++;
        check(fires==0,"no block fire anywhere in the burning cell, even with doFireTick and HARD");
        check(level.getBlockState(second.offset(1,0,1)).is(Blocks.OAK_PLANKS)&&subject.isAlive(),"flammable floor planks stay untouched and the burning mob survives the test window");
        // The ritual is unaffected: II is a stronger ward, not a different ritual rule.
        // Ordinary AI matters here: a NoAI mob is never allowed to start a ritual.
        subject.setNoAi(false);
        check(RitualManager.begin(subject,second),"a level II banner can still be desecrated");
        for(int i=0;i<5;i++)Sanctity.burnLevel(level);
        check(RitualManager.active(subject)&&subject.isOnFire(),"the ritual caster is not exempt from burning");
        RitualManager.abort();
        // Removing the banner stops the burning. Its loot is suppressed here, because the
        // ritual start above already armed the ten second no-drop ward on this exact banner.
        level.destroyBlock(second,true,FakePlayerFactory.getMinecraft(level));
        check(level.getEntitiesOfClass(ItemEntity.class,new AABB(second).inflate(2),e->e.getItem().getItem() instanceof BannerItem).isEmpty(),"ritual no-drop ward also silences the level II banner");
        subject.setRemainingFireTicks(0);Sanctity.burnLevel(level);
        check(!subject.isOnFire(),"banner removal stops any further ignition");
        // A fresh banner of each level, untouched by any ritual, keeps its level in the drop.
        BannerBlockEntity thirdBanner=place(level,thirdPos,2);
        level.destroyBlock(thirdPos,true,FakePlayerFactory.getMinecraft(level));
        ItemStack dropped=level.getEntitiesOfClass(ItemEntity.class,new AABB(thirdPos).inflate(2),e->e.getItem().getItem() instanceof BannerItem).stream().findFirst().map(ItemEntity::getItem).orElse(ItemStack.EMPTY);
        check(!dropped.isEmpty()&&dropped.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(holder(level))==2,"destroyed level II banner drops a level II banner item");
        level.destroyBlock(first,true,FakePlayerFactory.getMinecraft(level));
        ItemStack plainDrop=level.getEntitiesOfClass(ItemEntity.class,new AABB(first).inflate(2),e->e.getItem().getItem() instanceof BannerItem).stream().findFirst().map(ItemEntity::getItem).orElse(ItemStack.EMPTY);
        check(plainDrop.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(holder(level))==1,"level I still drops exactly level I");
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(first).inflate(96)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        System.out.println("FIRE SMOKE PASSED: "+checks+" checks");return checks;
    }
    private static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder(ServerLevel level) {return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Sanctity.KEY);}
    private static Zombie zombie(ServerLevel level,BlockPos pos) {return (Zombie)mob(level,pos,EntityType.ZOMBIE);}
    private static Skeleton skeleton(ServerLevel level,BlockPos pos) {return (Skeleton)mob(level,pos,EntityType.SKELETON);}
    private static Mob mob(ServerLevel level,BlockPos pos,EntityType<? extends Mob> type) {
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        Mob mob=type.create(level);mob.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);mob.setOnGround(true);mob.setNoAi(true);mob.setRemainingFireTicks(0);level.addFreshEntity(mob);return mob;
    }
    private static BannerBlockEntity place(ServerLevel level,BlockPos pos,int enchantLevel) {
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        var player=FakePlayerFactory.getMinecraft(level);ItemStack stack=new ItemStack(Items.WHITE_BANNER);
        stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Sanctity.KEY),enchantLevel);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));
        return (BannerBlockEntity)level.getBlockEntity(pos);
    }
}
