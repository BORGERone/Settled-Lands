package dev.settledlands;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
public final class Household {
    public static int category(BlockState state) {
        Block b=state.getBlock();
        if(b instanceof BedBlock)return 1;
        if(b instanceof ChestBlock || b instanceof BarrelBlock)return 2;
        if(b instanceof CraftingTableBlock || b instanceof AbstractFurnaceBlock)return 3;
        if(b instanceof CampfireBlock)return 4;
        return 0;
    }
    private Household() {}
}
