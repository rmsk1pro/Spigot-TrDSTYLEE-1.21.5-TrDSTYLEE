package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.EnumChatFormat;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatComponentUtils;
import net.minecraft.network.chat.ChatHoverable;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatMutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.MathHelper;
import net.minecraft.util.NullOps;
import net.minecraft.util.Unit;
import net.minecraft.util.UtilColor;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeBase;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.ItemActionContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.IMaterial;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.pattern.ShapeDetectorBlock;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.EnumDirection;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.world.level.block.BlockBed;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BlockSapling;
import net.minecraft.world.level.block.BlockSign;
import net.minecraft.world.level.block.BlockTileEntity;
import net.minecraft.world.level.block.BlockWitherSkull;
import net.minecraft.world.level.block.SoundEffectType;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityJukeBox;
import net.minecraft.world.level.block.entity.TileEntitySign;
import net.minecraft.world.level.block.entity.TileEntitySkull;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public final class ItemStack implements DataComponentHolder {

    private static final List<IChatBaseComponent> OP_NBT_WARNING = List.of(IChatBaseComponent.translatable("item.op_warning.line1").withStyle(EnumChatFormat.RED, EnumChatFormat.BOLD), IChatBaseComponent.translatable("item.op_warning.line2").withStyle(EnumChatFormat.RED), IChatBaseComponent.translatable("item.op_warning.line3").withStyle(EnumChatFormat.RED));
    private static final IChatBaseComponent UNBREAKABLE_TOOLTIP = IChatBaseComponent.translatable("item.unbreakable").withStyle(EnumChatFormat.BLUE);
    public static final MapCodec<ItemStack> MAP_CODEC = MapCodec.recursive("ItemStack", (codec) -> {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder), ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount), DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemstack) -> {
                return itemstack.components.asPatch();
            })).apply(instance, ItemStack::new);
        });
    });
    public static final Codec<ItemStack> CODEC;
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC;
    public static final Codec<ItemStack> STRICT_CODEC;
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC;
    public static final Codec<ItemStack> OPTIONAL_CODEC;
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC;
    private static final Logger LOGGER;
    public static final ItemStack EMPTY;
    private static final IChatBaseComponent DISABLED_ITEM_TOOLTIP;
    private int count;
    private int popTime;
    /** @deprecated */
    @Deprecated
    @Nullable
    private Item item;
    final PatchedDataComponentMap components;
    @Nullable
    private Entity entityRepresentation;

    public static DataResult<ItemStack> validateStrict(ItemStack itemstack) {
        DataResult<Unit> dataresult = validateComponents(itemstack.getComponents());

        return dataresult.isError() ? dataresult.map((unit) -> {
            return itemstack;
        }) : (itemstack.getCount() > itemstack.getMaxStackSize() ? DataResult.<ItemStack>error(() -> { // CraftBukkit - decompile error
            int i = itemstack.getCount();

            return "Item stack with stack size of " + i + " was larger than maximum: " + itemstack.getMaxStackSize();
        }) : DataResult.success(itemstack));
    }

    private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> createOptionalStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> streamcodec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
                int i = registryfriendlybytebuf.readVarInt();

                if (i <= 0) {
                    return ItemStack.EMPTY;
                } else {
                    Holder<Item> holder = (Holder) Item.STREAM_CODEC.decode(registryfriendlybytebuf);
                    DataComponentPatch datacomponentpatch = (DataComponentPatch) streamcodec.decode(registryfriendlybytebuf);

                    // CraftBukkit start
                    ItemStack itemstack = new ItemStack(holder, i, datacomponentpatch);
                    if (!datacomponentpatch.isEmpty()) {
                        CraftItemStack.setItemMeta(itemstack, CraftItemStack.getItemMeta(itemstack));
                    }
                    return itemstack;
                    // CraftBukkit end
                }
            }

            public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, ItemStack itemstack) {
                if (itemstack.isEmpty() || itemstack.getItem() == null) { // CraftBukkit - NPE fix itemstack.getItem()
                    registryfriendlybytebuf.writeVarInt(0);
                } else {
                    registryfriendlybytebuf.writeVarInt(itemstack.getCount());
                    // Spigot start - filter
                    itemstack = itemstack.copy();
                    CraftItemStack.setItemMeta(itemstack, CraftItemStack.getItemMeta(itemstack));
                    // Spigot end
                    Item.STREAM_CODEC.encode(registryfriendlybytebuf, itemstack.getItemHolder());
                    streamcodec.encode(registryfriendlybytebuf, itemstack.components.asPatch());
                }
            }
        };
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, ItemStack> streamcodec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
                ItemStack itemstack = (ItemStack) streamcodec.decode(registryfriendlybytebuf);

                if (!itemstack.isEmpty()) {
                    RegistryOps<Unit> registryops = registryfriendlybytebuf.registryAccess().<Unit>createSerializationContext(NullOps.INSTANCE);

                    ItemStack.CODEC.encodeStart(registryops, itemstack).getOrThrow(DecoderException::new);
                }

                return itemstack;
            }

            public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, ItemStack itemstack) {
                streamcodec.encode(registryfriendlybytebuf, itemstack);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return (DataComponentMap) (!this.isEmpty() ? this.components : DataComponentMap.EMPTY);
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public DataComponentMap immutableComponents() {
        return !this.isEmpty() ? this.components.toImmutableMap() : DataComponentMap.EMPTY;
    }

    public boolean hasNonDefault(DataComponentType<?> datacomponenttype) {
        return !this.isEmpty() && this.components.hasNonDefault(datacomponenttype);
    }

    public ItemStack(IMaterial imaterial) {
        this(imaterial, 1);
    }

    public ItemStack(Holder<Item> holder) {
        this(holder.value(), 1);
    }

    public ItemStack(Holder<Item> holder, int i, DataComponentPatch datacomponentpatch) {
        this(holder.value(), i, PatchedDataComponentMap.fromPatch(((Item) holder.value()).components(), datacomponentpatch));
    }

    public ItemStack(Holder<Item> holder, int i) {
        this(holder.value(), i);
    }

    public ItemStack(IMaterial imaterial, int i) {
        this(imaterial, i, new PatchedDataComponentMap(imaterial.asItem().components()));
    }

    private ItemStack(IMaterial imaterial, int i, PatchedDataComponentMap patcheddatacomponentmap) {
        this.item = imaterial.asItem();
        this.count = i;
        this.components = patcheddatacomponentmap;
        this.getItem().verifyComponentsAfterLoad(this);
    }

    private ItemStack(@Nullable Void ovoid) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap datacomponentmap) {
        if (datacomponentmap.has(DataComponents.MAX_DAMAGE) && (Integer) datacomponentmap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> {
                return "Item cannot be both damageable and stackable";
            });
        } else {
            ItemContainerContents itemcontainercontents = (ItemContainerContents) datacomponentmap.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

            for (ItemStack itemstack : itemcontainercontents.nonEmptyItems()) {
                int i = itemstack.getCount();
                int j = itemstack.getMaxStackSize();

                if (i > j) {
                    return DataResult.error(() -> {
                        return "Item stack with count of " + i + " was larger than maximum: " + j;
                    });
                }
            }

            return DataResult.success(Unit.INSTANCE);
        }
    }

    public static Optional<ItemStack> parse(HolderLookup.a holderlookup_a, NBTBase nbtbase) {
        return ItemStack.CODEC.parse(holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), nbtbase).resultOrPartial((s) -> {
            ItemStack.LOGGER.error("Tried to load invalid item: '{}'", s);
        });
    }

    public boolean isEmpty() {
        return this == ItemStack.EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet featureflagset) {
        return this.isEmpty() || this.getItem().isEnabled(featureflagset);
    }

    public ItemStack split(int i) {
        int j = Math.min(i, this.getCount());
        ItemStack itemstack = this.copyWithCount(j);

        this.shrink(j);
        return itemstack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            this.setCount(0);
            return itemstack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tagkey) {
        return this.getItem().builtInRegistryHolder().is(tagkey);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> predicate) {
        return predicate.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> holder) {
        return this.getItem().builtInRegistryHolder() == holder;
    }

    public boolean is(HolderSet<Item> holderset) {
        return holderset.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public EnumInteractionResult useOn(ItemActionContext itemactioncontext) {
        EntityHuman entityhuman = itemactioncontext.getPlayer();
        BlockPosition blockposition = itemactioncontext.getClickedPos();

        if (entityhuman != null && !entityhuman.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new ShapeDetectorBlock(itemactioncontext.getLevel(), blockposition, false))) {
            return EnumInteractionResult.PASS;
        } else {
            Item item = this.getItem();
            // CraftBukkit start - handle all block place event logic here
            DataComponentPatch oldData = this.components.asPatch();
            int oldCount = this.getCount();
            WorldServer world = (WorldServer) itemactioncontext.getLevel();

            if (!(item instanceof ItemBucket || item instanceof SolidBucketItem)) { // if not bucket
                world.captureBlockStates = true;
                // special case bonemeal
                if (item == Items.BONE_MEAL) {
                    world.captureTreeGeneration = true;
                }
            }
            EnumInteractionResult enuminteractionresult;
            try {
                enuminteractionresult = item.useOn(itemactioncontext);
            } finally {
                world.captureBlockStates = false;
            }
            DataComponentPatch newData = this.components.asPatch();
            int newCount = this.getCount();
            this.setCount(oldCount);
            this.restorePatch(oldData);
            if (enuminteractionresult.consumesAction() && world.captureTreeGeneration && world.capturedBlockStates.size() > 0) {
                world.captureTreeGeneration = false;
                Location location = CraftLocation.toBukkit(blockposition, world.getWorld());
                TreeType treeType = BlockSapling.treeType;
                BlockSapling.treeType = null;
                List<CraftBlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                world.capturedBlockStates.clear();
                StructureGrowEvent structureEvent = null;
                if (treeType != null) {
                    boolean isBonemeal = getItem() == Items.BONE_MEAL;
                    structureEvent = new StructureGrowEvent(location, treeType, isBonemeal, (Player) entityhuman.getBukkitEntity(), (List< BlockState>) (List<? extends BlockState>) blocks);
                    org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                }

                BlockFertilizeEvent fertilizeEvent = new BlockFertilizeEvent(CraftBlock.at(world, blockposition), (Player) entityhuman.getBukkitEntity(), (List< BlockState>) (List<? extends BlockState>) blocks);
                fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                if (!fertilizeEvent.isCancelled()) {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.components.asPatch(), oldData)) {
                        this.restorePatch(newData);
                        this.setCount(newCount);
                    }
                    for (CraftBlockState blockstate : blocks) {
                        // SPIGOT-7572 - Move fix for SPIGOT-7248 to CapturedBlockState, to allow bees in bee nest
                        CapturedBlockState.setBlockState(blockstate);
                    }
                    entityhuman.awardStat(StatisticList.ITEM_USED.get(item)); // SPIGOT-7236 - award stat
                }

                ItemSign.openSign = null; // SPIGOT-6758 - Reset on early return
                return enuminteractionresult;
            }
            world.captureTreeGeneration = false;

            if (entityhuman != null && enuminteractionresult instanceof EnumInteractionResult.d) {
                EnumInteractionResult.d enuminteractionresult_d = (EnumInteractionResult.d) enuminteractionresult;

                if (enuminteractionresult_d.wasItemInteraction()) {
                    EnumHand enumhand = itemactioncontext.getHand();
                    org.bukkit.event.block.BlockPlaceEvent placeEvent = null;
                    List<BlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                    world.capturedBlockStates.clear();
                    if (blocks.size() > 1) {
                        placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockMultiPlaceEvent(world, entityhuman, enumhand, blocks, blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    } else if (blocks.size() == 1) {
                        placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(world, entityhuman, enumhand, blocks.get(0), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    }

                    if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                        enuminteractionresult = EnumInteractionResult.FAIL; // cancel placement
                        // PAIL: Remove this when MC-99075 fixed
                        placeEvent.getPlayer().updateInventory();
                        // revert back all captured blocks
                        world.preventPoiUpdated = true; // CraftBukkit - SPIGOT-5710
                        for (BlockState blockstate : blocks) {
                            blockstate.update(true, false);
                        }
                        world.preventPoiUpdated = false;

                        // Brute force all possible updates
                        BlockPosition placedPos = ((CraftBlock) placeEvent.getBlock()).getPosition();
                        for (EnumDirection dir : EnumDirection.values()) {
                            ((EntityPlayer) entityhuman).connection.send(new PacketPlayOutBlockChange(world, placedPos.relative(dir)));
                        }
                        ItemSign.openSign = null; // SPIGOT-6758 - Reset on early return
                    } else {
                        // Change the stack to its new contents if it hasn't been tampered with.
                        if (this.getCount() == oldCount && Objects.equals(this.components.asPatch(), oldData)) {
                            this.restorePatch(newData);
                            this.setCount(newCount);
                        }

                        for (Map.Entry<BlockPosition, TileEntity> e : world.capturedTileEntities.entrySet()) {
                            world.setBlockEntity(e.getValue());
                        }

                        for (BlockState blockstate : blocks) {
                            int updateFlag = ((CraftBlockState) blockstate).getFlag();
                            IBlockData oldBlock = ((CraftBlockState) blockstate).getHandle();
                            BlockPosition newblockposition = ((CraftBlockState) blockstate).getPosition();
                            IBlockData block = world.getBlockState(newblockposition);

                            if (!(block.getBlock() instanceof BlockTileEntity)) { // Containers get placed automatically
                                block.onPlace(world, newblockposition, oldBlock, true, itemactioncontext);
                            }

                            world.notifyAndUpdatePhysics(newblockposition, null, oldBlock, block, world.getBlockState(newblockposition), updateFlag, 512); // send null chunk as chunk.k() returns false by this point
                        }

                        if (this.item == Items.WITHER_SKELETON_SKULL) { // Special case skulls to allow wither spawns to be cancelled
                            BlockPosition bp = blockposition;
                            if (!world.getBlockState(blockposition).canBeReplaced()) {
                                if (!world.getBlockState(blockposition).isSolid()) {
                                    bp = null;
                                } else {
                                    bp = bp.relative(itemactioncontext.getClickedFace());
                                }
                            }
                            if (bp != null) {
                                TileEntity te = world.getBlockEntity(bp);
                                if (te instanceof TileEntitySkull) {
                                    BlockWitherSkull.checkSpawn(world, bp, (TileEntitySkull) te);
                                }
                            }
                        }

                        // SPIGOT-4678
                        if (this.item instanceof ItemSign && ItemSign.openSign != null) {
                            try {
                                if (world.getBlockEntity(ItemSign.openSign) instanceof TileEntitySign tileentitysign) {
                                    if (world.getBlockState(ItemSign.openSign).getBlock() instanceof BlockSign blocksign) {
                                        blocksign.openTextEdit(entityhuman, tileentitysign, true, org.bukkit.event.player.PlayerSignOpenEvent.Cause.PLACE); // Craftbukkit
                                    }
                                }
                            } finally {
                                ItemSign.openSign = null;
                            }
                        }

                        // SPIGOT-7315: Moved from BlockBed#setPlacedBy
                        if (placeEvent != null && this.item instanceof ItemBed) {
                            BlockPosition position = ((CraftBlock) placeEvent.getBlock()).getPosition();
                            IBlockData blockData = world.getBlockState(position);

                            if (blockData.getBlock() instanceof BlockBed) {
                                world.updateNeighborsAt(position, Blocks.AIR);
                                blockData.updateNeighbourShapes(world, position, 3);
                            }
                        }

                        // SPIGOT-1288 - play sound stripped from ItemBlock
                        if (this.item instanceof ItemBlock) {
                            SoundEffectType soundeffecttype = ((ItemBlock) this.item).getBlock().defaultBlockState().getSoundType(); // TODO: not strictly correct, however currently only affects decorated pots
                            world.playSound(entityhuman, blockposition, soundeffecttype.getPlaceSound(), SoundCategory.BLOCKS, (soundeffecttype.getVolume() + 1.0F) / 2.0F, soundeffecttype.getPitch() * 0.8F);
                        }

                        entityhuman.awardStat(StatisticList.ITEM_USED.get(item));
                    }
                }
            }
            world.capturedTileEntities.clear();
            world.capturedBlockStates.clear();
            // CraftBukkit end

            return enuminteractionresult;
        }
    }

    public float getDestroySpeed(IBlockData iblockdata) {
        return this.getItem().getDestroySpeed(this, iblockdata);
    }

    public EnumInteractionResult use(World world, EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = this.copy();
        boolean flag = this.getUseDuration(entityhuman) <= 0;
        EnumInteractionResult enuminteractionresult = this.getItem().use(world, entityhuman, enumhand);

        if (flag && enuminteractionresult instanceof EnumInteractionResult.d enuminteractionresult_d) {
            return enuminteractionresult_d.heldItemTransformedTo(enuminteractionresult_d.heldItemTransformedTo() == null ? this.applyAfterUseComponentSideEffects(entityhuman, itemstack) : enuminteractionresult_d.heldItemTransformedTo().applyAfterUseComponentSideEffects(entityhuman, itemstack));
        } else {
            return enuminteractionresult;
        }
    }

    public ItemStack finishUsingItem(World world, EntityLiving entityliving) {
        ItemStack itemstack = this.copy();
        ItemStack itemstack1 = this.getItem().finishUsingItem(this, world, entityliving);

        return itemstack1.applyAfterUseComponentSideEffects(entityliving, itemstack);
    }

    private ItemStack applyAfterUseComponentSideEffects(EntityLiving entityliving, ItemStack itemstack) {
        UseRemainder useremainder = (UseRemainder) itemstack.get(DataComponents.USE_REMAINDER);
        UseCooldown usecooldown = (UseCooldown) itemstack.get(DataComponents.USE_COOLDOWN);
        int i = itemstack.getCount();
        ItemStack itemstack1 = this;

        if (useremainder != null) {
            boolean flag = entityliving.hasInfiniteMaterials();

            Objects.requireNonNull(entityliving);
            itemstack1 = useremainder.convertIntoRemainder(this, i, flag, entityliving::handleExtraItemsCreatedOnUse);
        }

        if (usecooldown != null) {
            usecooldown.apply(itemstack, entityliving);
        }

        return itemstack1;
    }

    public NBTBase save(HolderLookup.a holderlookup_a, NBTBase nbtbase) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            return (NBTBase) ItemStack.CODEC.encode(this, holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), nbtbase).getOrThrow();
        }
    }

    public NBTBase save(HolderLookup.a holderlookup_a) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            return (NBTBase) ItemStack.CODEC.encodeStart(holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), this).getOrThrow();
        }
    }

    public int getMaxStackSize() {
        return (Integer) this.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return MathHelper.clamp((Integer) this.getOrDefault(DataComponents.DAMAGE, 0), 0, this.getMaxDamage());
    }

    public void setDamageValue(int i) {
        this.set(DataComponents.DAMAGE, MathHelper.clamp(i, 0, this.getMaxDamage()));
    }

    public int getMaxDamage() {
        return (Integer) this.getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    public boolean isBroken() {
        return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage();
    }

    public boolean nextDamageWillBreak() {
        return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage() - 1;
    }

    public void hurtAndBreak(int i, WorldServer worldserver, @Nullable EntityPlayer entityplayer, Consumer<Item> consumer) {
        int j = this.processDurabilityChange(i, worldserver, entityplayer);
        // CraftBukkit start
        if (entityplayer != null) {
            PlayerItemDamageEvent event = new PlayerItemDamageEvent(entityplayer.getBukkitEntity(), CraftItemStack.asCraftMirror(this), j);
            event.getPlayer().getServer().getPluginManager().callEvent(event);

            if (j != event.getDamage() || event.isCancelled()) {
                event.getPlayer().updateInventory();
            }
            if (event.isCancelled()) {
                return;
            }

            j = event.getDamage();
        }
        // CraftBukkit end

        if (j != 0) {
            this.applyDamage(this.getDamageValue() + j, entityplayer, consumer);
        }

    }

    private int processDurabilityChange(int i, WorldServer worldserver, @Nullable EntityPlayer entityplayer) {
        return !this.isDamageableItem() ? 0 : (entityplayer != null && entityplayer.hasInfiniteMaterials() ? 0 : (i > 0 ? EnchantmentManager.processDurabilityChange(worldserver, this, i) : i));
    }

    private void applyDamage(int i, @Nullable EntityPlayer entityplayer, Consumer<Item> consumer) {
        if (entityplayer != null) {
            CriterionTriggers.ITEM_DURABILITY_CHANGED.trigger(entityplayer, this, i);
        }

        this.setDamageValue(i);
        if (this.isBroken()) {
            Item item = this.getItem();
            // CraftBukkit start - Check for item breaking
            if (this.count == 1 && entityplayer != null) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerItemBreakEvent(entityplayer, this);
            }
            // CraftBukkit end

            this.shrink(1);
            consumer.accept(item);
        }

    }

    public void hurtWithoutBreaking(int i, EntityHuman entityhuman) {
        if (entityhuman instanceof EntityPlayer entityplayer) {
            int j = this.processDurabilityChange(i, entityplayer.serverLevel(), entityplayer);

            if (j == 0) {
                return;
            }

            int k = Math.min(this.getDamageValue() + j, this.getMaxDamage() - 1);

            this.applyDamage(k, entityplayer, (item) -> {
            });
        }

    }

    public void hurtAndBreak(int i, EntityLiving entityliving, EnumItemSlot enumitemslot) {
        World world = entityliving.level();

        if (world instanceof WorldServer worldserver) {
            EntityPlayer entityplayer;

            if (entityliving instanceof EntityPlayer entityplayer1) {
                entityplayer = entityplayer1;
            } else {
                entityplayer = null;
            }

            this.hurtAndBreak(i, worldserver, entityplayer, (item) -> {
                entityliving.onEquippedItemBroken(item, enumitemslot);
            });
        }

    }

    public ItemStack hurtAndConvertOnBreak(int i, IMaterial imaterial, EntityLiving entityliving, EnumItemSlot enumitemslot) {
        this.hurtAndBreak(i, entityliving, enumitemslot);
        if (this.isEmpty()) {
            ItemStack itemstack = this.transmuteCopyIgnoreEmpty(imaterial, 1);

            if (itemstack.isDamageableItem()) {
                itemstack.setDamageValue(0);
            }

            return itemstack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction clickaction, EntityHuman entityhuman) {
        return this.getItem().overrideStackedOnOther(this, slot, clickaction, entityhuman);
    }

    public boolean overrideOtherStackedOnMe(ItemStack itemstack, Slot slot, ClickAction clickaction, EntityHuman entityhuman, SlotAccess slotaccess) {
        return this.getItem().overrideOtherStackedOnMe(this, itemstack, slot, clickaction, entityhuman, slotaccess);
    }

    public boolean hurtEnemy(EntityLiving entityliving, EntityLiving entityliving1) {
        Item item = this.getItem();

        item.hurtEnemy(this, entityliving, entityliving1);
        if (this.has(DataComponents.WEAPON)) {
            if (entityliving1 instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entityliving1;

                entityhuman.awardStat(StatisticList.ITEM_USED.get(item));
            }

            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(EntityLiving entityliving, EntityLiving entityliving1) {
        this.getItem().postHurtEnemy(this, entityliving, entityliving1);
        Weapon weapon = (Weapon) this.get(DataComponents.WEAPON);

        if (weapon != null) {
            this.hurtAndBreak(weapon.itemDamagePerAttack(), entityliving1, EnumItemSlot.MAINHAND);
        }

    }

    public void mineBlock(World world, IBlockData iblockdata, BlockPosition blockposition, EntityHuman entityhuman) {
        Item item = this.getItem();

        if (item.mineBlock(this, world, iblockdata, blockposition, entityhuman)) {
            entityhuman.awardStat(StatisticList.ITEM_USED.get(item));
        }

    }

    public boolean isCorrectToolForDrops(IBlockData iblockdata) {
        return this.getItem().isCorrectToolForDrops(this, iblockdata);
    }

    public EnumInteractionResult interactLivingEntity(EntityHuman entityhuman, EntityLiving entityliving, EnumHand enumhand) {
        Equippable equippable = (Equippable) this.get(DataComponents.EQUIPPABLE);

        if (equippable != null && equippable.equipOnInteract()) {
            EnumInteractionResult enuminteractionresult = equippable.equipOnTarget(entityhuman, entityliving, this);

            if (enuminteractionresult != EnumInteractionResult.PASS) {
                return enuminteractionresult;
            }
        }

        return this.getItem().interactLivingEntity(this, entityhuman, entityliving, enumhand);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(this.getItem(), this.count, this.components.copy());

            itemstack.setPopTime(this.getPopTime());
            return itemstack;
        }
    }

    public ItemStack copyWithCount(int i) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            itemstack.setCount(i);
            return itemstack;
        }
    }

    public ItemStack transmuteCopy(IMaterial imaterial) {
        return this.transmuteCopy(imaterial, this.getCount());
    }

    public ItemStack transmuteCopy(IMaterial imaterial, int i) {
        return this.isEmpty() ? ItemStack.EMPTY : this.transmuteCopyIgnoreEmpty(imaterial, i);
    }

    private ItemStack transmuteCopyIgnoreEmpty(IMaterial imaterial, int i) {
        return new ItemStack(imaterial.asItem().builtInRegistryHolder(), i, this.components.asPatch());
    }

    public static boolean matches(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack == itemstack1 ? true : (itemstack.getCount() != itemstack1.getCount() ? false : isSameItemSameComponents(itemstack, itemstack1));
    }

    /** @deprecated */
    @Deprecated
    public static boolean listMatches(List<ItemStack> list, List<ItemStack> list1) {
        if (list.size() != list1.size()) {
            return false;
        } else {
            for (int i = 0; i < list.size(); ++i) {
                if (!matches((ItemStack) list.get(i), (ItemStack) list1.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isSameItem(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack.is(itemstack1.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack itemstack, ItemStack itemstack1) {
        return !itemstack.is(itemstack1.getItem()) ? false : (itemstack.isEmpty() && itemstack1.isEmpty() ? true : Objects.equals(itemstack.components, itemstack1.components));
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String s) {
        return ItemStack.CODEC.lenientOptionalFieldOf(s).xmap((optional) -> {
            return (ItemStack) optional.orElse(ItemStack.EMPTY);
        }, (itemstack) -> {
            return itemstack.isEmpty() ? Optional.empty() : Optional.of(itemstack);
        });
    }

    public static int hashItemAndComponents(@Nullable ItemStack itemstack) {
        if (itemstack != null) {
            int i = 31 + itemstack.getItem().hashCode();

            return 31 * i + itemstack.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    /** @deprecated */
    @Deprecated
    public static int hashStackList(List<ItemStack> list) {
        int i = 0;

        for (ItemStack itemstack : list) {
            i = i * 31 + hashItemAndComponents(itemstack);
        }

        return i;
    }

    public String toString() {
        int i = this.getCount();

        return i + " " + String.valueOf(this.getItem());
    }

    public void inventoryTick(World world, Entity entity, @Nullable EnumItemSlot enumitemslot) {
        if (this.popTime > 0) {
            --this.popTime;
        }

        if (world instanceof WorldServer worldserver) {
            this.getItem().inventoryTick(this, worldserver, entity, enumitemslot);
        }

    }

    public void onCraftedBy(EntityHuman entityhuman, int i) {
        entityhuman.awardStat(StatisticList.ITEM_CRAFTED.get(this.getItem()), i);
        this.getItem().onCraftedBy(this, entityhuman);
    }

    public void onCraftedBySystem(World world) {
        this.getItem().onCraftedPostProcess(this, world);
    }

    public int getUseDuration(EntityLiving entityliving) {
        return this.getItem().getUseDuration(this, entityliving);
    }

    public ItemUseAnimation getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(World world, EntityLiving entityliving, int i) {
        ItemStack itemstack = this.copy();

        if (this.getItem().releaseUsing(this, world, entityliving, i)) {
            ItemStack itemstack1 = this.applyAfterUseComponentSideEffects(entityliving, itemstack);

            if (itemstack1 != this) {
                entityliving.setItemInHand(entityliving.getUsedItemHand(), itemstack1);
            }
        }

    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    // CraftBukkit start
    public void restorePatch(DataComponentPatch datacomponentpatch) {
        this.components.restorePatch(datacomponentpatch);
    }
    // CraftBukkit end

    @Nullable
    public <T> T set(DataComponentType<T> datacomponenttype, @Nullable T t0) {
        return (T) this.components.set(datacomponenttype, t0);
    }

    public <T> void copyFrom(DataComponentType<T> datacomponenttype, DataComponentGetter datacomponentgetter) {
        this.set(datacomponenttype, datacomponentgetter.get(datacomponenttype));
    }

    @Nullable
    public <T, U> T update(DataComponentType<T> datacomponenttype, T t0, U u0, BiFunction<T, U, T> bifunction) {
        return (T) this.set(datacomponenttype, bifunction.apply(this.getOrDefault(datacomponenttype, t0), u0));
    }

    @Nullable
    public <T> T update(DataComponentType<T> datacomponenttype, T t0, UnaryOperator<T> unaryoperator) {
        T t1 = (T) this.getOrDefault(datacomponenttype, t0);

        return (T) this.set(datacomponenttype, unaryoperator.apply(t1));
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> datacomponenttype) {
        return (T) this.components.remove(datacomponenttype);
    }

    public void applyComponentsAndValidate(DataComponentPatch datacomponentpatch) {
        DataComponentPatch datacomponentpatch1 = this.components.asPatch();

        this.components.applyPatch(datacomponentpatch);
        Optional<DataResult.Error<ItemStack>> optional = validateStrict(this).error();

        if (optional.isPresent()) {
            ItemStack.LOGGER.error("Failed to apply component patch '{}' to item: '{}'", datacomponentpatch, ((Error) optional.get()).message());
            this.components.restorePatch(datacomponentpatch1);
        } else {
            this.getItem().verifyComponentsAfterLoad(this);
        }
    }

    public void applyComponents(DataComponentPatch datacomponentpatch) {
        this.components.applyPatch(datacomponentpatch);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public void applyComponents(DataComponentMap datacomponentmap) {
        this.components.setAll(datacomponentmap);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public IChatBaseComponent getHoverName() {
        IChatBaseComponent ichatbasecomponent = this.getCustomName();

        return ichatbasecomponent != null ? ichatbasecomponent : this.getItemName();
    }

    @Nullable
    public IChatBaseComponent getCustomName() {
        IChatBaseComponent ichatbasecomponent = (IChatBaseComponent) this.get(DataComponents.CUSTOM_NAME);

        if (ichatbasecomponent != null) {
            return ichatbasecomponent;
        } else {
            WrittenBookContent writtenbookcontent = (WrittenBookContent) this.get(DataComponents.WRITTEN_BOOK_CONTENT);

            if (writtenbookcontent != null) {
                String s = (String) writtenbookcontent.title().raw();

                if (!UtilColor.isBlank(s)) {
                    return IChatBaseComponent.literal(s);
                }
            }

            return null;
        }
    }

    public IChatBaseComponent getItemName() {
        return this.getItem().getName(this);
    }

    public IChatBaseComponent getStyledHoverName() {
        IChatMutableComponent ichatmutablecomponent = IChatBaseComponent.empty().append(this.getHoverName()).withStyle(this.getRarity().color());

        if (this.has(DataComponents.CUSTOM_NAME)) {
            ichatmutablecomponent.withStyle(EnumChatFormat.ITALIC);
        }

        return ichatmutablecomponent;
    }

    public <T extends TooltipProvider> void addToTooltip(DataComponentType<T> datacomponenttype, Item.b item_b, TooltipDisplay tooltipdisplay, Consumer<IChatBaseComponent> consumer, TooltipFlag tooltipflag) {
        T t0 = (T) (this.get(datacomponenttype));

        if (t0 != null && tooltipdisplay.shows(datacomponenttype)) {
            t0.addToTooltip(item_b, consumer, tooltipflag, this.components);
        }

    }

    public List<IChatBaseComponent> getTooltipLines(Item.b item_b, @Nullable EntityHuman entityhuman, TooltipFlag tooltipflag) {
        TooltipDisplay tooltipdisplay = (TooltipDisplay) this.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        if (!tooltipflag.isCreative() && tooltipdisplay.hideTooltip()) {
            boolean flag = this.getItem().shouldPrintOpWarning(this, entityhuman);

            return flag ? ItemStack.OP_NBT_WARNING : List.of();
        } else {
            List<IChatBaseComponent> list = Lists.newArrayList();

            list.add(this.getStyledHoverName());
            Objects.requireNonNull(list);
            this.addDetailsToTooltip(item_b, tooltipdisplay, entityhuman, tooltipflag, list::add);
            return list;
        }
    }

    public void addDetailsToTooltip(Item.b item_b, TooltipDisplay tooltipdisplay, @Nullable EntityHuman entityhuman, TooltipFlag tooltipflag, Consumer<IChatBaseComponent> consumer) {
        this.getItem().appendHoverText(this, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.TROPICAL_FISH_PATTERN, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.INSTRUMENT, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.MAP_ID, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.BEES, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.CONTAINER_LOOT, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.CONTAINER, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.BANNER_PATTERNS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.POT_DECORATIONS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.WRITTEN_BOOK_CONTENT, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.CHARGED_PROJECTILES, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.FIREWORKS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.FIREWORK_EXPLOSION, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.POTION_CONTENTS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.TRIM, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.ENCHANTMENTS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.DYED_COLOR, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.LORE, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addAttributeTooltips(consumer, tooltipdisplay, entityhuman);
        if (this.has(DataComponents.UNBREAKABLE) && tooltipdisplay.shows(DataComponents.UNBREAKABLE)) {
            consumer.accept(ItemStack.UNBREAKABLE_TOOLTIP);
        }

        this.addToTooltip(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.SUSPICIOUS_STEW_EFFECTS, item_b, tooltipdisplay, consumer, tooltipflag);
        this.addToTooltip(DataComponents.BLOCK_STATE, item_b, tooltipdisplay, consumer, tooltipflag);
        if ((this.is(Items.SPAWNER) || this.is(Items.TRIAL_SPAWNER)) && tooltipdisplay.shows(DataComponents.BLOCK_ENTITY_DATA)) {
            CustomData customdata = (CustomData) this.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);

            Spawner.appendHoverText(customdata, consumer, "SpawnData");
        }

        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_BREAK);

        if (adventuremodepredicate != null && tooltipdisplay.shows(DataComponents.CAN_BREAK)) {
            consumer.accept(CommonComponents.EMPTY);
            consumer.accept(AdventureModePredicate.CAN_BREAK_HEADER);
            adventuremodepredicate.addToTooltip(consumer);
        }

        AdventureModePredicate adventuremodepredicate1 = (AdventureModePredicate) this.get(DataComponents.CAN_PLACE_ON);

        if (adventuremodepredicate1 != null && tooltipdisplay.shows(DataComponents.CAN_PLACE_ON)) {
            consumer.accept(CommonComponents.EMPTY);
            consumer.accept(AdventureModePredicate.CAN_PLACE_HEADER);
            adventuremodepredicate1.addToTooltip(consumer);
        }

        if (tooltipflag.isAdvanced()) {
            if (this.isDamaged() && tooltipdisplay.shows(DataComponents.DAMAGE)) {
                consumer.accept(IChatBaseComponent.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
            }

            consumer.accept(IChatBaseComponent.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(EnumChatFormat.DARK_GRAY));
            int i = this.components.size();

            if (i > 0) {
                consumer.accept(IChatBaseComponent.translatable("item.components", i).withStyle(EnumChatFormat.DARK_GRAY));
            }
        }

        if (entityhuman != null && !this.getItem().isEnabled(entityhuman.level().enabledFeatures())) {
            consumer.accept(ItemStack.DISABLED_ITEM_TOOLTIP);
        }

        boolean flag = this.getItem().shouldPrintOpWarning(this, entityhuman);

        if (flag) {
            ItemStack.OP_NBT_WARNING.forEach(consumer);
        }

    }

    private void addAttributeTooltips(Consumer<IChatBaseComponent> consumer, TooltipDisplay tooltipdisplay, @Nullable EntityHuman entityhuman) {
        if (tooltipdisplay.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
            for (EquipmentSlotGroup equipmentslotgroup : EquipmentSlotGroup.values()) {
                MutableBoolean mutableboolean = new MutableBoolean(true);

                this.forEachModifier(equipmentslotgroup, (holder, attributemodifier) -> {
                    if (mutableboolean.isTrue()) {
                        consumer.accept(CommonComponents.EMPTY);
                        consumer.accept(IChatBaseComponent.translatable("item.modifiers." + equipmentslotgroup.getSerializedName()).withStyle(EnumChatFormat.GRAY));
                        mutableboolean.setFalse();
                    }

                    this.addModifierTooltip(consumer, entityhuman, holder, attributemodifier);
                });
            }

        }
    }

    private void addModifierTooltip(Consumer<IChatBaseComponent> consumer, @Nullable EntityHuman entityhuman, Holder<AttributeBase> holder, AttributeModifier attributemodifier) {
        double d0 = attributemodifier.amount();
        boolean flag = false;

        if (entityhuman != null) {
            if (attributemodifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                d0 += entityhuman.getAttributeBaseValue(GenericAttributes.ATTACK_DAMAGE);
                flag = true;
            } else if (attributemodifier.is(Item.BASE_ATTACK_SPEED_ID)) {
                d0 += entityhuman.getAttributeBaseValue(GenericAttributes.ATTACK_SPEED);
                flag = true;
            }
        }

        double d1;

        if (attributemodifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && attributemodifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            if (holder.is(GenericAttributes.KNOCKBACK_RESISTANCE)) {
                d1 = d0 * 10.0D;
            } else {
                d1 = d0;
            }
        } else {
            d1 = d0 * 100.0D;
        }

        if (flag) {
            consumer.accept(CommonComponents.space().append((IChatBaseComponent) IChatBaseComponent.translatable("attribute.modifier.equals." + attributemodifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), IChatBaseComponent.translatable(((AttributeBase) holder.value()).getDescriptionId()))).withStyle(EnumChatFormat.DARK_GREEN));
        } else if (d0 > 0.0D) {
            consumer.accept(IChatBaseComponent.translatable("attribute.modifier.plus." + attributemodifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), IChatBaseComponent.translatable(((AttributeBase) holder.value()).getDescriptionId())).withStyle(((AttributeBase) holder.value()).getStyle(true)));
        } else if (d0 < 0.0D) {
            consumer.accept(IChatBaseComponent.translatable("attribute.modifier.take." + attributemodifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-d1), IChatBaseComponent.translatable(((AttributeBase) holder.value()).getDescriptionId())).withStyle(((AttributeBase) holder.value()).getStyle(false)));
        }

    }

    public boolean hasFoil() {
        Boolean obool = (Boolean) this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

        return obool != null ? obool : this.getItem().isFoil(this);
    }

    public EnumItemRarity getRarity() {
        EnumItemRarity enumitemrarity = (EnumItemRarity) this.getOrDefault(DataComponents.RARITY, EnumItemRarity.COMMON);

        if (!this.isEnchanted()) {
            return enumitemrarity;
        } else {
            EnumItemRarity enumitemrarity1;

            switch (enumitemrarity) {
                case COMMON:
                case UNCOMMON:
                    enumitemrarity1 = EnumItemRarity.RARE;
                    break;
                case RARE:
                    enumitemrarity1 = EnumItemRarity.EPIC;
                    break;
                default:
                    enumitemrarity1 = enumitemrarity;
            }

            return enumitemrarity1;
        }
    }

    public boolean isEnchantable() {
        if (!this.has(DataComponents.ENCHANTABLE)) {
            return false;
        } else {
            ItemEnchantments itemenchantments = (ItemEnchantments) this.get(DataComponents.ENCHANTMENTS);

            return itemenchantments != null && itemenchantments.isEmpty();
        }
    }

    public void enchant(Holder<Enchantment> holder, int i) {
        EnchantmentManager.updateEnchantments(this, (itemenchantments_a) -> {
            itemenchantments_a.upgrade(holder, i);
        });
    }

    public boolean isEnchanted() {
        return !((ItemEnchantments) this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).isEmpty();
    }

    public ItemEnchantments getEnchantments() {
        return (ItemEnchantments) this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof EntityItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity entity) {
        if (!this.isEmpty()) {
            this.entityRepresentation = entity;
        }

    }

    @Nullable
    public EntityItemFrame getFrame() {
        return this.entityRepresentation instanceof EntityItemFrame ? (EntityItemFrame) this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup equipmentslotgroup, BiConsumer<Holder<AttributeBase>, AttributeModifier> biconsumer) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        itemattributemodifiers.forEach(equipmentslotgroup, biconsumer);
        EnchantmentManager.forEachModifier(this, equipmentslotgroup, biconsumer);
    }

    public void forEachModifier(EnumItemSlot enumitemslot, BiConsumer<Holder<AttributeBase>, AttributeModifier> biconsumer) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        itemattributemodifiers.forEach(enumitemslot, biconsumer);
        EnchantmentManager.forEachModifier(this, enumitemslot, biconsumer);
    }

    // CraftBukkit start
    @Deprecated
    public void setItem(Item item) {
        this.item = item;
    }
    // CraftBukkit end

    public IChatBaseComponent getDisplayName() {
        IChatMutableComponent ichatmutablecomponent = IChatBaseComponent.empty().append(this.getHoverName());

        if (this.has(DataComponents.CUSTOM_NAME)) {
            ichatmutablecomponent.withStyle(EnumChatFormat.ITALIC);
        }

        IChatMutableComponent ichatmutablecomponent1 = ChatComponentUtils.wrapInSquareBrackets(ichatmutablecomponent);

        if (!this.isEmpty()) {
            ichatmutablecomponent1.withStyle(this.getRarity().color()).withStyle((chatmodifier) -> {
                return chatmodifier.withHoverEvent(new ChatHoverable.d(this));
            });
        }

        return ichatmutablecomponent1;
    }

    public boolean canPlaceOnBlockInAdventureMode(ShapeDetectorBlock shapedetectorblock) {
        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_PLACE_ON);

        return adventuremodepredicate != null && adventuremodepredicate.test(shapedetectorblock);
    }

    public boolean canBreakBlockInAdventureMode(ShapeDetectorBlock shapedetectorblock) {
        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_BREAK);

        return adventuremodepredicate != null && adventuremodepredicate.test(shapedetectorblock);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int i) {
        this.popTime = i;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int i) {
        this.count = i;
    }

    public void limitSize(int i) {
        if (!this.isEmpty() && this.getCount() > i) {
            this.setCount(i);
        }

    }

    public void grow(int i) {
        this.setCount(this.getCount() + i);
    }

    public void shrink(int i) {
        this.grow(-i);
    }

    public void consume(int i, @Nullable EntityLiving entityliving) {
        if ((entityliving == null || !entityliving.hasInfiniteMaterials()) && this != ItemStack.EMPTY) { // CraftBukkit
            this.shrink(i);
        }

    }

    public ItemStack consumeAndReturn(int i, @Nullable EntityLiving entityliving) {
        ItemStack itemstack = this.copyWithCount(i);

        this.consume(i, entityliving);
        return itemstack;
    }

    public void onUseTick(World world, EntityLiving entityliving, int i) {
        Consumable consumable = (Consumable) this.get(DataComponents.CONSUMABLE);

        if (consumable != null && consumable.shouldEmitParticlesAndSounds(i)) {
            consumable.emitParticlesAndSounds(entityliving.getRandom(), entityliving, this, 5);
        }

        this.getItem().onUseTick(world, entityliving, this, i);
    }

    public void onDestroyed(EntityItem entityitem) {
        this.getItem().onDestroyed(entityitem);
    }

    public boolean canBeHurtBy(DamageSource damagesource) {
        DamageResistant damageresistant = (DamageResistant) this.get(DataComponents.DAMAGE_RESISTANT);

        return damageresistant == null || !damageresistant.isResistantTo(damagesource);
    }

    public boolean isValidRepairItem(ItemStack itemstack) {
        Repairable repairable = (Repairable) this.get(DataComponents.REPAIRABLE);

        return repairable != null && repairable.isValidRepairItem(itemstack);
    }

    public boolean canDestroyBlock(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman) {
        return this.getItem().canDestroyBlock(this, iblockdata, world, blockposition, entityhuman);
    }

    static {
        MapCodec mapcodec = ItemStack.MAP_CODEC;

        Objects.requireNonNull(mapcodec);
        CODEC = Codec.lazyInitialized(mapcodec::codec);
        SINGLE_ITEM_CODEC = Codec.lazyInitialized(() -> {
            return RecordCodecBuilder.create((instance) -> {
                return instance.group(Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder), DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemstack) -> {
                    return itemstack.components.asPatch();
                })).apply(instance, (holder, datacomponentpatch) -> {
                    return new ItemStack(holder, 1, datacomponentpatch);
                });
            });
        });
        STRICT_CODEC = ItemStack.CODEC.validate(ItemStack::validateStrict);
        STRICT_SINGLE_ITEM_CODEC = ItemStack.SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
        OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(ItemStack.CODEC).xmap((optional) -> {
            return (ItemStack) optional.orElse(ItemStack.EMPTY);
        }, (itemstack) -> {
            return itemstack.isEmpty() ? Optional.empty() : Optional.of(itemstack);
        });
        SIMPLE_ITEM_CODEC = Item.CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
        OPTIONAL_STREAM_CODEC = createOptionalStreamCodec(DataComponentPatch.STREAM_CODEC);
        OPTIONAL_UNTRUSTED_STREAM_CODEC = createOptionalStreamCodec(DataComponentPatch.DELIMITED_STREAM_CODEC);
        STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
                ItemStack itemstack = (ItemStack) ItemStack.OPTIONAL_STREAM_CODEC.decode(registryfriendlybytebuf);

                if (itemstack.isEmpty()) {
                    throw new DecoderException("Empty ItemStack not allowed");
                } else {
                    return itemstack;
                }
            }

            public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, ItemStack itemstack) {
                if (itemstack.isEmpty()) {
                    throw new EncoderException("Empty ItemStack not allowed");
                } else {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(registryfriendlybytebuf, itemstack);
                }
            }
        };
        OPTIONAL_LIST_STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity));
        LOGGER = LogUtils.getLogger();
        EMPTY = new ItemStack((Void) null);
        DISABLED_ITEM_TOOLTIP = IChatBaseComponent.translatable("item.disabled").withStyle(EnumChatFormat.RED);
    }
}
