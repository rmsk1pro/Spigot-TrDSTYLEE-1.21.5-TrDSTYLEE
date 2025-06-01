package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockDirectional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnumBlockMirror;
import net.minecraft.world.level.block.EnumBlockRotation;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyPistonType;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.EnumPistonReaction;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

// CraftBukkit start
import com.google.common.collect.ImmutableList;
import java.util.AbstractList;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
// CraftBukkit end

public class BlockPiston extends BlockDirectional {

    public static final MapCodec<BlockPiston> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("sticky").forGetter((blockpiston) -> {
            return blockpiston.isSticky;
        }), propertiesCodec()).apply(instance, BlockPiston::new);
    });
    public static final BlockStateBoolean EXTENDED = BlockProperties.EXTENDED;
    public static final int TRIGGER_EXTEND = 0;
    public static final int TRIGGER_CONTRACT = 1;
    public static final int TRIGGER_DROP = 2;
    public static final int PLATFORM_THICKNESS = 4;
    private static final Map<EnumDirection, VoxelShape> SHAPES = VoxelShapes.rotateAll(Block.boxZ(16.0D, 4.0D, 16.0D));
    private final boolean isSticky;

    @Override
    public MapCodec<BlockPiston> codec() {
        return BlockPiston.CODEC;
    }

    public BlockPiston(boolean flag, BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockPiston.FACING, EnumDirection.NORTH)).setValue(BlockPiston.EXTENDED, false));
        this.isSticky = flag;
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (Boolean) iblockdata.getValue(BlockPiston.EXTENDED) ? (VoxelShape) BlockPiston.SHAPES.get(iblockdata.getValue(BlockPiston.FACING)) : VoxelShapes.block();
    }

    @Override
    public void setPlacedBy(World world, BlockPosition blockposition, IBlockData iblockdata, EntityLiving entityliving, ItemStack itemstack) {
        if (!world.isClientSide) {
            this.checkIfExtend(world, blockposition, iblockdata);
        }

    }

    @Override
    protected void neighborChanged(IBlockData iblockdata, World world, BlockPosition blockposition, Block block, @Nullable Orientation orientation, boolean flag) {
        if (!world.isClientSide) {
            this.checkIfExtend(world, blockposition, iblockdata);
        }

    }

    @Override
    protected void onPlace(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            if (!world.isClientSide && world.getBlockEntity(blockposition) == null) {
                this.checkIfExtend(world, blockposition, iblockdata);
            }

        }
    }

    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        return (IBlockData) ((IBlockData) this.defaultBlockState().setValue(BlockPiston.FACING, blockactioncontext.getNearestLookingDirection().getOpposite())).setValue(BlockPiston.EXTENDED, false);
    }

    private void checkIfExtend(World world, BlockPosition blockposition, IBlockData iblockdata) {
        EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockPiston.FACING);
        boolean flag = this.getNeighborSignal(world, blockposition, enumdirection);

        if (flag && !(Boolean) iblockdata.getValue(BlockPiston.EXTENDED)) {
            if ((new PistonExtendsChecker(world, blockposition, enumdirection, true)).resolve()) {
                world.blockEvent(blockposition, this, 0, enumdirection.get3DDataValue());
            }
        } else if (!flag && (Boolean) iblockdata.getValue(BlockPiston.EXTENDED)) {
            BlockPosition blockposition1 = blockposition.relative(enumdirection, 2);
            IBlockData iblockdata1 = world.getBlockState(blockposition1);
            int i = 1;

            if (iblockdata1.is(Blocks.MOVING_PISTON) && iblockdata1.getValue(BlockPiston.FACING) == enumdirection) {
                TileEntity tileentity = world.getBlockEntity(blockposition1);

                if (tileentity instanceof TileEntityPiston) {
                    TileEntityPiston tileentitypiston = (TileEntityPiston) tileentity;

                    if (tileentitypiston.isExtending() && (tileentitypiston.getProgress(0.0F) < 0.5F || world.getGameTime() == tileentitypiston.getLastTicked() || ((WorldServer) world).isHandlingTick())) {
                        i = 2;
                    }
                }
            }

            // CraftBukkit start
            if (!this.isSticky) {
                org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                BlockPistonRetractEvent event = new BlockPistonRetractEvent(block, ImmutableList.<org.bukkit.block.Block>of(), CraftBlock.notchToBlockFace(enumdirection));
                world.getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // PAIL: checkME - what happened to setTypeAndData?
            // CraftBukkit end
            world.blockEvent(blockposition, this, i, enumdirection.get3DDataValue());
        }

    }

    private boolean getNeighborSignal(SignalGetter signalgetter, BlockPosition blockposition, EnumDirection enumdirection) {
        for (EnumDirection enumdirection1 : EnumDirection.values()) {
            if (enumdirection1 != enumdirection && signalgetter.hasSignal(blockposition.relative(enumdirection1), enumdirection1)) {
                return true;
            }
        }

        if (signalgetter.hasSignal(blockposition, EnumDirection.DOWN)) {
            return true;
        } else {
            BlockPosition blockposition1 = blockposition.above();

            for (EnumDirection enumdirection2 : EnumDirection.values()) {
                if (enumdirection2 != EnumDirection.DOWN && signalgetter.hasSignal(blockposition1.relative(enumdirection2), enumdirection2)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    protected boolean triggerEvent(IBlockData iblockdata, World world, BlockPosition blockposition, int i, int j) {
        EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockPiston.FACING);
        IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockPiston.EXTENDED, true);

        if (!world.isClientSide) {
            boolean flag = this.getNeighborSignal(world, blockposition, enumdirection);

            if (flag && (i == 1 || i == 2)) {
                world.setBlock(blockposition, iblockdata1, 2);
                return false;
            }

            if (!flag && i == 0) {
                return false;
            }
        }

        if (i == 0) {
            if (!this.moveBlocks(world, blockposition, enumdirection, true)) {
                return false;
            }

            world.setBlock(blockposition, iblockdata1, 67);
            world.playSound((Entity) null, blockposition, SoundEffects.PISTON_EXTEND, SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.25F + 0.6F);
            world.gameEvent(GameEvent.BLOCK_ACTIVATE, blockposition, GameEvent.a.of(iblockdata1));
        } else if (i == 1 || i == 2) {
            TileEntity tileentity = world.getBlockEntity(blockposition.relative(enumdirection));

            if (tileentity instanceof TileEntityPiston) {
                ((TileEntityPiston) tileentity).finalTick();
            }

            IBlockData iblockdata2 = (IBlockData) ((IBlockData) Blocks.MOVING_PISTON.defaultBlockState().setValue(BlockPistonMoving.FACING, enumdirection)).setValue(BlockPistonMoving.TYPE, this.isSticky ? BlockPropertyPistonType.STICKY : BlockPropertyPistonType.DEFAULT);

            world.setBlock(blockposition, iblockdata2, 276);
            world.setBlockEntity(BlockPistonMoving.newMovingBlockEntity(blockposition, iblockdata2, (IBlockData) this.defaultBlockState().setValue(BlockPiston.FACING, EnumDirection.from3DDataValue(j & 7)), enumdirection, false, true));
            world.updateNeighborsAt(blockposition, iblockdata2.getBlock());
            iblockdata2.updateNeighbourShapes(world, blockposition, 2);
            if (this.isSticky) {
                BlockPosition blockposition1 = blockposition.offset(enumdirection.getStepX() * 2, enumdirection.getStepY() * 2, enumdirection.getStepZ() * 2);
                IBlockData iblockdata3 = world.getBlockState(blockposition1);
                boolean flag1 = false;

                if (iblockdata3.is(Blocks.MOVING_PISTON)) {
                    TileEntity tileentity1 = world.getBlockEntity(blockposition1);

                    if (tileentity1 instanceof TileEntityPiston) {
                        TileEntityPiston tileentitypiston = (TileEntityPiston) tileentity1;

                        if (tileentitypiston.getDirection() == enumdirection && tileentitypiston.isExtending()) {
                            tileentitypiston.finalTick();
                            flag1 = true;
                        }
                    }
                }

                if (!flag1) {
                    if (i != 1 || iblockdata3.isAir() || !isPushable(iblockdata3, world, blockposition1, enumdirection.getOpposite(), false, enumdirection) || iblockdata3.getPistonPushReaction() != EnumPistonReaction.NORMAL && !iblockdata3.is(Blocks.PISTON) && !iblockdata3.is(Blocks.STICKY_PISTON)) {
                        world.removeBlock(blockposition.relative(enumdirection), false);
                    } else {
                        this.moveBlocks(world, blockposition, enumdirection, false);
                    }
                }
            } else {
                world.removeBlock(blockposition.relative(enumdirection), false);
            }

            world.playSound((Entity) null, blockposition, SoundEffects.PISTON_CONTRACT, SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.15F + 0.6F);
            world.gameEvent(GameEvent.BLOCK_DEACTIVATE, blockposition, GameEvent.a.of(iblockdata2));
        }

        return true;
    }

    public static boolean isPushable(IBlockData iblockdata, World world, BlockPosition blockposition, EnumDirection enumdirection, boolean flag, EnumDirection enumdirection1) {
        if (blockposition.getY() >= world.getMinY() && blockposition.getY() <= world.getMaxY() && world.getWorldBorder().isWithinBounds(blockposition)) {
            if (iblockdata.isAir()) {
                return true;
            } else if (!iblockdata.is(Blocks.OBSIDIAN) && !iblockdata.is(Blocks.CRYING_OBSIDIAN) && !iblockdata.is(Blocks.RESPAWN_ANCHOR) && !iblockdata.is(Blocks.REINFORCED_DEEPSLATE)) {
                if (enumdirection == EnumDirection.DOWN && blockposition.getY() == world.getMinY()) {
                    return false;
                } else if (enumdirection == EnumDirection.UP && blockposition.getY() == world.getMaxY()) {
                    return false;
                } else {
                    if (!iblockdata.is(Blocks.PISTON) && !iblockdata.is(Blocks.STICKY_PISTON)) {
                        if (iblockdata.getDestroySpeed(world, blockposition) == -1.0F) {
                            return false;
                        }

                        switch (iblockdata.getPistonPushReaction()) {
                            case BLOCK:
                                return false;
                            case DESTROY:
                                return flag;
                            case PUSH_ONLY:
                                return enumdirection == enumdirection1;
                        }
                    } else if ((Boolean) iblockdata.getValue(BlockPiston.EXTENDED)) {
                        return false;
                    }

                    return !iblockdata.hasBlockEntity();
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean moveBlocks(World world, BlockPosition blockposition, EnumDirection enumdirection, boolean flag) {
        BlockPosition blockposition1 = blockposition.relative(enumdirection);

        if (!flag && world.getBlockState(blockposition1).is(Blocks.PISTON_HEAD)) {
            world.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 276);
        }

        PistonExtendsChecker pistonextendschecker = new PistonExtendsChecker(world, blockposition, enumdirection, flag);

        if (!pistonextendschecker.resolve()) {
            return false;
        } else {
            Map<BlockPosition, IBlockData> map = Maps.newHashMap();
            List<BlockPosition> list = pistonextendschecker.getToPush();
            List<IBlockData> list1 = Lists.newArrayList();

            for (BlockPosition blockposition2 : list) {
                IBlockData iblockdata = world.getBlockState(blockposition2);

                list1.add(iblockdata);
                map.put(blockposition2, iblockdata);
            }

            List<BlockPosition> list2 = pistonextendschecker.getToDestroy();
            IBlockData[] aiblockdata = new IBlockData[list.size() + list2.size()];
            EnumDirection enumdirection1 = flag ? enumdirection : enumdirection.getOpposite();
            int i = 0;
            // CraftBukkit start
            final org.bukkit.block.Block bblock = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

            final List<BlockPosition> moved = pistonextendschecker.getToPush();
            final List<BlockPosition> broken = pistonextendschecker.getToDestroy();

            List<org.bukkit.block.Block> blocks = new AbstractList<org.bukkit.block.Block>() {

                @Override
                public int size() {
                    return moved.size() + broken.size();
                }

                @Override
                public org.bukkit.block.Block get(int index) {
                    if (index >= size() || index < 0) {
                        throw new ArrayIndexOutOfBoundsException(index);
                    }
                    BlockPosition pos = (BlockPosition) (index < moved.size() ? moved.get(index) : broken.get(index - moved.size()));
                    return bblock.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                }
            };
            org.bukkit.event.block.BlockPistonEvent event;
            if (flag) {
                event = new BlockPistonExtendEvent(bblock, blocks, CraftBlock.notchToBlockFace(enumdirection1));
            } else {
                event = new BlockPistonRetractEvent(bblock, blocks, CraftBlock.notchToBlockFace(enumdirection1));
            }
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                for (BlockPosition b : broken) {
                    world.sendBlockUpdated(b, Blocks.AIR.defaultBlockState(), world.getBlockState(b), 3);
                }
                for (BlockPosition b : moved) {
                    world.sendBlockUpdated(b, Blocks.AIR.defaultBlockState(), world.getBlockState(b), 3);
                    b = b.relative(enumdirection1);
                    world.sendBlockUpdated(b, Blocks.AIR.defaultBlockState(), world.getBlockState(b), 3);
                }
                return false;
            }
            // CraftBukkit end

            for (int j = list2.size() - 1; j >= 0; --j) {
                BlockPosition blockposition3 = (BlockPosition) list2.get(j);
                IBlockData iblockdata1 = world.getBlockState(blockposition3);
                TileEntity tileentity = iblockdata1.hasBlockEntity() ? world.getBlockEntity(blockposition3) : null;

                dropResources(iblockdata1, world, blockposition3, tileentity);
                if (!iblockdata1.is(TagsBlock.FIRE) && world.isClientSide()) {
                    world.levelEvent(2001, blockposition3, getId(iblockdata1));
                }

                world.setBlock(blockposition3, Blocks.AIR.defaultBlockState(), 18);
                world.gameEvent(GameEvent.BLOCK_DESTROY, blockposition3, GameEvent.a.of(iblockdata1));
                aiblockdata[i++] = iblockdata1;
            }

            for (int k = list.size() - 1; k >= 0; --k) {
                BlockPosition blockposition4 = (BlockPosition) list.get(k);
                IBlockData iblockdata2 = world.getBlockState(blockposition4);

                blockposition4 = blockposition4.relative(enumdirection1);
                map.remove(blockposition4);
                IBlockData iblockdata3 = (IBlockData) Blocks.MOVING_PISTON.defaultBlockState().setValue(BlockPiston.FACING, enumdirection);

                world.setBlock(blockposition4, iblockdata3, 324);
                world.setBlockEntity(BlockPistonMoving.newMovingBlockEntity(blockposition4, iblockdata3, (IBlockData) list1.get(k), enumdirection, flag, false));
                aiblockdata[i++] = iblockdata2;
            }

            if (flag) {
                BlockPropertyPistonType blockpropertypistontype = this.isSticky ? BlockPropertyPistonType.STICKY : BlockPropertyPistonType.DEFAULT;
                IBlockData iblockdata4 = (IBlockData) ((IBlockData) Blocks.PISTON_HEAD.defaultBlockState().setValue(BlockPistonExtension.FACING, enumdirection)).setValue(BlockPistonExtension.TYPE, blockpropertypistontype);
                IBlockData iblockdata5 = (IBlockData) ((IBlockData) Blocks.MOVING_PISTON.defaultBlockState().setValue(BlockPistonMoving.FACING, enumdirection)).setValue(BlockPistonMoving.TYPE, this.isSticky ? BlockPropertyPistonType.STICKY : BlockPropertyPistonType.DEFAULT);

                map.remove(blockposition1);
                world.setBlock(blockposition1, iblockdata5, 324);
                world.setBlockEntity(BlockPistonMoving.newMovingBlockEntity(blockposition1, iblockdata5, iblockdata4, enumdirection, true, true));
            }

            IBlockData iblockdata6 = Blocks.AIR.defaultBlockState();

            for (BlockPosition blockposition5 : map.keySet()) {
                world.setBlock(blockposition5, iblockdata6, 82);
            }

            for (Map.Entry<BlockPosition, IBlockData> map_entry : map.entrySet()) {
                BlockPosition blockposition6 = (BlockPosition) map_entry.getKey();
                IBlockData iblockdata7 = (IBlockData) map_entry.getValue();

                iblockdata7.updateIndirectNeighbourShapes(world, blockposition6, 2);
                iblockdata6.updateNeighbourShapes(world, blockposition6, 2);
                iblockdata6.updateIndirectNeighbourShapes(world, blockposition6, 2);
            }

            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(world, pistonextendschecker.getPushDirection(), (EnumDirection) null);

            i = 0;

            for (int l = list2.size() - 1; l >= 0; --l) {
                IBlockData iblockdata8 = aiblockdata[i++];
                BlockPosition blockposition7 = (BlockPosition) list2.get(l);

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    iblockdata8.affectNeighborsAfterRemoval(worldserver, blockposition7, false);
                }

                iblockdata8.updateIndirectNeighbourShapes(world, blockposition7, 2);
                world.updateNeighborsAt(blockposition7, iblockdata8.getBlock(), orientation);
            }

            for (int i1 = list.size() - 1; i1 >= 0; --i1) {
                world.updateNeighborsAt((BlockPosition) list.get(i1), aiblockdata[i++].getBlock(), orientation);
            }

            if (flag) {
                world.updateNeighborsAt(blockposition1, Blocks.PISTON_HEAD, orientation);
            }

            return true;
        }
    }

    @Override
    protected IBlockData rotate(IBlockData iblockdata, EnumBlockRotation enumblockrotation) {
        return (IBlockData) iblockdata.setValue(BlockPiston.FACING, enumblockrotation.rotate((EnumDirection) iblockdata.getValue(BlockPiston.FACING)));
    }

    @Override
    protected IBlockData mirror(IBlockData iblockdata, EnumBlockMirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((EnumDirection) iblockdata.getValue(BlockPiston.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockPiston.FACING, BlockPiston.EXTENDED);
    }

    @Override
    protected boolean useShapeForLightOcclusion(IBlockData iblockdata) {
        return (Boolean) iblockdata.getValue(BlockPiston.EXTENDED);
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return false;
    }
}
