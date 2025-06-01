package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.item.EntityTNTPrimed;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.MovingObjectPositionBlock;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.TNTPrimeEvent.PrimeCause;
// CraftBukkit end

public class BlockTNT extends Block {

    public static final MapCodec<BlockTNT> CODEC = simpleCodec(BlockTNT::new);
    public static final BlockStateBoolean UNSTABLE = BlockProperties.UNSTABLE;

    @Override
    public MapCodec<BlockTNT> codec() {
        return BlockTNT.CODEC;
    }

    public BlockTNT(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) this.defaultBlockState().setValue(BlockTNT.UNSTABLE, false));
    }

    @Override
    protected void onPlace(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            if (world.hasNeighborSignal(blockposition) && CraftEventFactory.callTNTPrimeEvent(world, blockposition, PrimeCause.REDSTONE, null, null) && prime(world, blockposition)) { // CraftBukkit - TNTPrimeEvent
                world.removeBlock(blockposition, false);
            }

        }
    }

    @Override
    protected void neighborChanged(IBlockData iblockdata, World world, BlockPosition blockposition, Block block, @Nullable Orientation orientation, boolean flag) {
        if (world.hasNeighborSignal(blockposition) && CraftEventFactory.callTNTPrimeEvent(world, blockposition, PrimeCause.REDSTONE, null, null) && prime(world, blockposition)) { // CraftBukkit - TNTPrimeEvent
            world.removeBlock(blockposition, false);
        }

    }

    @Override
    public IBlockData playerWillDestroy(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman) {
        if (!world.isClientSide() && !entityhuman.getAbilities().instabuild && (Boolean) iblockdata.getValue(BlockTNT.UNSTABLE) && CraftEventFactory.callTNTPrimeEvent(world, blockposition, PrimeCause.BLOCK_BREAK, entityhuman, null)) { // CraftBukkit - TNTPrimeEvent
            prime(world, blockposition);
        }

        return super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    @Override
    public void wasExploded(WorldServer worldserver, BlockPosition blockposition, Explosion explosion) {
        if (worldserver.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLODES)) {
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(worldserver, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, explosion.getIndirectSourceEntity());
            int i = entitytntprimed.getFuse();

            entitytntprimed.setFuse((short) (worldserver.random.nextInt(i / 4) + i / 8));
            worldserver.addFreshEntity(entitytntprimed);
        }
    }

    public static boolean prime(World world, BlockPosition blockposition) {
        return prime(world, blockposition, (EntityLiving) null);
    }

    private static boolean prime(World world, BlockPosition blockposition, @Nullable EntityLiving entityliving) {
        if (world instanceof WorldServer worldserver) {
            if (worldserver.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLODES)) {
                EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, entityliving);

                world.addFreshEntity(entitytntprimed);
                world.playSound((Entity) null, entitytntprimed.getX(), entitytntprimed.getY(), entitytntprimed.getZ(), SoundEffects.TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.gameEvent(entityliving, (Holder) GameEvent.PRIME_FUSE, blockposition);
                return true;
            }
        }

        return false;
    }

    @Override
    protected EnumInteractionResult useItemOn(ItemStack itemstack, IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, MovingObjectPositionBlock movingobjectpositionblock) {
        if (!itemstack.is(Items.FLINT_AND_STEEL) && !itemstack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(itemstack, iblockdata, world, blockposition, entityhuman, enumhand, movingobjectpositionblock);
        } else {
            // CraftBukkit start - TNTPrimeEvent
            if (!CraftEventFactory.callTNTPrimeEvent(world, blockposition, PrimeCause.PLAYER, entityhuman, null)) {
                return EnumInteractionResult.CONSUME;
            }
            // CraftBukkit end
            if (prime(world, blockposition, entityhuman)) {
                world.setBlock(blockposition, Blocks.AIR.defaultBlockState(), 11);
                Item item = itemstack.getItem();

                if (itemstack.is(Items.FLINT_AND_STEEL)) {
                    itemstack.hurtAndBreak(1, entityhuman, EntityLiving.getSlotForHand(enumhand));
                } else {
                    itemstack.consume(1, entityhuman);
                }

                entityhuman.awardStat(StatisticList.ITEM_USED.get(item));
            } else if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (!worldserver.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLODES)) {
                    entityhuman.displayClientMessage(IChatBaseComponent.translatable("block.minecraft.tnt.disabled"), true);
                    return EnumInteractionResult.PASS;
                }
            }

            return EnumInteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onProjectileHit(World world, IBlockData iblockdata, MovingObjectPositionBlock movingobjectpositionblock, IProjectile iprojectile) {
        if (world instanceof WorldServer worldserver) {
            BlockPosition blockposition = movingobjectpositionblock.getBlockPos();
            Entity entity = iprojectile.getOwner();

            // CraftBukkit - TNTPrimeEvent
            if (iprojectile.isOnFire() && iprojectile.mayInteract(worldserver, blockposition) && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(iprojectile, blockposition, Blocks.AIR.defaultBlockState()) && CraftEventFactory.callTNTPrimeEvent(world, blockposition, PrimeCause.PROJECTILE, iprojectile, null) && prime(world, blockposition, entity instanceof EntityLiving ? (EntityLiving) entity : null)) {
                world.removeBlock(blockposition, false);
            }
        }

    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockTNT.UNSTABLE);
    }
}
