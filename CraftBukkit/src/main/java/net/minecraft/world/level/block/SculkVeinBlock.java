package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Collection;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidTypes;

public class SculkVeinBlock extends MultifaceSpreadeableBlock implements SculkBehaviour {

    public static final MapCodec<SculkVeinBlock> CODEC = simpleCodec(SculkVeinBlock::new);
    private final MultifaceSpreader veinSpreader;
    private final MultifaceSpreader sameSpaceSpreader;

    @Override
    public MapCodec<SculkVeinBlock> codec() {
        return SculkVeinBlock.CODEC;
    }

    public SculkVeinBlock(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.veinSpreader = new MultifaceSpreader(new SculkVeinBlock.a(MultifaceSpreader.DEFAULT_SPREAD_ORDER));
        this.sameSpaceSpreader = new MultifaceSpreader(new SculkVeinBlock.a(new MultifaceSpreader.e[]{MultifaceSpreader.e.SAME_POSITION}));
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.veinSpreader;
    }

    public MultifaceSpreader getSameSpaceSpreader() {
        return this.sameSpaceSpreader;
    }

    public static boolean regrow(GeneratorAccess generatoraccess, BlockPosition blockposition, IBlockData iblockdata, Collection<EnumDirection> collection) {
        boolean flag = false;
        IBlockData iblockdata1 = Blocks.SCULK_VEIN.defaultBlockState();

        for (EnumDirection enumdirection : collection) {
            if (canAttachTo(generatoraccess, blockposition, enumdirection)) {
                iblockdata1 = (IBlockData) iblockdata1.setValue(getFaceProperty(enumdirection), true);
                flag = true;
            }
        }

        if (!flag) {
            return false;
        } else {
            if (!iblockdata.getFluidState().isEmpty()) {
                iblockdata1 = (IBlockData) iblockdata1.setValue(MultifaceBlock.WATERLOGGED, true);
            }

            generatoraccess.setBlock(blockposition, iblockdata1, 3);
            return true;
        }
    }

    @Override
    public void onDischarged(GeneratorAccess generatoraccess, IBlockData iblockdata, BlockPosition blockposition, RandomSource randomsource) {
        if (iblockdata.is(this)) {
            for (EnumDirection enumdirection : SculkVeinBlock.DIRECTIONS) {
                BlockStateBoolean blockstateboolean = getFaceProperty(enumdirection);

                if ((Boolean) iblockdata.getValue(blockstateboolean) && generatoraccess.getBlockState(blockposition.relative(enumdirection)).is(Blocks.SCULK)) {
                    iblockdata = (IBlockData) iblockdata.setValue(blockstateboolean, false);
                }
            }

            if (!hasAnyFace(iblockdata)) {
                Fluid fluid = generatoraccess.getFluidState(blockposition);

                iblockdata = (fluid.isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState();
            }

            generatoraccess.setBlock(blockposition, iblockdata, 3);
            SculkBehaviour.super.onDischarged(generatoraccess, iblockdata, blockposition, randomsource);
        }
    }

    @Override
    public int attemptUseCharge(SculkSpreader.a sculkspreader_a, GeneratorAccess generatoraccess, BlockPosition blockposition, RandomSource randomsource, SculkSpreader sculkspreader, boolean flag) {
        // CraftBukkit - add source block
        return flag && this.attemptPlaceSculk(sculkspreader, generatoraccess, sculkspreader_a.getPos(), randomsource, blockposition) ? sculkspreader_a.getCharge() - 1 : (randomsource.nextInt(sculkspreader.chargeDecayRate()) == 0 ? MathHelper.floor((float) sculkspreader_a.getCharge() * 0.5F) : sculkspreader_a.getCharge());
    }

    private boolean attemptPlaceSculk(SculkSpreader sculkspreader, GeneratorAccess generatoraccess, BlockPosition blockposition, RandomSource randomsource, BlockPosition sourceBlock) { // CraftBukkit
        IBlockData iblockdata = generatoraccess.getBlockState(blockposition);
        TagKey<Block> tagkey = sculkspreader.replaceableBlocks();

        for (EnumDirection enumdirection : EnumDirection.allShuffled(randomsource)) {
            if (hasFace(iblockdata, enumdirection)) {
                BlockPosition blockposition1 = blockposition.relative(enumdirection);
                IBlockData iblockdata1 = generatoraccess.getBlockState(blockposition1);

                if (iblockdata1.is(tagkey)) {
                    IBlockData iblockdata2 = Blocks.SCULK.defaultBlockState();

                    // CraftBukkit start - Call BlockSpreadEvent
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(generatoraccess, sourceBlock, blockposition1, iblockdata2, 3)) {
                        return false;
                    }
                    // CraftBukkit end
                    Block.pushEntitiesUp(iblockdata1, iblockdata2, generatoraccess, blockposition1);
                    generatoraccess.playSound((Entity) null, blockposition1, SoundEffects.SCULK_BLOCK_SPREAD, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    this.veinSpreader.spreadAll(iblockdata2, generatoraccess, blockposition1, sculkspreader.isWorldGeneration());
                    EnumDirection enumdirection1 = enumdirection.getOpposite();

                    for (EnumDirection enumdirection2 : SculkVeinBlock.DIRECTIONS) {
                        if (enumdirection2 != enumdirection1) {
                            BlockPosition blockposition2 = blockposition1.relative(enumdirection2);
                            IBlockData iblockdata3 = generatoraccess.getBlockState(blockposition2);

                            if (iblockdata3.is(this)) {
                                this.onDischarged(generatoraccess, iblockdata3, blockposition2, randomsource);
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasSubstrateAccess(GeneratorAccess generatoraccess, IBlockData iblockdata, BlockPosition blockposition) {
        if (!iblockdata.is(Blocks.SCULK_VEIN)) {
            return false;
        } else {
            for (EnumDirection enumdirection : SculkVeinBlock.DIRECTIONS) {
                if (hasFace(iblockdata, enumdirection) && generatoraccess.getBlockState(blockposition.relative(enumdirection)).is(TagsBlock.SCULK_REPLACEABLE)) {
                    return true;
                }
            }

            return false;
        }
    }

    private class a extends MultifaceSpreader.a {

        private final MultifaceSpreader.e[] spreadTypes;

        public a(final MultifaceSpreader.e... amultifacespreader_e) {
            super(SculkVeinBlock.this);
            this.spreadTypes = amultifacespreader_e;
        }

        @Override
        public boolean stateCanBeReplaced(IBlockAccess iblockaccess, BlockPosition blockposition, BlockPosition blockposition1, EnumDirection enumdirection, IBlockData iblockdata) {
            IBlockData iblockdata1 = iblockaccess.getBlockState(blockposition1.relative(enumdirection));

            if (!iblockdata1.is(Blocks.SCULK) && !iblockdata1.is(Blocks.SCULK_CATALYST) && !iblockdata1.is(Blocks.MOVING_PISTON)) {
                if (blockposition.distManhattan(blockposition1) == 2) {
                    BlockPosition blockposition2 = blockposition.relative(enumdirection.getOpposite());

                    if (iblockaccess.getBlockState(blockposition2).isFaceSturdy(iblockaccess, blockposition2, enumdirection)) {
                        return false;
                    }
                }

                Fluid fluid = iblockdata.getFluidState();

                return !fluid.isEmpty() && !fluid.is(FluidTypes.WATER) ? false : (iblockdata.is(TagsBlock.FIRE) ? false : iblockdata.canBeReplaced() || super.stateCanBeReplaced(iblockaccess, blockposition, blockposition1, enumdirection, iblockdata));
            } else {
                return false;
            }
        }

        @Override
        public MultifaceSpreader.e[] getSpreadTypes() {
            return this.spreadTypes;
        }

        @Override
        public boolean isOtherBlockValidAsSource(IBlockData iblockdata) {
            return !iblockdata.is(Blocks.SCULK_VEIN);
        }
    }
}
