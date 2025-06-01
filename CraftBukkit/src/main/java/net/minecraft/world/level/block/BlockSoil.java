package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.piston.BlockPistonMoving;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateInteger;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

// CraftBukkit start
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class BlockSoil extends Block {

    public static final MapCodec<BlockSoil> CODEC = simpleCodec(BlockSoil::new);
    public static final BlockStateInteger MOISTURE = BlockProperties.MOISTURE;
    private static final VoxelShape SHAPE = Block.column(16.0D, 0.0D, 15.0D);
    public static final int MAX_MOISTURE = 7;

    @Override
    public MapCodec<BlockSoil> codec() {
        return BlockSoil.CODEC;
    }

    protected BlockSoil(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) (this.stateDefinition.any()).setValue(BlockSoil.MOISTURE, 0));
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        if (enumdirection == EnumDirection.UP && !iblockdata.canSurvive(iworldreader, blockposition)) {
            scheduledtickaccess.scheduleTick(blockposition, (Block) this, 1);
        }

        return super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        IBlockData iblockdata1 = iworldreader.getBlockState(blockposition.above());

        return !iblockdata1.isSolid() || iblockdata1.getBlock() instanceof BlockFenceGate || iblockdata1.getBlock() instanceof BlockPistonMoving;
    }

    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        return !this.defaultBlockState().canSurvive(blockactioncontext.getLevel(), blockactioncontext.getClickedPos()) ? Blocks.DIRT.defaultBlockState() : super.getStateForPlacement(blockactioncontext);
    }

    @Override
    protected boolean useShapeForLightOcclusion(IBlockData iblockdata) {
        return true;
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockSoil.SHAPE;
    }

    @Override
    protected void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            turnToDirt((Entity) null, iblockdata, worldserver, blockposition);
        }

    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        int i = (Integer) iblockdata.getValue(BlockSoil.MOISTURE);

        if (!isNearWater(worldserver, blockposition) && !worldserver.isRainingAt(blockposition.above())) {
            if (i > 0) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(worldserver, blockposition, (IBlockData) iblockdata.setValue(BlockSoil.MOISTURE, i - 1), 2); // CraftBukkit
            } else if (!shouldMaintainFarmland(worldserver, blockposition)) {
                turnToDirt((Entity) null, iblockdata, worldserver, blockposition);
            }
        } else if (i < 7) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(worldserver, blockposition, (IBlockData) iblockdata.setValue(BlockSoil.MOISTURE, 7), 2); // CraftBukkit
        }

    }

    @Override
    public void fallOn(World world, IBlockData iblockdata, BlockPosition blockposition, Entity entity, double d0) {
        super.fallOn(world, iblockdata, blockposition, entity, d0); // CraftBukkit - moved here as game rules / events shouldn't affect fall damage.
        if (world instanceof WorldServer worldserver) {
            if ((double) world.random.nextFloat() < d0 - 0.5D && entity instanceof EntityLiving && (entity instanceof EntityHuman || worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) && entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() > 0.512F) {
                // CraftBukkit start - Interact soil
                org.bukkit.event.Cancellable cancellable;
                if (entity instanceof EntityHuman) {
                    cancellable = CraftEventFactory.callPlayerInteractEvent((EntityHuman) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
                } else {
                    cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                    world.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
                }

                if (cancellable.isCancelled()) {
                    return;
                }

                if (!CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, Blocks.DIRT.defaultBlockState())) {
                    return;
                }
                // CraftBukkit end
                turnToDirt(entity, iblockdata, world, blockposition);
            }
        }

        // super.fallOn(world, iblockdata, blockposition, entity, d0); // CraftBukkit - moved up
    }

    public static void turnToDirt(@Nullable Entity entity, IBlockData iblockdata, World world, BlockPosition blockposition) {
        // CraftBukkit start
        if (CraftEventFactory.callBlockFadeEvent(world, blockposition, Blocks.DIRT.defaultBlockState()).isCancelled()) {
            return;
        }
        // CraftBukkit end
        IBlockData iblockdata1 = pushEntitiesUp(iblockdata, Blocks.DIRT.defaultBlockState(), world, blockposition);

        world.setBlockAndUpdate(blockposition, iblockdata1);
        world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(entity, iblockdata1));
    }

    private static boolean shouldMaintainFarmland(IBlockAccess iblockaccess, BlockPosition blockposition) {
        return iblockaccess.getBlockState(blockposition.above()).is(TagsBlock.MAINTAINS_FARMLAND);
    }

    private static boolean isNearWater(IWorldReader iworldreader, BlockPosition blockposition) {
        for (BlockPosition blockposition1 : BlockPosition.betweenClosed(blockposition.offset(-4, 0, -4), blockposition.offset(4, 1, 4))) {
            if (iworldreader.getFluidState(blockposition1).is(TagsFluid.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockSoil.MOISTURE);
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return false;
    }
}
