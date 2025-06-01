package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.ChestLock;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.IInventory;
import net.minecraft.world.INamableTileEntity;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.IBlockData;

public abstract class TileEntityContainer extends TileEntity implements IInventory, ITileInventory, INamableTileEntity {

    public ChestLock lockKey;
    @Nullable
    public IChatBaseComponent name;

    protected TileEntityContainer(TileEntityTypes<?> tileentitytypes, BlockPosition blockposition, IBlockData iblockdata) {
        super(tileentitytypes, blockposition, iblockdata);
        this.lockKey = ChestLock.NO_LOCK;
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.loadAdditional(nbttagcompound, holderlookup_a);
        this.lockKey = ChestLock.fromTag(nbttagcompound, holderlookup_a);
        this.name = parseCustomNameSafe(nbttagcompound.get("CustomName"), holderlookup_a);
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.saveAdditional(nbttagcompound, holderlookup_a);
        this.lockKey.addToTag(nbttagcompound, holderlookup_a);
        if (this.name != null) {
            nbttagcompound.put("CustomName", (NBTBase) ComponentSerialization.CODEC.encodeStart(holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), this.name).getOrThrow());
        }

    }

    @Override
    public IChatBaseComponent getName() {
        return this.name != null ? this.name : this.getDefaultName();
    }

    @Override
    public IChatBaseComponent getDisplayName() {
        return this.getName();
    }

    @Nullable
    @Override
    public IChatBaseComponent getCustomName() {
        return this.name;
    }

    protected abstract IChatBaseComponent getDefaultName();

    public boolean canOpen(EntityHuman entityhuman) {
        return canUnlock(entityhuman, this.lockKey, this.getDisplayName());
    }

    public static boolean canUnlock(EntityHuman entityhuman, ChestLock chestlock, IChatBaseComponent ichatbasecomponent) {
        if (!entityhuman.isSpectator() && !chestlock.unlocksWith(entityhuman.getMainHandItem())) {
            entityhuman.displayClientMessage(IChatBaseComponent.translatable("container.isLocked", ichatbasecomponent), true);
            entityhuman.playNotifySound(SoundEffects.CHEST_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
            return false;
        } else {
            return true;
        }
    }

    protected abstract NonNullList<ItemStack> getItems();

    protected abstract void setItems(NonNullList<ItemStack> nonnulllist);

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.getItems()) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return (ItemStack) this.getItems().get(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        ItemStack itemstack = ContainerUtil.removeItem(this.getItems(), i, j);

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerUtil.takeItem(this.getItems(), i);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.getItems().set(i, itemstack);
        itemstack.limitSize(this.getMaxStackSize(itemstack));
        this.setChanged();
    }

    @Override
    public boolean stillValid(EntityHuman entityhuman) {
        return IInventory.stillValidBlockEntity(this, entityhuman);
    }

    @Override
    public void clearContent() {
        this.getItems().clear();
    }

    @Nullable
    @Override
    public Container createMenu(int i, PlayerInventory playerinventory, EntityHuman entityhuman) {
        return this.canOpen(entityhuman) ? this.createMenu(i, playerinventory) : null;
    }

    protected abstract Container createMenu(int i, PlayerInventory playerinventory);

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        super.applyImplicitComponents(datacomponentgetter);
        this.name = (IChatBaseComponent) datacomponentgetter.get(DataComponents.CUSTOM_NAME);
        this.lockKey = (ChestLock) datacomponentgetter.getOrDefault(DataComponents.LOCK, ChestLock.NO_LOCK);
        ((ItemContainerContents) datacomponentgetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {
        super.collectImplicitComponents(datacomponentmap_a);
        datacomponentmap_a.set(DataComponents.CUSTOM_NAME, this.name);
        if (!this.lockKey.equals(ChestLock.NO_LOCK)) {
            datacomponentmap_a.set(DataComponents.LOCK, this.lockKey);
        }

        datacomponentmap_a.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(NBTTagCompound nbttagcompound) {
        nbttagcompound.remove("CustomName");
        nbttagcompound.remove("lock");
        nbttagcompound.remove("Items");
    }

    // CraftBukkit start
    @Override
    public org.bukkit.Location getLocation() {
        if (level == null) return null;
        return new org.bukkit.Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }
    // CraftBukkit end
}
