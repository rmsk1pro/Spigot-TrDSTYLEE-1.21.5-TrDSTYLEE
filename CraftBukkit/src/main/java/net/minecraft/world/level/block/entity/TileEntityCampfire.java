package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.InventoryUtils;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.item.crafting.RecipeCampfire;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipes;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockCampfire;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.inventory.CampfireRecipe;
// CraftBukkit end

public class TileEntityCampfire extends TileEntity implements Clearable {

    private static final int BURN_COOL_SPEED = 2;
    private static final int NUM_SLOTS = 4;
    private final NonNullList<ItemStack> items;
    public final int[] cookingProgress;
    public final int[] cookingTime;

    public TileEntityCampfire(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.CAMPFIRE, blockposition, iblockdata);
        this.items = NonNullList.<ItemStack>withSize(4, ItemStack.EMPTY);
        this.cookingProgress = new int[4];
        this.cookingTime = new int[4];
    }

    public static void cookTick(WorldServer worldserver, BlockPosition blockposition, IBlockData iblockdata, TileEntityCampfire tileentitycampfire, CraftingManager.a<SingleRecipeInput, RecipeCampfire> craftingmanager_a) {
        boolean flag = false;

        for (int i = 0; i < tileentitycampfire.items.size(); ++i) {
            ItemStack itemstack = tileentitycampfire.items.get(i);

            if (!itemstack.isEmpty()) {
                flag = true;
                int j = tileentitycampfire.cookingProgress[i]++;

                if (tileentitycampfire.cookingProgress[i] >= tileentitycampfire.cookingTime[i]) {
                    SingleRecipeInput singlerecipeinput = new SingleRecipeInput(itemstack);
                    ItemStack itemstack1 = (ItemStack) craftingmanager_a.getRecipeFor(singlerecipeinput, worldserver).map((recipeholder) -> {
                        return ((RecipeCampfire) recipeholder.value()).assemble(singlerecipeinput, worldserver.registryAccess());
                    }).orElse(itemstack);

                    if (itemstack1.isItemEnabled(worldserver.enabledFeatures())) {
                        // CraftBukkit start - fire BlockCookEvent
                        CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
                        org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);

                        BlockCookEvent blockCookEvent = new BlockCookEvent(CraftBlock.at(worldserver, blockposition), source, result);
                        worldserver.getCraftServer().getPluginManager().callEvent(blockCookEvent);

                        if (blockCookEvent.isCancelled()) {
                            return;
                        }

                        result = blockCookEvent.getResult();
                        itemstack1 = CraftItemStack.asNMSCopy(result);
                        // CraftBukkit end
                        InventoryUtils.dropItemStack(worldserver, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), itemstack1);
                        tileentitycampfire.items.set(i, ItemStack.EMPTY);
                        worldserver.sendBlockUpdated(blockposition, iblockdata, iblockdata, 3);
                        worldserver.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(iblockdata));
                    }
                }
            }
        }

        if (flag) {
            setChanged(worldserver, blockposition, iblockdata);
        }

    }

    public static void cooldownTick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityCampfire tileentitycampfire) {
        boolean flag = false;

        for (int i = 0; i < tileentitycampfire.items.size(); ++i) {
            if (tileentitycampfire.cookingProgress[i] > 0) {
                flag = true;
                tileentitycampfire.cookingProgress[i] = MathHelper.clamp(tileentitycampfire.cookingProgress[i] - 2, 0, tileentitycampfire.cookingTime[i]);
            }
        }

        if (flag) {
            setChanged(world, blockposition, iblockdata);
        }

    }

    public static void particleTick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityCampfire tileentitycampfire) {
        RandomSource randomsource = world.random;

        if (randomsource.nextFloat() < 0.11F) {
            for (int i = 0; i < randomsource.nextInt(2) + 2; ++i) {
                BlockCampfire.makeParticles(world, blockposition, (Boolean) iblockdata.getValue(BlockCampfire.SIGNAL_FIRE), false);
            }
        }

        int j = ((EnumDirection) iblockdata.getValue(BlockCampfire.FACING)).get2DDataValue();

        for (int k = 0; k < tileentitycampfire.items.size(); ++k) {
            if (!((ItemStack) tileentitycampfire.items.get(k)).isEmpty() && randomsource.nextFloat() < 0.2F) {
                EnumDirection enumdirection = EnumDirection.from2DDataValue(Math.floorMod(k + j, 4));
                float f = 0.3125F;
                double d0 = (double) blockposition.getX() + 0.5D - (double) ((float) enumdirection.getStepX() * 0.3125F) + (double) ((float) enumdirection.getClockWise().getStepX() * 0.3125F);
                double d1 = (double) blockposition.getY() + 0.5D;
                double d2 = (double) blockposition.getZ() + 0.5D - (double) ((float) enumdirection.getStepZ() * 0.3125F) + (double) ((float) enumdirection.getClockWise().getStepZ() * 0.3125F);

                for (int l = 0; l < 4; ++l) {
                    world.addParticle(Particles.SMOKE, d0, d1, d2, 0.0D, 5.0E-4D, 0.0D);
                }
            }
        }

    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.loadAdditional(nbttagcompound, holderlookup_a);
        this.items.clear();
        ContainerUtil.loadAllItems(nbttagcompound, this.items, holderlookup_a);
        nbttagcompound.getIntArray("CookingTimes").ifPresentOrElse((aint) -> {
            System.arraycopy(aint, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, aint.length));
        }, () -> {
            Arrays.fill(this.cookingProgress, 0);
        });
        nbttagcompound.getIntArray("CookingTotalTimes").ifPresentOrElse((aint) -> {
            System.arraycopy(aint, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, aint.length));
        }, () -> {
            Arrays.fill(this.cookingTime, 0);
        });
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.saveAdditional(nbttagcompound, holderlookup_a);
        ContainerUtil.saveAllItems(nbttagcompound, this.items, true, holderlookup_a);
        nbttagcompound.putIntArray("CookingTimes", this.cookingProgress);
        nbttagcompound.putIntArray("CookingTotalTimes", this.cookingTime);
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        ContainerUtil.saveAllItems(nbttagcompound, this.items, true, holderlookup_a);
        return nbttagcompound;
    }

    public boolean placeFood(WorldServer worldserver, @Nullable EntityLiving entityliving, ItemStack itemstack) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.items.get(i);

            if (itemstack1.isEmpty()) {
                Optional<RecipeHolder<RecipeCampfire>> optional = worldserver.recipeAccess().getRecipeFor(Recipes.CAMPFIRE_COOKING, new SingleRecipeInput(itemstack), worldserver);

                if (optional.isEmpty()) {
                    return false;
                }

                // CraftBukkit start
                CampfireStartEvent event = new CampfireStartEvent(CraftBlock.at(this.level,this.worldPosition), CraftItemStack.asCraftMirror(itemstack), (CampfireRecipe) optional.get().toBukkitRecipe());
                this.level.getCraftServer().getPluginManager().callEvent(event);
                this.cookingTime[i] = event.getTotalCookTime(); // i -> event.getTotalCookTime()
                // CraftBukkit end
                this.cookingProgress[i] = 0;
                this.items.set(i, itemstack.consumeAndReturn(1, entityliving));
                worldserver.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.a.of(entityliving, this.getBlockState()));
                this.markUpdated();
                return true;
            }
        }

        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPosition blockposition, IBlockData iblockdata) {
        if (this.level != null) {
            InventoryUtils.dropContents(this.level, blockposition, this.getItems());
        }

    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        super.applyImplicitComponents(datacomponentgetter);
        ((ItemContainerContents) datacomponentgetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {
        super.collectImplicitComponents(datacomponentmap_a);
        datacomponentmap_a.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(NBTTagCompound nbttagcompound) {
        nbttagcompound.remove("Items");
    }
}
