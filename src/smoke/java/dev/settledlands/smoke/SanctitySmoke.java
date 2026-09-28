package dev.settledlands.smoke;

import dev.settledlands.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid=SettledLands.ID)
public final class SanctitySmoke {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        System.out.println("SANCTITY CHECK OK: "+message);checks++;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        MinecraftServer server=event.getServer();
        server.execute(()->{
            try { run(server); checks+=RitualSmoke.run(server); checks+=FireSmoke.run(server); checks+=TableSmoke.run(server); System.out.println("SANCTITY SMOKE PASSED: "+checks+" checks"); result("PASSED "+checks); }
            catch(Throwable t) { System.out.println("SANCTITY SMOKE FAILED");t.printStackTrace(); result("FAILED "+t); }
            finally { server.halt(false); }
        });
    }
    private static java.util.stream.Stream<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> enchantmentStream(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().map(h->(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>)h);
    }
    private static void result(String text) {
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("sanctity-smoke-result.txt"),text); }
        catch(java.io.IOException e) { throw new RuntimeException(e); }
    }
    private static void run(MinecraftServer server) {
        ServerLevel level=server.overworld();BlockPos pos=new BlockPos(8,120,8),table=new BlockPos(8,120,20);
        level.getChunkAt(pos);level.getChunkAt(table);
        if(level.getBlockEntity(pos) instanceof BannerBlockEntity previous) {
            check(Sanctity.holy(previous),"restart restores enchantment from real saved chunk");
            check(Sanctity.count(level,Cell.at(pos))==1,"restart rebuilds loaded-chunk protection index");
        }
        level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(16)).forEach(Entity::discard);
        var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.setPos(8,120,9);
        var enchant=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Sanctity.KEY);
        check(enchant.value().getMaxLevel()==2,"data-driven enchantment has two levels");
        check(enchant.value().getMinCost(2)>enchant.value().getMinCost(1)&&enchant.value().canEnchant(new ItemStack(Items.WHITE_BANNER)),"level II is reachable on banners through normal enchanting rules");
        ItemStack banner=new ItemStack(Items.WHITE_BANNER);
        var patterns=new BannerPatternLayers.Builder().add(level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.STRIPE_CENTER),DyeColor.RED).build();
        banner.set(DataComponents.BANNER_PATTERNS,patterns);banner.set(DataComponents.CUSTOM_NAME,Component.literal("Pattern survival test"));
        check(banner.isEnchantable(),"banner accepted as enchantable by actual mixed-in Item code");
        check(banner.getEnchantmentValue()==10,"enchantability value 10");
        check(!new ItemStack(Items.COBBLESTONE).isEnchantable(),"unrelated item remains not enchantable");
        // A real server-side enchantment menu, real levels and lapis, with bookshelves.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=1;dy++)
            level.setBlockAndUpdate(table.offset(dx,dy,dz),Math.max(Math.abs(dx),Math.abs(dz))==2?Blocks.BOOKSHELF.defaultBlockState():Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(table,Blocks.ENCHANTING_TABLE.defaultBlockState());
        player.experienceLevel=30;
        EnchantmentMenu menu=new EnchantmentMenu(99,player.getInventory(),ContainerLevelAccess.create(level,table));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));menu.getSlot(0).set(banner);
        int row=-1;
        for(int i=0;i<3;i++)if(menu.costs[i]>0&&menu.enchantClue[i]>=0) {row=i;break;}
        check(row>=0,"table offers an enchantment for a patterned banner");
        check(menu.clickMenuButton(player,row),"table enchant action succeeds");
        ItemStack enchanted=menu.getSlot(0).getItem().copy();
        int tableLevel=EnchantmentHelper.getItemEnchantmentLevel(enchant,enchanted);
        check(tableLevel>=0&&tableLevel<=2,"real table click never exceeds the new maximum level "+tableLevel);
        // Pin the rest of the protection/loot sequence to level I; both levels get their own checks.
        EnchantmentHelper.updateEnchantments(enchanted,mutable->mutable.set(enchant,1));
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,enchanted)==1,"level I is fixed for the protection sequence");
        // Deterministic rolls at fixed enchanting power, same code path the table uses.
        ItemStack strong=EnchantmentHelper.enchantItem(RandomSource.create(7),new ItemStack(Items.WHITE_BANNER),30,enchantmentStream(level));
        ItemStack weak=EnchantmentHelper.enchantItem(RandomSource.create(7),new ItemStack(Items.WHITE_BANNER),16,enchantmentStream(level));
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,strong)==2,"maximum table power produces Sanctity II");
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,weak)==1,"lower table power still produces Sanctity I");
        check(player.experienceLevel==30-(row+1) && menu.getSlot(1).getItem().getCount()==3-(row+1),"table consumes "+(row+1)+" levels and lapis");
        check(patterns.equals(enchanted.get(DataComponents.BANNER_PATTERNS)),"enchanting preserves patterns");
        check(enchanted.getHoverName().getString().equals("Pattern survival test"),"enchanting preserves custom name");
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND,enchanted.copy());
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));
        check(level.getBlockEntity(pos) instanceof BannerBlockEntity,"normal item placement creates banner block entity");
        BannerBlockEntity placed=(BannerBlockEntity)level.getBlockEntity(pos);
        long cell=Cell.at(pos);
        check(Sanctity.holy(placed)&&Sanctity.count(level,cell)==1,"placement indexes the enchanted banner");
        check(patterns.equals(placed.collectComponents().get(DataComponents.BANNER_PATTERNS)),"placement preserves patterns");
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,placed.getItem())==1,"vanilla pick-block preserves Sanctity");
        TerritoryData data=TerritoryData.get(level);long now=ActivityClock.get(server).seconds;data.force(cell,40,70,false,now);
        var zombie=EntityType.ZOMBIE.create(level);zombie.setPos(8,120,8);
        var spawn=new MobSpawnEvent.PositionCheck(zombie,level,MobSpawnType.NATURAL,null);NeoForge.EVENT_BUS.post(spawn);
        check(spawn.getResult()==MobSpawnEvent.PositionCheck.Result.FAIL,"holy cell blocks natural zombie spawn check");
        var egg=new MobSpawnEvent.PositionCheck(zombie,level,MobSpawnType.SPAWN_EGG,null);NeoForge.EVENT_BUS.post(egg);
        check(egg.getResult()!=MobSpawnEvent.PositionCheck.Result.FAIL,"spawn eggs are not blocked");
        check(Sanctity.count(level,Cell.offset(cell,1,0))==0 && Sanctity.count(level,Cell.at(pos.below(8)))==0,"no protection leaks to adjacent or lower cells");
        var saved=placed.saveWithFullMetadata(level.registryAccess());
        var restored=BlockEntity.loadStatic(pos,placed.getBlockState(),saved,level.registryAccess());
        check(restored instanceof BannerBlockEntity b && Sanctity.holy(b) && patterns.equals(b.collectComponents().get(DataComponents.BANNER_PATTERNS)),"block entity NBT round-trip preserves enchantment and patterns");
        level.destroyBlock(pos,true,player);
        check(Sanctity.count(level,cell)==0,"breaking the sole banner removes protection immediately");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2),i->i.getItem().getItem() instanceof BannerItem);
        check(drops.size()==1 && drops.getFirst().getItem().getCount()==1,"exactly one banner drops; no duplication");
        ItemStack dropped=drops.getFirst().getItem().copy();
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,dropped)==1 && patterns.equals(dropped.get(DataComponents.BANNER_PATTERNS)),"normal drop retains enchantment and pattern");
        check(dropped.getHoverName().getString().equals("Pattern survival test"),"normal drop retains custom name");
        check(data.score(cell,now)==40 && data.kills(cell)==70,"banner never overwrites T or P");
        drops.forEach(Entity::discard);
        // Re-place the actual drop, then add a second source in the same cell.
        place(level,player,pos,dropped);BlockPos second=pos.east(2);place(level,player,second,dropped);
        check(Sanctity.count(level,cell)==2,"multiple independent sources counted");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        check(Sanctity.count(level,cell)==1,"command-style no-drop removal leaves second source active");
        level.destroyBlock(second.below(),true,player);
        check(Sanctity.count(level,cell)==0,"destroying the support removes last protection");
        var supportDrops=level.getEntitiesOfClass(ItemEntity.class,new AABB(second).inflate(2),i->i.getItem().getItem() instanceof BannerItem);
        check(supportDrops.size()==1 && EnchantmentHelper.getItemEnchantmentLevel(enchant,supportDrops.getFirst().getItem())==1,"support-loss drop retains Sanctity");
        // Wall-mounted banner uses the same saved block entity components.
        level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(16)).forEach(Entity::discard);
        BlockPos support=pos.east(4),wall=support.east();
        level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND,dropped.copy());
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support),Direction.EAST,support,false)));
        check(level.getBlockState(wall).getBlock() instanceof net.minecraft.world.level.block.WallBannerBlock,"item placement supports wall banners");
        check(Sanctity.count(level,cell)==1,"wall banner protects its cell");
        level.destroyBlock(support,true,player);
        check(Sanctity.count(level,cell)==0,"wall support destruction removes protection");
        var wallDrops=level.getEntitiesOfClass(ItemEntity.class,new AABB(wall).inflate(2),i->i.getItem().getItem() instanceof BannerItem);
        check(wallDrops.size()==1 && EnchantmentHelper.getItemEnchantmentLevel(enchant,wallDrops.getFirst().getItem())==1 && patterns.equals(wallDrops.getFirst().getItem().get(DataComponents.BANNER_PATTERNS)),"wall drop preserves Sanctity and pattern");
        level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(16)).forEach(Entity::discard);
        place(level,player,pos,dropped);
        level.explode(null,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,4,net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
        check(!(level.getBlockEntity(pos) instanceof BannerBlockEntity) && Sanctity.count(level,cell)==0,"explosion destroys banner and removes protection");
        var explosionDrops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(8),i->i.getItem().getItem() instanceof BannerItem);
        check(explosionDrops.size()<=1 && explosionDrops.stream().allMatch(i->EnchantmentHelper.getItemEnchantmentLevel(enchant,i.getItem())==1 && patterns.equals(i.getItem().get(DataComponents.BANNER_PATTERNS))),"any surviving explosion drop retains enchantment and pattern");
        // Keep one real banner in the test world for a subsequent restart/index test.
        place(level,player,pos,dropped);
        System.out.println("SANCTITY RESTART FIXTURE: 8 120 8");
    }
    private static void place(ServerLevel level,net.neoforged.neoforge.common.util.FakePlayer player,BlockPos pos,ItemStack stack) {
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND,stack.copy());
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));
    }
}
