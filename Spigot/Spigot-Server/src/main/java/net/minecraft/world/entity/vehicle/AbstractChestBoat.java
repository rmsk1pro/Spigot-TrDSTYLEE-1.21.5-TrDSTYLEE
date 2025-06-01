package net.minecraft.world.entity.vehicle;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.InventoryUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.piglin.PiglinAI;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;

// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public abstract class AbstractChestBoat extends AbstractBoat implements HasCustomInventoryScreen, ContainerEntity {

    private static final int CONTAINER_SIZE = 27;
    private NonNullList<ItemStack> itemStacks;
    @Nullable
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    public AbstractChestBoat(EntityTypes<? extends AbstractChestBoat> entitytypes, World world, Supplier<Item> supplier) {
        super(entitytypes, world, supplier);
        this.itemStacks = NonNullList.<ItemStack>withSize(27, ItemStack.EMPTY);
    }

    @Override
    protected float getSinglePassengerXOffset() {
        return 0.15F;
    }

    @Override
    protected int getMaxPassengers() {
        return 1;
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        this.addChestVehicleSaveData(nbttagcompound, this.registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.readChestVehicleSaveData(nbttagcompound, this.registryAccess());
    }

    @Override
    public void destroy(WorldServer worldserver, DamageSource damagesource) {
        this.destroy(worldserver, this.getDropItem());
        this.chestVehicleDestroyed(damagesource, worldserver, this);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(entity_removalreason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        // CraftBukkit end
        if (!this.level().isClientSide && entity_removalreason.shouldDestroy()) {
            InventoryUtils.dropContents(this.level(), (Entity) this, this);
        }

        super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public EnumInteractionResult interact(EntityHuman entityhuman, EnumHand enumhand) {
        if (!entityhuman.isSecondaryUseActive()) {
            EnumInteractionResult enuminteractionresult = super.interact(entityhuman, enumhand);

            if (enuminteractionresult != EnumInteractionResult.PASS) {
                return enuminteractionresult;
            }
        }

        if (this.canAddPassenger(entityhuman) && !entityhuman.isSecondaryUseActive()) {
            return EnumInteractionResult.PASS;
        } else {
            EnumInteractionResult enuminteractionresult1 = this.interactWithContainerVehicle(entityhuman);

            if (enuminteractionresult1.consumesAction()) {
                World world = entityhuman.level();

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    this.gameEvent(GameEvent.CONTAINER_OPEN, entityhuman);
                    PiglinAI.angerNearbyPiglins(worldserver, entityhuman, true);
                }
            }

            return enuminteractionresult1;
        }
    }

    @Override
    public void openCustomInventoryScreen(EntityHuman entityhuman) {
        entityhuman.openMenu(this);
        World world = entityhuman.level();

        if (world instanceof WorldServer worldserver) {
            this.gameEvent(GameEvent.CONTAINER_OPEN, entityhuman);
            PiglinAI.angerNearbyPiglins(worldserver, entityhuman, true);
        }

    }

    @Override
    public void clearContent() {
        this.clearChestVehicleContent();
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public ItemStack getItem(int i) {
        return this.getChestVehicleItem(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        return this.removeChestVehicleItem(i, j);
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return this.removeChestVehicleItemNoUpdate(i);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.setChestVehicleItem(i, itemstack);
    }

    @Override
    public SlotAccess getSlot(int i) {
        return this.getChestVehicleSlot(i);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(EntityHuman entityhuman) {
        return this.isChestVehicleStillValid(entityhuman);
    }

    @Nullable
    @Override
    public Container createMenu(int i, PlayerInventory playerinventory, EntityHuman entityhuman) {
        if (this.lootTable != null && entityhuman.isSpectator()) {
            return null;
        } else {
            this.unpackLootTable(playerinventory.player);
            return ContainerChest.threeRows(i, playerinventory, this);
        }
    }

    public void unpackLootTable(@Nullable EntityHuman entityhuman) {
        this.unpackChestVehicleLootTable(entityhuman);
    }

    @Nullable
    @Override
    public ResourceKey<LootTable> getContainerLootTable() {
        return this.lootTable;
    }

    @Override
    public void setContainerLootTable(@Nullable ResourceKey<LootTable> resourcekey) {
        this.lootTable = resourcekey;
    }

    @Override
    public long getContainerLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setContainerLootTableSeed(long i) {
        this.lootTableSeed = i;
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return this.itemStacks;
    }

    @Override
    public void clearItemStacks() {
        this.itemStacks = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public void stopOpen(EntityHuman entityhuman) {
        this.level().gameEvent(GameEvent.CONTAINER_CLOSE, this.position(), GameEvent.a.of((Entity) entityhuman));
    }

    // CraftBukkit start
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return this.itemStacks;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public InventoryHolder getOwner() {
        org.bukkit.entity.Entity entity = getBukkitEntity();
        if (entity instanceof InventoryHolder) return (InventoryHolder) entity;
        return null;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        maxStack = size;
    }

    @Override
    public Location getLocation() {
        return getBukkitEntity().getLocation();
    }
    // CraftBukkit end
}
