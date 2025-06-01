package net.minecraft.world.level.redstone;

import com.google.common.collect.Sets;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockRedstoneWire;
import net.minecraft.world.level.block.state.IBlockData;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockRedstoneEvent;
// CraftBukkit end

public class DefaultRedstoneWireEvaluator extends RedstoneWireEvaluator {

    public DefaultRedstoneWireEvaluator(BlockRedstoneWire blockredstonewire) {
        super(blockredstonewire);
    }

    @Override
    public void updatePowerStrength(World world, BlockPosition blockposition, IBlockData iblockdata, @Nullable Orientation orientation, boolean flag) {
        int i = this.calculateTargetStrength(world, blockposition);

        // CraftBukkit start
        int oldPower = iblockdata.getValue(BlockRedstoneWire.POWER);
        if (oldPower != i) {
            BlockRedstoneEvent event = new BlockRedstoneEvent(CraftBlock.at(world, blockposition), oldPower, i);
            world.getCraftServer().getPluginManager().callEvent(event);

            i = event.getNewCurrent();
        }
        if (oldPower != i) {
            // CraftBukkit end
            if (world.getBlockState(blockposition) == iblockdata) {
                world.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockRedstoneWire.POWER, i), 2);
            }

            Set<BlockPosition> set = Sets.newHashSet();

            set.add(blockposition);

            for (EnumDirection enumdirection : EnumDirection.values()) {
                set.add(blockposition.relative(enumdirection));
            }

            for (BlockPosition blockposition1 : set) {
                world.updateNeighborsAt(blockposition1, this.wireBlock);
            }
        }

    }

    private int calculateTargetStrength(World world, BlockPosition blockposition) {
        int i = this.getBlockSignal(world, blockposition);

        return i == 15 ? i : Math.max(i, this.getIncomingWireSignal(world, blockposition));
    }
}
