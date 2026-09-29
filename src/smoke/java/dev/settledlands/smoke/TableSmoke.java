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
/** Proves that Sanctity II can actually be obtained in game, not only in data.
 *  Row prices are rolled independently by vanilla, so the cheapest offer may sit in any row:
 *  no check here assumes which row number shows what. Every loop iteration ends with a real
 *  purchase, because the player seed refreshes only on success — without it all rolls stick. */
public final class TableSmoke {
    private static int checks;
    private static int nextMenuId=500;
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
        int sanctityId=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getId(enchant.value());
        // Weak table: level I in exactly one row, the rest blank; blank rows are dead slots.
        library(level,table,5);
        int weakOne=0,weakDup=0,weakBlank=0,weakBought=0;
        for(int i=0;i<25;i++) {
            EnchantmentMenu menu=freshBannerMenu(level,table,player);
            if(countOffers(menu,sanctityId,1)==1)weakOne++;
            if(duplicateOffer(menu))weakDup++;
            int blank=blankRow(menu);
            int levels=player.experienceLevel,lapis=menu.getSlot(1).getItem().getCount();
            if(blank>=0&&!menu.clickMenuButton(player,blank)&&player.experienceLevel==levels
                &&menu.getSlot(1).getItem().getCount()==lapis
                &&EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem())==0)weakBlank++;
            int offered=findRow(menu,sanctityId,1);
            if(offered>=0&&menu.clickMenuButton(player,offered)
                &&EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem())==1)weakBought++;
        }
        check(weakOne==25,"weak table offers level I in exactly one row: "+weakOne+"/25");
        check(weakDup==0,"weak table never shows the same offer twice: "+weakDup+" duplicates/25");
        check(weakBlank==25,"weak table rows without offers refuse clicks and consume nothing: "+weakBlank+"/25");
        check(weakBought==25,"weak table always sells level I from its offered row: "+weakBought+"/25");
        // Full table: level I in exactly one row, level II in exactly one other row.
        library(level,table,15);
        int fullOne=0,fullOther=0,fullDup=0,fullBlank=0,fullBoughtI=0,fullBoughtII=0;
        for(int i=0;i<40;i++) {
            EnchantmentMenu menu=freshBannerMenu(level,table,player);
            int iRow=findRow(menu,sanctityId,1),iiRow=findRow(menu,sanctityId,2);
            if(countOffers(menu,sanctityId,1)==1)fullOne++;
            if(countOffers(menu,sanctityId,2)==1&&iiRow>=0&&iiRow!=iRow)fullOther++;
            if(duplicateOffer(menu))fullDup++;
            int blank=blankRow(menu);
            int levels=player.experienceLevel,lapis=menu.getSlot(1).getItem().getCount();
            if(blank>=0&&!menu.clickMenuButton(player,blank)&&player.experienceLevel==levels
                &&menu.getSlot(1).getItem().getCount()==lapis
                &&EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem())==0)fullBlank++;
            if(iRow>=0&&menu.clickMenuButton(player,iRow)
                &&EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem())==1)fullBoughtI++;
            EnchantmentMenu second=freshBannerMenu(level,table,player);
            int buy=findRow(second,sanctityId,2);
            if(buy>=0&&second.clickMenuButton(player,buy)
                &&EnchantmentHelper.getItemEnchantmentLevel(enchant,second.getSlot(0).getItem())==2)fullBoughtII++;
        }
        check(fullOne==40,"full table offers level I in exactly one row: "+fullOne+"/40");
        check(fullOther==40,"full table offers level II in exactly one other row: "+fullOther+"/40");
        check(fullDup==0,"full table never shows the same offer twice: "+fullDup+" duplicates/40");
        check(fullBlank==40,"full table rows without offers refuse clicks and consume nothing: "+fullBlank+"/40");
        check(fullBoughtI==40,"full table always sells level I from its offered row: "+fullBoughtI+"/40");
        check(fullBoughtII==40,"full table always sells level II from its offered row: "+fullBoughtII+"/40");
        // Medium table: the no-duplicate invariant holds whatever the library rolls.
        library(level,table,10);
        int mediumDup=0,mediumBought=0;
        for(int i=0;i<25;i++) {
            EnchantmentMenu menu=freshBannerMenu(level,table,player);
            if(duplicateOffer(menu))mediumDup++;
            int offered=findRow(menu,sanctityId,1);
            if(offered>=0&&menu.clickMenuButton(player,offered)
                &&EnchantmentHelper.getItemEnchantmentLevel(enchant,menu.getSlot(0).getItem())==1)mediumBought++;
        }
        check(mediumDup==0,"medium table never shows the same offer twice: "+mediumDup+" duplicates/25");
        check(mediumBought==25,"medium table always sells level I from its offered row: "+mediumBought+"/25");
        // Dedup must not touch other items: a sword keeps all three vanilla rows.
        library(level,table,15);
        int swordRows=0,swordBought=0;
        for(int i=0;i<10;i++) {
            EnchantmentMenu menu=new EnchantmentMenu(nextMenuId++,player.getInventory(),ContainerLevelAccess.create(level,table));
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
            player.experienceLevel=1000;
            if(menu.costs[0]>0&&menu.costs[1]>0&&menu.costs[2]>0&&menu.enchantClue[0]>=0&&menu.enchantClue[1]>=0&&menu.enchantClue[2]>=0)swordRows++;
            if(menu.clickMenuButton(player,0))swordBought++;
        }
        check(swordRows==10,"a diamond sword keeps all three vanilla rows offered: "+swordRows+"/10");
        check(swordBought==10,"a diamond sword purchase succeeds: "+swordBought+"/10");
        check(enchant.value().getMinCost(2)>enchant.value().getMinCost(1),"level II asks for more than level I, so the table still shows a progression");
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

    /** A brand-new menu rolls brand-new offers; ids stay unique so menus never clash. */
    private static EnchantmentMenu freshBannerMenu(ServerLevel level,BlockPos table,net.minecraft.server.level.ServerPlayer player) {
        EnchantmentMenu menu=new EnchantmentMenu(nextMenuId++,player.getInventory(),ContainerLevelAccess.create(level,table));
        menu.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));
        player.experienceLevel=1000;
        return menu;
    }
    /** How many rows offer this enchantment at this level. */
    private static int countOffers(EnchantmentMenu menu,int clue,int level) {
        int found=0;
        for(int i=0;i<3;i++)if(menu.costs[i]>0&&menu.enchantClue[i]==clue&&menu.levelClue[i]==level)found++;
        return found;
    }
    /** First row showing this offer, or -1 when no row shows it. */
    private static int findRow(EnchantmentMenu menu,int clue,int level) {
        for(int i=0;i<3;i++)if(menu.costs[i]>0&&menu.enchantClue[i]==clue&&menu.levelClue[i]==level)return i;
        return -1;
    }
    /** First row without any offer, or -1 when every row shows something. */
    private static int blankRow(EnchantmentMenu menu) {
        for(int i=0;i<3;i++)if(menu.costs[i]<=0||menu.enchantClue[i]<0)return i;
        return -1;
    }
    /** Two offered rows showing the same enchantment at the same level. Blank rows never count. */
    private static boolean duplicateOffer(EnchantmentMenu menu) {
        for(int i=0;i<3;i++) {
            if(menu.costs[i]<=0||menu.enchantClue[i]<0)continue;
            for(int j=i+1;j<3;j++)
                if(menu.costs[j]>0&&menu.enchantClue[j]==menu.enchantClue[i]&&menu.levelClue[j]==menu.levelClue[i])return true;
        }
        return false;
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
