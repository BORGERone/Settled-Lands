package dev.settledlands.smoke;
import dev.settledlands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.Arrays;
/** Proves that Sanctity II can actually be obtained in game, not only in data. */
public final class TableSmoke {
    private static int checks;
    private static void check(boolean condition,String name) {if(!condition)throw new AssertionError(name);System.out.println("TABLE CHECK OK: "+name);checks++;}
    public static int run(MinecraftServer server) {
        ServerLevel level=server.overworld();
        BlockPos table=new BlockPos(56,120,40);
        level.getChunkAt(table);
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(table).inflate(64)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        var player=FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(table.getX()+.5,table.getY()+1,table.getZ()+.5);
        var enchant=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Sanctity.KEY);
        check(enchant.value().getMinCost(2)<=20,"level II wants no more than a modest library: min power "+enchant.value().getMinCost(2));
        library(level,table,15);
        report(level,5,"weak table, 5 shelves");
        report(level,10,"medium table, 10 shelves");
        report(level,15,"full table, 15 shelves");
        // Level I must be obtainable even from a weak table, and from every row of a full one.
        int[] weak=clickRow(level,table,5,2,25);
        check(weak[1]>0,"a weak five shelf table still gives level I: "+Arrays.toString(weak));
        check(weak[2]==0,"a weak table can never give level II: "+Arrays.toString(weak));
        int[] weakFirst=clickRow(level,table,5,0,25);
        check(weakFirst[1]>0,"even the first row of a weak table gives level I: "+Arrays.toString(weakFirst));
        int[] fullTop=clickRow(level,table,15,2,40);
        check(fullTop[2]>0&&fullTop[1]==0,"the top row of a full table gives level II only: "+Arrays.toString(fullTop));
        int[] fullFirst=clickRow(level,table,15,0,25);
        check(fullFirst[1]>0,"the first row of a full table gives level I: "+Arrays.toString(fullFirst));
        int[] levels=new int[4];
        int offered=0,clues=0,costMin=Integer.MAX_VALUE,costMax=0;
        EnchantmentMenu menu=new EnchantmentMenu(151,player.getInventory(),ContainerLevelAccess.create(level,table));
        for(int i=0;i<60;i++) {
            menu.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
            player.experienceLevel=1000;
            if(menu.costs[2]<=0)continue;
            offered++;costMin=Math.min(costMin,menu.costs[2]);costMax=Math.max(costMax,menu.costs[2]);
            if(menu.levelClue[2]>=2)clues++;
            if(!menu.clickMenuButton(player,2))continue;
            int got=EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem());
            levels[Math.clamp(got,0,3)]++;
            menu.getSlot(0).set(ItemStack.EMPTY);
        }
        System.out.println("TABLE DEBUG full library: clicks="+offered+" levelII clues="+clues+" results="+Arrays.toString(levels));
        check(offered>50,"a full library always offers the top row for the "+offered+"/60 rolls");
        check(levels[2]>0,"the real enchanting table produces Sanctity II");
        check(clues>0,"the table hint shows the level II clue, so the numeral is visible before clicking");
        check(levels[1]+levels[2]==offered,"every click produced exactly one Sanctity, never a different amount");
        // The roll range of a full library must sit entirely above the level II threshold.
        System.out.println("TABLE DEBUG full library power range "+costMin+".."+costMax
            +" vs thresholds I="+enchant.value().getMinCost(1)+" II="+enchant.value().getMinCost(2));
        check(enchant.value().getMinCost(2)>enchant.value().getMinCost(1),"level II asks for more than level I, so the table still shows a progression");
        check(costMin>=enchant.value().getMinCost(2),"every full library roll reaches level II, never only level I");
        // Anvil prices: level I costs 2 levels, level II costs 6.
        BlockPos anvilPos=new BlockPos(56,120,48);level.getChunkAt(anvilPos);
        level.setBlockAndUpdate(anvilPos,Blocks.ANVIL.defaultBlockState());
        player.setPos(anvilPos.getX()+.5,anvilPos.getY()+1,anvilPos.getZ()+.5);
        ItemStack plain=new ItemStack(Items.WHITE_BANNER),worn=new ItemStack(Items.WHITE_BANNER);
        plain.set(DataComponents.BANNER_PATTERNS,new BannerPatternLayers.Builder().add(level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.STRIPE_CENTER),DyeColor.RED).build());
        plain.set(DataComponents.CUSTOM_NAME,Component.literal("Bought banner"));
        worn.enchant(enchant,1);
        AnvilMenu transfer=anvil(level,anvilPos,160);transfer.getSlot(0).set(plain);transfer.getSlot(1).set(worn);transfer.createResult();
        ItemStack bought=transfer.getSlot(2).getItem();
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,bought)==1,"a plain banner plus a level I banner yields level I");
        check(transfer.getCost()==2,"level I costs exactly 2 levels on the anvil, got "+transfer.getCost());
        check(plain.get(DataComponents.BANNER_PATTERNS)!=null&&bought.get(DataComponents.BANNER_PATTERNS)!=null&&bought.getHoverName().getString().equals("Bought banner"),"a bought level I banner keeps its own pattern and name");
        ItemStack strong=new ItemStack(Items.WHITE_BANNER);strong.enchant(enchant,2);
        AnvilMenu expensive=anvil(level,anvilPos,161);
        expensive.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));expensive.getSlot(1).set(strong);expensive.createResult();
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,expensive.getSlot(2).getItem())==2&&expensive.getCost()==6,"transferring level II costs exactly 6 levels, got "+expensive.getCost());
        AnvilMenu anvil=anvil(level,anvilPos,153);
        ItemStack left=new ItemStack(Items.WHITE_BANNER),right=new ItemStack(Items.WHITE_BANNER);
        left.enchant(enchant,1);right.enchant(enchant,1);
        anvil.getSlot(0).set(left);anvil.getSlot(1).set(right);anvil.createResult();
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,anvil.getSlot(2).getItem())==2,"two level I banners combine into level II on the anvil");
        check(anvil.getCost()==6,"level II costs exactly 6 levels on the anvil, got "+anvil.getCost());
        // Upgrading an existing level I with a level II sacrifice must not exceed the maximum.
        AnvilMenu cap=anvil(level,anvilPos,162);ItemStack two=new ItemStack(Items.WHITE_BANNER);two.enchant(enchant,2);
        cap.getSlot(0).set(left.copy());cap.getSlot(1).set(two);cap.createResult();
        check(EnchantmentHelper.getItemEnchantmentLevel(enchant,cap.getSlot(2).getItem())==2&&cap.getCost()==6,"merging with a level II sacrifice stops at level II and costs 6");
        AnvilMenu plainPair=anvil(level,anvilPos,154);
        plainPair.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));plainPair.getSlot(1).set(new ItemStack(Items.WHITE_BANNER));plainPair.createResult();
        check(plainPair.getSlot(2).getItem().isEmpty(),"two plain banners still produce nothing in the anvil");
        AnvilMenu mixed=anvil(level,anvilPos,155);
        mixed.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));mixed.getSlot(1).set(new ItemStack(Items.SHIELD));mixed.createResult();
        check(mixed.getSlot(2).getItem().isEmpty(),"a banner with an unrelated item stays vanilla");
        level.setBlockAndUpdate(anvilPos,Blocks.AIR.defaultBlockState());
        // The operator command must work with the new maximum.
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHITE_BANNER));
        int commandResult=command(player,"enchant @s settledlands:sanctity 2");
        check(commandResult==1&&EnchantmentHelper.getItemEnchantmentLevel(enchant,player.getMainHandItem())==2,"the /enchant command applies level II end to end");
        check(command(player,"enchant @s settledlands:sanctity 3")<0,"level III is still rejected");
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(table).inflate(64)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        System.out.println("TABLE SMOKE PASSED: "+checks+" checks");return checks;
    }

    /** Clicks one row repeatedly; index 1 counts level I hits, index 2 counts level II hits. */
    private static int[] clickRow(ServerLevel level,BlockPos table,int shelves,int row,int attempts) {
        library(level,table,shelves);
        var player=FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(table.getX()+.5,table.getY()+1,table.getZ()+.5);
        var enchant=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Sanctity.KEY);
        int[] levels=new int[3];
        EnchantmentMenu menu=new EnchantmentMenu(400+row,player.getInventory(),ContainerLevelAccess.create(level,table));
        for(int i=0;i<attempts;i++) {
            menu.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
            player.experienceLevel=1000;
            if(menu.costs[row]<=0)continue;
            if(!menu.clickMenuButton(player,row))continue;
            int got=EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem());
            if(got>0)levels[Math.min(got,2)]++;
        }
        return levels;
    }
    /** Measures what all three table rows show, using registry keys so the console stays readable. */
    private static void report(ServerLevel level,int shelves,String label) {
        BlockPos table=new BlockPos(56,120,40);
        library(level,table,shelves);
        var player=FakePlayerFactory.getMinecraft(level);
        player.setPos(table.getX()+.5,table.getY()+1,table.getZ()+.5);
        var registry=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for(int row=0;row<3;row++) {
            EnchantmentMenu menu=new EnchantmentMenu(300+row,player.getInventory(),ContainerLevelAccess.create(level,table));
            menu.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
            player.experienceLevel=1000;
            int cost=menu.costs[row];
            String hint=menu.enchantClue[row]<0?"none":key(registry,menu.enchantClue[row])+" level "+menu.levelClue[row];
            String shown=menu.enchantClue[row]<0?"nothing":key(registry,menu.enchantClue[row])+" level "+menu.levelClue[row];
            String got="-";
            if(menu.clickMenuButton(player,row)) {
                ItemStack result=menu.getSlot(0).getItem();
                var enchantments=EnchantmentHelper.getEnchantmentsForCrafting(result);
                got=enchantments.isEmpty()?"nothing":enchantments.entrySet().stream()
                    .map(e->e.getKey().unwrapKey().map(k->k.location().getPath()).orElse("?")+":"+e.getIntValue())
                    .sorted().reduce((a,b)->a+" "+b).orElse("?");
            }
            System.out.println("TABLE ROW "+shelves+" shelves row"+row+" cost="+cost+" hint="+hint+" result="+got);
            append("полок "+shelves+", строка "+(row+1)+": цена "+cost+", показывает "+shown+", выдаёт "+got+"\n");
        }
    }
    private static String key(net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment> registry,int id) {
        return registry.getHolder(id).flatMap(h->h.unwrapKey()).map(k->k.location().getPath()).orElse("?");
    }
    private static void append(String text) {
        try {java.nio.file.Files.writeString(java.nio.file.Path.of("table-report.txt"),text,
            java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}
        catch(java.io.IOException e) {throw new RuntimeException(e);}
    }
    private static AnvilMenu anvil(ServerLevel level,BlockPos pos,int id) {return new AnvilMenu(id,player(level).getInventory(),ContainerLevelAccess.create(level,pos));}
    private static net.minecraft.server.level.ServerPlayer player(ServerLevel level) {return FakePlayerFactory.getMinecraft(level);}
    /** Runs a real command through the server dispatcher, exactly as a typed command would. */
    private static int command(net.minecraft.server.level.ServerPlayer player,String command) {
        var dispatcher=player.getServer().getCommands().getDispatcher();
        try {return dispatcher.execute(dispatcher.parse(command,player.createCommandSourceStack().withPermission(4)));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) {return -1;}
    }
    /** Places a table with the requested number of shelves, strongest case is 15 like vanilla. */
    private static void library(ServerLevel level,BlockPos table,int shelves) {
        int placed=0;
        for(int dy=0;dy<=1;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            BlockPos pos=table.offset(dx,dy,dz);
            if(pos.equals(table))continue;
            boolean ring=Math.max(Math.abs(dx),Math.abs(dz))==2;
            if(ring&&placed<shelves) {level.setBlockAndUpdate(pos,Blocks.BOOKSHELF.defaultBlockState());placed++;}
            else level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(table,Blocks.ENCHANTING_TABLE.defaultBlockState());
    }
}
