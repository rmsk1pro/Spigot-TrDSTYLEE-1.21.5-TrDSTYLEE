package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.event.block.BlockFormEvent;
// CraftBukkit end

public class BlockConcretePowder extends BlockFalling {

    public static final MapCodec<BlockConcretePowder> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("concrete").forGetter((blockconcretepowder) -> {
            return blockconcretepowder.concrete;
        }), propertiesCodec()).apply(instance, BlockConcretePowder::new);
    });
    private final Block concrete;

    @Override
    public MapCodec<BlockConcretePowder> codec() {
        return BlockConcretePowder.CODEC;
    }

    public BlockConcretePowder(Block block, BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.concrete = block;
    }

    @Override
    public void onLand(World world, BlockPosition blockposition, IBlockData iblockdata, IBlockData iblockdata1, EntityFallingBlock entityfallingblock) {
        if (shouldSolidify(world, blockposition, iblockdata1)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition, this.concrete.defaultBlockState(), 3); // CraftBukkit
        }

    }

    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        IBlockAccess iblockaccess = blockactioncontext.getLevel();
        BlockPosition blockposition = blockactioncontext.getClickedPos();
        IBlockData iblockdata = iblockaccess.getBlockState(blockposition);

        // CraftBukkit start
        if (!shouldSolidify(iblockaccess, blockposition, iblockdata)) {
            return super.getStateForPlacement(blockactioncontext);
        }

        // TODO: An event factory call for methods like this
        CraftBlockState blockState = CraftBlockStates.getBlockState(blockactioncontext.getLevel(), blockposition);
        blockState.setData(this.concrete.defaultBlockState());

        BlockFormEvent event = new BlockFormEvent(blockState.getBlock(), blockState);
        blockactioncontext.getLevel().getServer().server.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            return blockState.getHandle();
        }

        return super.getStateForPlacement(blockactioncontext);
        // CraftBukkit end
    }

    private static boolean shouldSolidify(IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata) {
        return canSolidify(iblockdata) || touchesLiquid(iblockaccess, blockposition);
    }

    private static boolean touchesLiquid(IBlockAccess iblockaccess, BlockPosition blockposition) {
        boolean flag = false;
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();

        for (EnumDirection enumdirection : EnumDirection.values()) {
            IBlockData iblockdata = iblockaccess.getBlockState(blockposition_mutableblockposition);

            if (enumdirection != EnumDirection.DOWN || canSolidify(iblockdata)) {
                blockposition_mutableblockposition.setWithOffset(blockposition, enumdirection);
                iblockdata = iblockaccess.getBlockState(blockposition_mutableblockposition);
                if (canSolidify(iblockdata) && !iblockdata.isFaceSturdy(iblockaccess, blockposition, enumdirection.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static boolean canSolidify(IBlockData iblockdata) {
        return iblockdata.getFluidState().is(TagsFluid.WATER);
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        // CraftBukkit start
        if (touchesLiquid(iworldreader, blockposition)) {
            // Suppress during worldgen
            if (!(iworldreader instanceof World world)) {
                return this.concrete.defaultBlockState();
            }
            CraftBlockState blockState = CraftBlockStates.getBlockState(world, blockposition);
            blockState.setData(this.concrete.defaultBlockState());

            BlockFormEvent event = new BlockFormEvent(blockState.getBlock(), blockState);
            world.getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }

        return super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
        // CraftBukkit end
    }

    @Override
    public int getDustColor(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        return iblockdata.getMapColor(iblockaccess, blockposition).col;
    }
}
