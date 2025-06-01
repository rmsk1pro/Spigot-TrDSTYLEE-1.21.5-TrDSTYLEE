package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.SystemUtils;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.commands.arguments.ArgumentAnchor;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.ParticleParamBlock;
import net.minecraft.core.particles.ParticleParamItem;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutAnimation;
import net.minecraft.network.protocol.game.PacketPlayOutCollect;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEffect;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEquipment;
import net.minecraft.network.protocol.game.PacketPlayOutEntityStatus;
import net.minecraft.network.protocol.game.PacketPlayOutRemoveEntityEffect;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsEntity;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumHand;
import net.minecraft.world.damagesource.CombatMath;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectList;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.attributes.AttributeBase;
import net.minecraft.world.entity.ai.attributes.AttributeDefaults;
import net.minecraft.world.entity.ai.attributes.AttributeMapBase;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.animal.EntityBird;
import net.minecraft.world.entity.animal.wolf.EntityWolf;
import net.minecraft.world.entity.boss.wither.EntityWither;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockBed;
import net.minecraft.world.level.block.BlockHoney;
import net.minecraft.world.level.block.BlockLadder;
import net.minecraft.world.level.block.BlockTrapdoor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundEffectType;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameterSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardTeam;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedList;
import java.util.UUID;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.component.Consumable;
import org.bukkit.Location;
import org.bukkit.craftbukkit.attribute.CraftAttributeMap;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ArrowBodyCountChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
// CraftBukkit end

public abstract class EntityLiving extends Entity implements Attackable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_ACTIVE_EFFECTS = "active_effects";
    private static final MinecraftKey SPEED_MODIFIER_POWDER_SNOW_ID = MinecraftKey.withDefaultNamespace("powder_snow");
    private static final MinecraftKey SPRINTING_MODIFIER_ID = MinecraftKey.withDefaultNamespace("sprinting");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(EntityLiving.SPRINTING_MODIFIER_ID, (double) 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int BODY_ARMOR_OFFSET = 105;
    public static final int SADDLE_OFFSET = 106;
    public static final int SWING_DURATION = 6;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003D;
    public static final double DEFAULT_BASE_GRAVITY = 0.08D;
    public static final int DEATH_DURATION = 20;
    protected static final float INPUT_FRICTION = 0.98F;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final float BASE_JUMP_POWER = 0.42F;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0D;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    public static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final DataWatcherObject<Byte> DATA_LIVING_ENTITY_FLAGS = DataWatcher.<Byte>defineId(EntityLiving.class, DataWatcherRegistry.BYTE);
    public static final DataWatcherObject<Float> DATA_HEALTH_ID = DataWatcher.<Float>defineId(EntityLiving.class, DataWatcherRegistry.FLOAT);
    private static final DataWatcherObject<List<ParticleParam>> DATA_EFFECT_PARTICLES = DataWatcher.<List<ParticleParam>>defineId(EntityLiving.class, DataWatcherRegistry.PARTICLES);
    private static final DataWatcherObject<Boolean> DATA_EFFECT_AMBIENCE_ID = DataWatcher.<Boolean>defineId(EntityLiving.class, DataWatcherRegistry.BOOLEAN);
    public static final DataWatcherObject<Integer> DATA_ARROW_COUNT_ID = DataWatcher.<Integer>defineId(EntityLiving.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> DATA_STINGER_COUNT_ID = DataWatcher.<Integer>defineId(EntityLiving.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Optional<BlockPosition>> SLEEPING_POS_ID = DataWatcher.<Optional<BlockPosition>>defineId(EntityLiving.class, DataWatcherRegistry.OPTIONAL_BLOCK_POS);
    private static final int PARTICLE_FREQUENCY_WHEN_INVISIBLE = 15;
    protected static final EntitySize SLEEPING_DIMENSIONS = EntitySize.fixed(0.2F, 0.2F).withEyeHeight(0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    public static final float DEFAULT_BABY_SCALE = 0.5F;
    public static final String ATTRIBUTES_FIELD = "attributes";
    public static final Predicate<EntityLiving> PLAYER_NOT_WEARING_DISGUISE_ITEM = (entityliving) -> {
        if (entityliving instanceof EntityHuman entityhuman) {
            ItemStack itemstack = entityhuman.getItemBySlot(EnumItemSlot.HEAD);

            return !itemstack.is(TagsItem.GAZE_DISGUISE_EQUIPMENT);
        } else {
            return true;
        }
    };
    private final AttributeMapBase attributes;
    public CombatTracker combatTracker = new CombatTracker(this);
    public final Map<Holder<MobEffectList>, MobEffect> activeEffects = Maps.newHashMap();
    private final Map<EnumItemSlot, ItemStack> lastEquipmentItems = SystemUtils.<EnumItemSlot, ItemStack>makeEnumMap(EnumItemSlot.class, (enumitemslot) -> {
        return ItemStack.EMPTY;
    });
    public boolean swinging;
    private boolean discardFriction = false;
    public EnumHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public int invulnerableDuration = 20;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public final ElytraAnimationState elytraAnimationState = new ElytraAnimationState(this);
    @Nullable
    public EntityReference<EntityHuman> lastHurtByPlayer;
    protected int lastHurtByPlayerMemoryTime;
    protected boolean dead;
    protected int noActionTime;
    public float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected InterpolationHandler interpolation = new InterpolationHandler(this);
    protected double lerpYHeadRot;
    protected int lerpHeadSteps;
    public boolean effectsDirty = true;
    @Nullable
    public EntityReference<EntityLiving> lastHurtByMob;
    public int lastHurtByMobTimestamp;
    @Nullable
    private EntityLiving lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem;
    public int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPosition lastPos;
    private Optional<BlockPosition> lastClimbablePos;
    @Nullable
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    protected float autoSpinAttackDmg;
    @Nullable
    protected ItemStack autoSpinAttackItemStack;
    private float swimAmount;
    private float swimAmountO;
    protected BehaviorController<?> brain;
    protected boolean skipDropExperience;
    private final EnumMap<EnumItemSlot, Reference2ObjectMap<Enchantment, Set<EnchantmentLocationBasedEffect>>> activeLocationDependentEnchantments;
    public final EntityEquipment equipment;
    // CraftBukkit start
    public int expToDrop;
    public ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>();
    public final org.bukkit.craftbukkit.attribute.CraftAttributeMap craftAttributes;
    public boolean collides = true;
    public Set<UUID> collidableExemptions = new HashSet<>();
    public boolean bukkitPickUpLoot;

    @Override
    public float getBukkitYaw() {
        return getYHeadRot();
    }
    // CraftBukkit end

    protected EntityLiving(EntityTypes<? extends EntityLiving> entitytypes, World world) {
        super(entitytypes, world);
        this.useItem = ItemStack.EMPTY;
        this.lastClimbablePos = Optional.empty();
        this.activeLocationDependentEnchantments = new EnumMap(EnumItemSlot.class);
        this.attributes = new AttributeMapBase(AttributeDefaults.getSupplier(entitytypes));
        this.craftAttributes = new CraftAttributeMap(attributes); // CraftBukkit
        // CraftBukkit - setHealth(getMaxHealth()) inlined and simplified to skip the instanceof check for EntityPlayer, as getBukkitEntity() is not initialized in constructor
        this.entityData.set(EntityLiving.DATA_HEALTH_ID, (float) this.getAttribute(GenericAttributes.MAX_HEALTH).getValue());
        this.equipment = this.createEquipment();
        this.blocksBuilding = true;
        this.reapplyPosition();
        this.setYRot((float) (Math.random() * (double) ((float) Math.PI * 2F)));
        this.yHeadRot = this.getYRot();
        DynamicOpsNBT dynamicopsnbt = DynamicOpsNBT.INSTANCE;

        this.brain = this.makeBrain(new Dynamic(dynamicopsnbt, (NBTBase) dynamicopsnbt.createMap(ImmutableMap.of(dynamicopsnbt.createString("memories"), (NBTBase) dynamicopsnbt.emptyMap()))));
    }

    @Contract(pure = true)
    protected EntityEquipment createEquipment() {
        return new EntityEquipment();
    }

    public BehaviorController<?> getBrain() {
        return this.brain;
    }

    protected BehaviorController.b<?> brainProvider() {
        return BehaviorController.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    @Override
    public void kill(WorldServer worldserver) {
        this.hurtServer(worldserver, this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityTypes<?> entitytypes) {
        return true;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityLiving.DATA_LIVING_ENTITY_FLAGS, (byte) 0);
        datawatcher_a.define(EntityLiving.DATA_EFFECT_PARTICLES, List.of());
        datawatcher_a.define(EntityLiving.DATA_EFFECT_AMBIENCE_ID, false);
        datawatcher_a.define(EntityLiving.DATA_ARROW_COUNT_ID, 0);
        datawatcher_a.define(EntityLiving.DATA_STINGER_COUNT_ID, 0);
        datawatcher_a.define(EntityLiving.DATA_HEALTH_ID, 1.0F);
        datawatcher_a.define(EntityLiving.SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeProvider.Builder createLivingAttributes() {
        return AttributeProvider.builder().add(GenericAttributes.MAX_HEALTH).add(GenericAttributes.KNOCKBACK_RESISTANCE).add(GenericAttributes.MOVEMENT_SPEED).add(GenericAttributes.ARMOR).add(GenericAttributes.ARMOR_TOUGHNESS).add(GenericAttributes.MAX_ABSORPTION).add(GenericAttributes.STEP_HEIGHT).add(GenericAttributes.SCALE).add(GenericAttributes.GRAVITY).add(GenericAttributes.SAFE_FALL_DISTANCE).add(GenericAttributes.FALL_DAMAGE_MULTIPLIER).add(GenericAttributes.JUMP_STRENGTH).add(GenericAttributes.OXYGEN_BONUS).add(GenericAttributes.BURNING_TIME).add(GenericAttributes.EXPLOSION_KNOCKBACK_RESISTANCE).add(GenericAttributes.WATER_MOVEMENT_EFFICIENCY).add(GenericAttributes.MOVEMENT_EFFICIENCY).add(GenericAttributes.ATTACK_KNOCKBACK);
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (flag && this.fallDistance > 0.0D) {
                this.onChangedBlock(worldserver, blockposition);
                double d1 = (double) Math.max(0, MathHelper.floor(this.calculateFallPower(this.fallDistance)));

                if (d1 > 0.0D && !iblockdata.isAir()) {
                    double d2 = this.getX();
                    double d3 = this.getY();
                    double d4 = this.getZ();
                    BlockPosition blockposition1 = this.blockPosition();

                    if (blockposition.getX() != blockposition1.getX() || blockposition.getZ() != blockposition1.getZ()) {
                        double d5 = d2 - (double) blockposition.getX() - 0.5D;
                        double d6 = d4 - (double) blockposition.getZ() - 0.5D;
                        double d7 = Math.max(Math.abs(d5), Math.abs(d6));

                        d2 = (double) blockposition.getX() + 0.5D + d5 / d7 * 0.5D;
                        d4 = (double) blockposition.getZ() + 0.5D + d6 / d7 * 0.5D;
                    }

                    double d8 = Math.min((double) 0.2F + d1 / 15.0D, 2.5D);
                    int i = (int) (150.0D * d8);

                    // CraftBukkit start - visiblity api
                    if (this instanceof EntityPlayer) {
                        worldserver.sendParticlesSource((EntityPlayer) this, new ParticleParamBlock(Particles.BLOCK, iblockdata), false, false, d2, d3, d4, i, 0.0D, 0.0D, 0.0D, (double) 0.15F);
                    } else {
                        worldserver.sendParticles(new ParticleParamBlock(Particles.BLOCK, iblockdata), d2, d3, d4, i, 0.0D, 0.0D, 0.0D, (double) 0.15F);
                    }
                    // CraftBukkit end
                }
            }
        }

        super.checkFallDamage(d0, flag, iblockdata, blockposition);
        if (flag) {
            this.lastClimbablePos = Optional.empty();
        }

    }

    public final boolean canBreatheUnderwater() {
        return this.getType().is(TagsEntity.CAN_BREATHE_UNDER_WATER);
    }

    public float getSwimAmount(float f) {
        return MathHelper.lerp(f, this.swimAmountO, this.swimAmount);
    }

    public boolean hasLandedInLiquid() {
        return this.getDeltaMovement().y() < (double) 1.0E-5F && this.isInLiquid();
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        WorldServer worldserver = (WorldServer) this.level(); // CraftBukkit - decompile error

        if (worldserver instanceof WorldServer worldserver1) {
            EnchantmentManager.tickEffects(worldserver1, this);
        }

        super.baseTick();
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("livingEntityBaseTick");
        if (this.fireImmune() || this.level().isClientSide) {
            this.clearFire();
        }

        if (this.isAlive()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                worldserver = (WorldServer) world;
                boolean flag = this instanceof EntityHuman;

                if (this.isInWall()) {
                    this.hurtServer(worldserver, this.damageSources().inWall(), 1.0F);
                } else if (flag && !worldserver.getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d0 = worldserver.getWorldBorder().getDistanceToBorder(this) + worldserver.getWorldBorder().getDamageSafeZone();

                    if (d0 < 0.0D) {
                        double d1 = worldserver.getWorldBorder().getDamagePerBlock();

                        if (d1 > 0.0D) {
                            this.hurtServer(worldserver, this.damageSources().outOfBorder(), (float) Math.max(1, MathHelper.floor(-d0 * d1)));
                        }
                    }
                }

                if (this.isEyeInFluid(TagsFluid.WATER) && !worldserver.getBlockState(BlockPosition.containing(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                    boolean flag1 = !this.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(this) && (!flag || !((EntityHuman) this).getAbilities().invulnerable);

                    if (flag1) {
                        this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                        if (this.getAirSupply() == -20) {
                            this.setAirSupply(0);
                            worldserver.broadcastEntityEvent(this, (byte) 67);
                            this.hurtServer(worldserver, this.damageSources().drown(), 2.0F);
                        }
                    } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                        this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
                    }

                    if (this.isPassenger() && this.getVehicle() != null && this.getVehicle().dismountsUnderwater()) {
                        this.stopRiding();
                    }
                } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                    this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
                }

                BlockPosition blockposition = this.blockPosition();

                if (!Objects.equal(this.lastPos, blockposition)) {
                    this.lastPos = blockposition;
                    this.onChangedBlock(worldserver, blockposition);
                }
            }
        }

        if (this.hurtTime > 0) {
            --this.hurtTime;
        }

        if (this.invulnerableTime > 0 && !(this instanceof EntityPlayer)) {
            --this.invulnerableTime;
        }

        if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerMemoryTime > 0) {
            --this.lastHurtByPlayerMemoryTime;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        EntityLiving entityliving = this.getLastHurtByMob();

        if (entityliving != null) {
            if (!entityliving.isAlive()) {
                this.setLastHurtByMob((EntityLiving) null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob((EntityLiving) null);
            }
        }

        this.tickEffects();
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        gameprofilerfiller.pop();
    }

    @Override
    protected float getBlockSpeedFactor() {
        return MathHelper.lerp((float) this.getAttributeValue(GenericAttributes.MOVEMENT_EFFICIENCY), super.getBlockSpeedFactor(), 1.0F);
    }

    public float getLuck() {
        return 0.0F;
    }

    protected void removeFrost() {
        AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.MOVEMENT_SPEED);

        if (attributemodifiable != null) {
            if (attributemodifiable.getModifier(EntityLiving.SPEED_MODIFIER_POWDER_SNOW_ID) != null) {
                attributemodifiable.removeModifier(EntityLiving.SPEED_MODIFIER_POWDER_SNOW_ID);
            }

        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = this.getTicksFrozen();

            if (i > 0) {
                AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.MOVEMENT_SPEED);

                if (attributemodifiable == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();

                attributemodifiable.addTransientModifier(new AttributeModifier(EntityLiving.SPEED_MODIFIER_POWDER_SNOW_ID, (double) f, AttributeModifier.Operation.ADD_VALUE));
            }
        }

    }

    protected void onChangedBlock(WorldServer worldserver, BlockPosition blockposition) {
        EnchantmentManager.runLocationChangedEffects(worldserver, this);
    }

    public boolean isBaby() {
        return false;
    }

    public float getAgeScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public final float getScale() {
        AttributeMapBase attributemapbase = this.getAttributes();

        return attributemapbase == null ? 1.0F : this.sanitizeScale((float) attributemapbase.getValue(GenericAttributes.SCALE));
    }

    protected float sanitizeScale(float f) {
        return f;
    }

    public boolean isAffectedByFluids() {
        return true;
    }

    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        }

    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    protected int decreaseAirSupply(int i) {
        AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.OXYGEN_BONUS);
        double d0;

        if (attributemodifiable != null) {
            d0 = attributemodifiable.getValue();
        } else {
            d0 = 0.0D;
        }

        return d0 > 0.0D && this.random.nextDouble() >= 1.0D / (d0 + 1.0D) ? i : i - 1;
    }

    protected int increaseAirSupply(int i) {
        return Math.min(i + 4, this.getMaxAirSupply());
    }

    public final int getExperienceReward(WorldServer worldserver, @Nullable Entity entity) {
        return EnchantmentManager.processMobExperience(worldserver, entity, this, this.getBaseExperienceReward(worldserver));
    }

    protected int getBaseExperienceReward(WorldServer worldserver) {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Nullable
    public EntityLiving getLastHurtByMob() {
        return (EntityLiving) EntityReference.get(this.lastHurtByMob, this.level(), EntityLiving.class);
    }

    @Nullable
    public EntityHuman getLastHurtByPlayer() {
        return (EntityHuman) EntityReference.get(this.lastHurtByPlayer, this.level(), EntityHuman.class);
    }

    @Override
    public EntityLiving getLastAttacker() {
        return this.getLastHurtByMob();
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(EntityHuman entityhuman, int i) {
        this.setLastHurtByPlayer(new EntityReference(entityhuman), i);
    }

    public void setLastHurtByPlayer(UUID uuid, int i) {
        this.setLastHurtByPlayer(new EntityReference(uuid), i);
    }

    private void setLastHurtByPlayer(EntityReference<EntityHuman> entityreference, int i) {
        this.lastHurtByPlayer = entityreference;
        this.lastHurtByPlayerMemoryTime = i;
    }

    public void setLastHurtByMob(@Nullable EntityLiving entityliving) {
        this.lastHurtByMob = entityliving != null ? new EntityReference(entityliving) : null;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public EntityLiving getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity entity) {
        if (entity instanceof EntityLiving) {
            this.lastHurtMob = (EntityLiving) entity;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int i) {
        this.noActionTime = i;
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(boolean flag) {
        this.discardFriction = flag;
    }

    protected boolean doesEmitEquipEvent(EnumItemSlot enumitemslot) {
        return true;
    }

    public void onEquipItem(EnumItemSlot enumitemslot, ItemStack itemstack, ItemStack itemstack1) {
        // CraftBukkit start
        onEquipItem(enumitemslot, itemstack, itemstack1, false);
    }

    public void onEquipItem(EnumItemSlot enumitemslot, ItemStack itemstack, ItemStack itemstack1, boolean silent) {
        // CraftBukkit end
        if (!this.level().isClientSide() && !this.isSpectator()) {
            if (!ItemStack.isSameItemSameComponents(itemstack, itemstack1) && !this.firstTick) {
                Equippable equippable = (Equippable) itemstack1.get(DataComponents.EQUIPPABLE);

                if (!this.isSilent() && equippable != null && enumitemslot == equippable.slot() && !silent) { // CraftBukkit
                    this.level().playSeededSound((Entity) null, this.getX(), this.getY(), this.getZ(), this.getEquipSound(enumitemslot, itemstack1, equippable), this.getSoundSource(), 1.0F, 1.0F, this.random.nextLong());
                }

                if (this.doesEmitEquipEvent(enumitemslot)) {
                    this.gameEvent(equippable != null ? GameEvent.EQUIP : GameEvent.UNEQUIP);
                }

            }
        }
    }

    protected Holder<SoundEffect> getEquipSound(EnumItemSlot enumitemslot, ItemStack itemstack, Equippable equippable) {
        return equippable.equipSound();
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(entity_removalreason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        // CraftBukkit end
        if (entity_removalreason == Entity.RemovalReason.KILLED || entity_removalreason == Entity.RemovalReason.DISCARDED) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                this.triggerOnDeathMobEffects(worldserver, entity_removalreason);
            }
        }

        super.remove(entity_removalreason, cause); // CraftBukkit
        this.brain.clearMemories();
    }

    protected void triggerOnDeathMobEffects(WorldServer worldserver, Entity.RemovalReason entity_removalreason) {
        for (MobEffect mobeffect : this.getActiveEffects()) {
            mobeffect.onMobRemoved(worldserver, this, entity_removalreason);
        }

        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH); // CraftBukkit
        this.activeEffects.clear();
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        nbttagcompound.putFloat("Health", this.getHealth());
        nbttagcompound.putShort("HurtTime", (short) this.hurtTime);
        nbttagcompound.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        nbttagcompound.putShort("DeathTime", (short) this.deathTime);
        nbttagcompound.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        nbttagcompound.put("attributes", this.getAttributes().save());
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        if (!this.activeEffects.isEmpty()) {
            nbttagcompound.store("active_effects", MobEffect.CODEC.listOf(), registryops, List.copyOf(this.activeEffects.values()));
        }

        nbttagcompound.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent((blockposition) -> {
            nbttagcompound.store("sleeping_pos", BlockPosition.CODEC, blockposition);
        });
        DataResult<NBTBase> dataresult = this.brain.<NBTBase>serializeStart(DynamicOpsNBT.INSTANCE);
        Logger logger = EntityLiving.LOGGER;

        java.util.Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbttagcompound.put("Brain", nbtbase);
        });
        if (this.lastHurtByPlayer != null) {
            this.lastHurtByPlayer.store(nbttagcompound, "last_hurt_by_player");
            nbttagcompound.putInt("last_hurt_by_player_memory_time", this.lastHurtByPlayerMemoryTime);
        }

        if (this.lastHurtByMob != null) {
            this.lastHurtByMob.store(nbttagcompound, "last_hurt_by_mob");
            nbttagcompound.putInt("ticks_since_last_hurt_by_mob", this.tickCount - this.lastHurtByMobTimestamp);
        }

        if (!this.equipment.isEmpty()) {
            nbttagcompound.store("equipment", EntityEquipment.CODEC, registryops, this.equipment);
        }

    }

    @Nullable
    public EntityItem drop(ItemStack itemstack, boolean flag, boolean flag1) {
        // CraftBukkit start - SPIGOT-2942: Add boolean to call event
        return drop(itemstack, flag, flag1, true);
    }

    @Nullable
    public EntityItem drop(ItemStack itemstack, boolean flag, boolean flag1, boolean callEvent) {
        // CraftBukkit end
        if (itemstack.isEmpty()) {
            return null;
        } else if (this.level().isClientSide) {
            this.swing(EnumHand.MAIN_HAND);
            return null;
        } else {
            EntityItem entityitem = this.createItemStackToDrop(itemstack, flag, flag1);

            if (entityitem != null) {
                // CraftBukkit start - fire PlayerDropItemEvent
                if (callEvent && this instanceof EntityPlayer) {
                    Player player = (Player) this.getBukkitEntity();
                    org.bukkit.entity.Item drop = (org.bukkit.entity.Item) entityitem.getBukkitEntity();

                    PlayerDropItemEvent event = new PlayerDropItemEvent(player, drop);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        org.bukkit.inventory.ItemStack cur = player.getInventory().getItemInHand();
                        if (flag1 && (cur == null || cur.getAmount() == 0)) {
                            // The complete stack was dropped
                            player.getInventory().setItemInHand(drop.getItemStack());
                        } else if (flag1 && cur.isSimilar(drop.getItemStack()) && cur.getAmount() < cur.getMaxStackSize() && drop.getItemStack().getAmount() == 1) {
                            // Only one item is dropped
                            cur.setAmount(cur.getAmount() + 1);
                            player.getInventory().setItemInHand(cur);
                        } else {
                            // Fallback
                            player.getInventory().addItem(drop.getItemStack());
                        }
                        return null;
                    }
                }
                // CraftBukkit end
                this.level().addFreshEntity(entityitem);
            }

            return entityitem;
        }
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        this.internalSetAbsorptionAmount(nbttagcompound.getFloatOr("AbsorptionAmount", 0.0F));
        if (this.level() != null && !this.level().isClientSide) {
            Optional<NBTTagList> optional = nbttagcompound.getList("attributes"); // CraftBukkit - decompile error
            AttributeMapBase attributemapbase = this.getAttributes();

            java.util.Objects.requireNonNull(attributemapbase);
            optional.ifPresent(attributemapbase::load);
        }

        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);
        List<MobEffect> list = (List) nbttagcompound.read("active_effects", MobEffect.CODEC.listOf(), registryops).orElse(List.of());

        this.activeEffects.clear();

        for (MobEffect mobeffect : list) {
            this.activeEffects.put(mobeffect.getEffect(), mobeffect);
        }

        // CraftBukkit start
        if (nbttagcompound.contains("Bukkit.MaxHealth")) {
            NBTBase nbtbase = nbttagcompound.get("Bukkit.MaxHealth");
            if (nbtbase.getId() == 5) {
                this.getAttribute(GenericAttributes.MAX_HEALTH).setBaseValue(((NBTTagFloat) nbtbase).doubleValue());
            } else if (nbtbase.getId() == 3) {
                this.getAttribute(GenericAttributes.MAX_HEALTH).setBaseValue(((NBTTagInt) nbtbase).doubleValue());
            }
        }
        // CraftBukkit end

        this.setHealth(nbttagcompound.getFloatOr("Health", this.getMaxHealth()));
        this.hurtTime = nbttagcompound.getShortOr("HurtTime", (short) 0);
        this.deathTime = nbttagcompound.getShortOr("DeathTime", (short) 0);
        this.lastHurtByMobTimestamp = nbttagcompound.getIntOr("HurtByTimestamp", 0);
        nbttagcompound.getString("Team").ifPresent((s) -> {
            Scoreboard scoreboard = this.level().getScoreboard();
            ScoreboardTeam scoreboardteam = scoreboard.getPlayerTeam(s);
            boolean flag = scoreboardteam != null && scoreboard.addPlayerToTeam(this.getStringUUID(), scoreboardteam);

            if (!flag) {
                EntityLiving.LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", s);
            }

        });
        this.setSharedFlag(7, nbttagcompound.getBooleanOr("FallFlying", false));
        nbttagcompound.read("sleeping_pos", BlockPosition.CODEC).ifPresentOrElse((blockposition) -> {
            this.setSleepingPos(blockposition);
            this.entityData.set(EntityLiving.DATA_POSE, EntityPose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockposition);
            }

        }, this::clearSleepingPos);
        nbttagcompound.getCompound("Brain").ifPresent((nbttagcompound1) -> {
            this.brain = this.makeBrain(new Dynamic(DynamicOpsNBT.INSTANCE, nbttagcompound1));
        });
        this.lastHurtByPlayer = EntityReference.<EntityHuman>read(nbttagcompound, "last_hurt_by_player");
        this.lastHurtByPlayerMemoryTime = nbttagcompound.getIntOr("last_hurt_by_player_memory_time", 0);
        this.lastHurtByMob = EntityReference.<EntityLiving>read(nbttagcompound, "last_hurt_by_mob");
        this.lastHurtByMobTimestamp = nbttagcompound.getIntOr("ticks_since_last_hurt_by_mob", 0) + this.tickCount;
        this.equipment.setAll((EntityEquipment) nbttagcompound.read("equipment", EntityEquipment.CODEC, registryops).orElseGet(EntityEquipment::new));
    }

    // CraftBukkit start
    private boolean isTickingEffects = false;
    private List<ProcessableEffect> effectsToProcess = Lists.newArrayList();

    private static class ProcessableEffect {

        private Holder<MobEffectList> type;
        private MobEffect effect;
        private final EntityPotionEffectEvent.Cause cause;

        private ProcessableEffect(MobEffect effect, EntityPotionEffectEvent.Cause cause) {
            this.effect = effect;
            this.cause = cause;
        }

        private ProcessableEffect(Holder<MobEffectList> type, EntityPotionEffectEvent.Cause cause) {
            this.type = type;
            this.cause = cause;
        }
    }
    // CraftBukkit end

    protected void tickEffects() {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            Iterator<Holder<MobEffectList>> iterator = this.activeEffects.keySet().iterator();

            isTickingEffects = true; // CraftBukkit
            try {
                while (iterator.hasNext()) {
                    Holder<MobEffectList> holder = (Holder) iterator.next();
                    MobEffect mobeffect = (MobEffect) this.activeEffects.get(holder);

                    if (!mobeffect.tickServer(worldserver, this, () -> {
                        this.onEffectUpdated(mobeffect, true, (Entity) null);
                    })) {
                        // CraftBukkit start
                        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobeffect, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.EXPIRATION);
                        if (event.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end
                        iterator.remove();
                        this.onEffectsRemoved(List.of(mobeffect));
                    } else if (mobeffect.getDuration() % 600 == 0) {
                        this.onEffectUpdated(mobeffect, false, (Entity) null);
                    }
                }
            } catch (ConcurrentModificationException concurrentmodificationexception) {
                ;
            }
            // CraftBukkit start
            isTickingEffects = false;
            for (ProcessableEffect e : effectsToProcess) {
                if (e.effect != null) {
                    addEffect(e.effect, e.cause);
                } else {
                    removeEffect(e.type, e.cause);
                }
            }
            effectsToProcess.clear();
            // CraftBukkit end

            if (this.effectsDirty) {
                this.updateInvisibilityStatus();
                this.updateGlowingStatus();
                this.effectsDirty = false;
            }
        } else {
            for (MobEffect mobeffect1 : this.activeEffects.values()) {
                mobeffect1.tickClient();
            }

            List<ParticleParam> list = (List) this.entityData.get(EntityLiving.DATA_EFFECT_PARTICLES);

            if (!list.isEmpty()) {
                boolean flag = (Boolean) this.entityData.get(EntityLiving.DATA_EFFECT_AMBIENCE_ID);
                int i = this.isInvisible() ? 15 : 4;
                int j = flag ? 5 : 1;

                if (this.random.nextInt(i * j) == 0) {
                    this.level().addParticle((ParticleParam) SystemUtils.getRandom(list, this.random), this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 1.0D, 1.0D, 1.0D);
                }
            }
        }

    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
            this.updateSynchronizedMobEffectParticles();
        }
    }

    private void updateSynchronizedMobEffectParticles() {
        List<ParticleParam> list = this.activeEffects.values().stream().filter(MobEffect::isVisible).map(MobEffect::getParticleOptions).toList();

        this.entityData.set(EntityLiving.DATA_EFFECT_PARTICLES, list);
        this.entityData.set(EntityLiving.DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(this.activeEffects.values()));
    }

    private void updateGlowingStatus() {
        boolean flag = this.isCurrentlyGlowing();

        if (this.getSharedFlag(6) != flag) {
            this.setSharedFlag(6, flag);
        }

    }

    public double getVisibilityPercent(@Nullable Entity entity) {
        double d0 = 1.0D;

        if (this.isDiscrete()) {
            d0 *= 0.8D;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();

            if (f < 0.1F) {
                f = 0.1F;
            }

            d0 *= 0.7D * (double) f;
        }

        if (entity != null) {
            ItemStack itemstack = this.getItemBySlot(EnumItemSlot.HEAD);
            EntityTypes<?> entitytypes = entity.getType();

            if (entitytypes == EntityTypes.SKELETON && itemstack.is(Items.SKELETON_SKULL) || entitytypes == EntityTypes.ZOMBIE && itemstack.is(Items.ZOMBIE_HEAD) || entitytypes == EntityTypes.PIGLIN && itemstack.is(Items.PIGLIN_HEAD) || entitytypes == EntityTypes.PIGLIN_BRUTE && itemstack.is(Items.PIGLIN_HEAD) || entitytypes == EntityTypes.CREEPER && itemstack.is(Items.CREEPER_HEAD)) {
                d0 *= 0.5D;
            }
        }

        return d0;
    }

    public boolean canAttack(EntityLiving entityliving) {
        return entityliving instanceof EntityHuman && this.level().getDifficulty() == EnumDifficulty.PEACEFUL ? false : entityliving.canBeSeenAsEnemy();
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffect> collection) {
        for (MobEffect mobeffect : collection) {
            if (mobeffect.isVisible() && !mobeffect.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    protected void removeEffectParticles() {
        this.entityData.set(EntityLiving.DATA_EFFECT_PARTICLES, List.of());
    }

    // CraftBukkit start
    public boolean removeAllEffects() {
        return removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeAllEffects(EntityPotionEffectEvent.Cause cause) {
        // CraftBukkit end
        if (this.level().isClientSide) {
            return false;
        } else if (this.activeEffects.isEmpty()) {
            return false;
        } else {
            // CraftBukkit start
            List<MobEffect> toRemove = new LinkedList<>();
            Iterator<MobEffect> iterator = this.activeEffects.values().iterator();

            while (iterator.hasNext()) {
                MobEffect effect = iterator.next();
                EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause, EntityPotionEffectEvent.Action.CLEARED);
                if (event.isCancelled()) {
                    continue;
                }

                iterator.remove();
                toRemove.add(effect);
            }

            this.onEffectsRemoved(toRemove);
            return !toRemove.isEmpty();
            // CraftBukkit end
        }
    }

    public Collection<MobEffect> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<Holder<MobEffectList>, MobEffect> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(Holder<MobEffectList> holder) {
        return this.activeEffects.containsKey(holder);
    }

    @Nullable
    public MobEffect getEffect(Holder<MobEffectList> holder) {
        return (MobEffect) this.activeEffects.get(holder);
    }

    public float getEffectBlendFactor(Holder<MobEffectList> holder, float f) {
        MobEffect mobeffect = this.getEffect(holder);

        return mobeffect != null ? mobeffect.getBlendFactor(this, f) : 0.0F;
    }

    public final boolean addEffect(MobEffect mobeffect) {
        return this.addEffect(mobeffect, (Entity) null);
    }

    // CraftBukkit start
    public boolean addEffect(MobEffect mobeffect, EntityPotionEffectEvent.Cause cause) {
        return this.addEffect(mobeffect, (Entity) null, cause);
    }

    public boolean addEffect(MobEffect mobeffect, @Nullable Entity entity) {
        return this.addEffect(mobeffect, entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean addEffect(MobEffect mobeffect, @Nullable Entity entity, EntityPotionEffectEvent.Cause cause) {
        if (isTickingEffects) {
            effectsToProcess.add(new ProcessableEffect(mobeffect, cause));
            return true;
        }
        // CraftBukkit end

        if (!this.canBeAffected(mobeffect)) {
            return false;
        } else {
            MobEffect mobeffect1 = (MobEffect) this.activeEffects.get(mobeffect.getEffect());
            boolean flag = false;

            // CraftBukkit start
            boolean override = false;
            if (mobeffect1 != null) {
                override = new MobEffect(mobeffect1).update(mobeffect);
            }

            EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobeffect1, mobeffect, cause, override);
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end

            if (mobeffect1 == null) {
                this.activeEffects.put(mobeffect.getEffect(), mobeffect);
                this.onEffectAdded(mobeffect, entity);
                flag = true;
                mobeffect.onEffectAdded(this);
                // CraftBukkit start
            } else if (event.isOverride()) {
                mobeffect1.update(mobeffect);
                this.onEffectUpdated(mobeffect1, true, entity);
                // CraftBukkit end
                flag = true;
            }

            mobeffect.onEffectStarted(this);
            return flag;
        }
    }

    public boolean canBeAffected(MobEffect mobeffect) {
        return this.getType().is(TagsEntity.IMMUNE_TO_INFESTED) ? !mobeffect.is(MobEffects.INFESTED) : (this.getType().is(TagsEntity.IMMUNE_TO_OOZING) ? !mobeffect.is(MobEffects.OOZING) : (!this.getType().is(TagsEntity.IGNORES_POISON_AND_REGEN) ? true : !mobeffect.is(MobEffects.REGENERATION) && !mobeffect.is(MobEffects.POISON)));
    }

    public void forceAddEffect(MobEffect mobeffect, @Nullable Entity entity) {
        if (this.canBeAffected(mobeffect)) {
            MobEffect mobeffect1 = (MobEffect) this.activeEffects.put(mobeffect.getEffect(), mobeffect);

            if (mobeffect1 == null) {
                this.onEffectAdded(mobeffect, entity);
            } else {
                mobeffect.copyBlendState(mobeffect1);
                this.onEffectUpdated(mobeffect, true, entity);
            }

        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getType().is(TagsEntity.INVERTED_HEALING_AND_HARM);
    }

    // CraftBukkit start
    @Nullable
    public final MobEffect removeEffectNoUpdate(Holder<MobEffectList> holder) {
        return removeEffectNoUpdate(holder, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    @Nullable
    public final MobEffect removeEffectNoUpdate(Holder<MobEffectList> holder, EntityPotionEffectEvent.Cause cause) {
        if (isTickingEffects) {
            effectsToProcess.add(new ProcessableEffect(holder, cause));
            return null;
        }

        MobEffect effect = this.activeEffects.get(holder);
        if (effect == null) {
            return null;
        }

        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause);
        if (event.isCancelled()) {
            return null;
        }

        return (MobEffect) this.activeEffects.remove(holder);
    }

    public boolean removeEffect(Holder<MobEffectList> holder) {
        return removeEffect(holder, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeEffect(Holder<MobEffectList> holder, EntityPotionEffectEvent.Cause cause) {
        MobEffect mobeffect = this.removeEffectNoUpdate(holder, cause);
        // CraftBukkit end

        if (mobeffect != null) {
            this.onEffectsRemoved(List.of(mobeffect));
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffect mobeffect, @Nullable Entity entity) {
        if (!this.level().isClientSide) {
            this.effectsDirty = true;
            ((MobEffectList) mobeffect.getEffect().value()).addAttributeModifiers(this.getAttributes(), mobeffect.getAmplifier());
            this.sendEffectToPassengers(mobeffect);
        }

    }

    public void sendEffectToPassengers(MobEffect mobeffect) {
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof EntityPlayer entityplayer) {
                entityplayer.connection.send(new PacketPlayOutEntityEffect(this.getId(), mobeffect, false));
            }
        }

    }

    protected void onEffectUpdated(MobEffect mobeffect, boolean flag, @Nullable Entity entity) {
        if (!this.level().isClientSide) {
            this.effectsDirty = true;
            if (flag) {
                MobEffectList mobeffectlist = (MobEffectList) mobeffect.getEffect().value();

                mobeffectlist.removeAttributeModifiers(this.getAttributes());
                mobeffectlist.addAttributeModifiers(this.getAttributes(), mobeffect.getAmplifier());
                this.refreshDirtyAttributes();
            }

            this.sendEffectToPassengers(mobeffect);
        }
    }

    protected void onEffectsRemoved(Collection<MobEffect> collection) {
        if (!this.level().isClientSide) {
            this.effectsDirty = true;

            for (MobEffect mobeffect : collection) {
                ((MobEffectList) mobeffect.getEffect().value()).removeAttributeModifiers(this.getAttributes());

                for (Entity entity : this.getPassengers()) {
                    if (entity instanceof EntityPlayer) {
                        EntityPlayer entityplayer = (EntityPlayer) entity;

                        entityplayer.connection.send(new PacketPlayOutRemoveEntityEffect(this.getId(), mobeffect.getEffect()));
                    }
                }
            }

            this.refreshDirtyAttributes();
        }
    }

    private void refreshDirtyAttributes() {
        Set<AttributeModifiable> set = this.getAttributes().getAttributesToUpdate();

        for (AttributeModifiable attributemodifiable : set) {
            this.onAttributeUpdated(attributemodifiable.getAttribute());
        }

        set.clear();
    }

    protected void onAttributeUpdated(Holder<AttributeBase> holder) {
        if (holder.is(GenericAttributes.MAX_HEALTH)) {
            float f = this.getMaxHealth();

            if (this.getHealth() > f) {
                this.setHealth(f);
            }
        } else if (holder.is(GenericAttributes.MAX_ABSORPTION)) {
            float f1 = this.getMaxAbsorption();

            if (this.getAbsorptionAmount() > f1) {
                this.setAbsorptionAmount(f1);
            }
        } else if (holder.is(GenericAttributes.SCALE)) {
            this.refreshDimensions();
        }

    }

    // CraftBukkit start - Delegate so we can handle providing a reason for health being regained
    public void heal(float f) {
        heal(f, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    public void heal(float f, EntityRegainHealthEvent.RegainReason regainReason) {
        float f1 = this.getHealth();

        if (f1 > 0.0F) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), f, regainReason);
            // Suppress during worldgen
            if (this.valid) {
                this.level().getCraftServer().getPluginManager().callEvent(event);
            }

            if (!event.isCancelled()) {
                this.setHealth((float) (this.getHealth() + event.getAmount()));
            }
            // CraftBukkit end
        }

    }

    public float getHealth() {
        // CraftBukkit start - Use unscaled health
        if (this instanceof EntityPlayer) {
            return (float) ((EntityPlayer) this).getBukkitEntity().getHealth();
        }
        // CraftBukkit end
        return (Float) this.entityData.get(EntityLiving.DATA_HEALTH_ID);
    }

    public void setHealth(float f) {
        // CraftBukkit start - Handle scaled health
        if (this instanceof EntityPlayer) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = ((EntityPlayer) this).getBukkitEntity();
            // Squeeze
            if (f < 0.0F) {
                player.setRealHealth(0.0D);
            } else if (f > player.getMaxHealth()) {
                player.setRealHealth(player.getMaxHealth());
            } else {
                player.setRealHealth(f);
            }

            player.updateScaledHealth(false);
            return;
        }
        // CraftBukkit end
        this.entityData.set(EntityLiving.DATA_HEALTH_ID, MathHelper.clamp(f, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else if (this.isRemoved() || this.dead || this.getHealth() <= 0.0F) { // CraftBukkit - Don't allow entities that got set to dead/killed elsewhere to get damaged and die
            return false;
        } else if (damagesource.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            if (f < 0.0F) {
                f = 0.0F;
            }

            float f1 = f;
            /* // CraftBukkit start - Moved into handleEntityDamage(DamageSource, float) for get f and actuallyHurt(DamageSource, float, EntityDamageEvent) for handle damage
            float f2 = this.applyItemBlocking(worldserver, damagesource, f);

            f -= f2;
            boolean flag = f2 > 0.0F;
             */ // CraftBukkit end

            // CraftBukkit - Moved into handleEntityDamage(DamageSource, float) for get f
            if (false && damagesource.is(DamageTypeTags.IS_FREEZING) && this.getType().is(TagsEntity.FREEZE_HURTS_EXTRA_TYPES)) {
                f *= 5.0F;
            }

            // CraftBukkit - Moved into handleEntityDamage(DamageSource, float) for get f and actuallyHurt(DamageSource, float, EntityDamageEvent) for handle damage
            if (false && damagesource.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EnumItemSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damagesource, f);
                f *= 0.75F;
            }

            // CraftBukkit start
            EntityDamageEvent event = handleEntityDamage(damagesource, f);
            f = 0;
            f += (float) event.getDamage(DamageModifier.BASE);
            f += (float) event.getDamage(DamageModifier.FREEZING);
            f += (float) event.getDamage(DamageModifier.HARD_HAT);

            float f2 = (float) -event.getDamage(DamageModifier.BLOCKING);

            f -= f2;
            boolean flag = f2 > 0.0F;
            // CraftBukkit end

            if (Float.isNaN(f) || Float.isInfinite(f)) {
                f = Float.MAX_VALUE;
            }

            boolean flag1 = true;

            if ((float) this.invulnerableTime > (float) this.invulnerableDuration / 2.0F && !damagesource.is(DamageTypeTags.BYPASSES_COOLDOWN)) { // CraftBukkit - restore use of maxNoDamageTicks
                if (f <= this.lastHurt) {
                    return false;
                }

                // CraftBukkit start
                if (!this.actuallyHurt(worldserver, damagesource, (float) event.getFinalDamage() - this.lastHurt, event)) {
                    return false;
                }
                // CraftBukkit end
                this.lastHurt = f;
                flag1 = false;
            } else {
                // CraftBukkit start
                if (!this.actuallyHurt(worldserver, damagesource, (float) event.getFinalDamage(), event)) {
                    return false;
                }
                this.lastHurt = f;
                this.invulnerableTime = this.invulnerableDuration; // CraftBukkit - restore use of maxNoDamageTicks
                // this.actuallyHurt(worldserver, damagesource, f);
                // CraftBukkit end
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            this.resolveMobResponsibleForDamage(damagesource);
            this.resolvePlayerResponsibleForDamage(damagesource);
            if (flag1) {
                BlocksAttacks blocksattacks = (BlocksAttacks) this.getUseItem().get(DataComponents.BLOCKS_ATTACKS);

                if (flag && blocksattacks != null) {
                    blocksattacks.onBlocked(worldserver, this);
                } else {
                    worldserver.broadcastDamageEvent(this, damagesource);
                }

                if (!damagesource.is(DamageTypeTags.NO_IMPACT) && !flag) { // CraftBukkit - Prevent marking hurt if the damage is blocked
                    this.markHurt();
                }

                if (!damagesource.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double d0 = 0.0D;
                    double d1 = 0.0D;
                    Entity entity = damagesource.getDirectEntity();

                    if (entity instanceof IProjectile) {
                        IProjectile iprojectile = (IProjectile) entity;
                        DoubleDoubleImmutablePair doubledoubleimmutablepair = iprojectile.calculateHorizontalHurtKnockbackDirection(this, damagesource);

                        d0 = -doubledoubleimmutablepair.leftDouble();
                        d1 = -doubledoubleimmutablepair.rightDouble();
                    } else if (damagesource.getSourcePosition() != null) {
                        d0 = damagesource.getSourcePosition().x() - this.getX();
                        d1 = damagesource.getSourcePosition().z() - this.getZ();
                    }

                    this.knockback((double) 0.4F, d0, d1, entity, entity == null ? EntityKnockbackEvent.KnockbackCause.DAMAGE : EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK); // CraftBukkit
                    if (!flag) {
                        this.indicateDamage(d0, d1);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(damagesource)) {
                    if (flag1) {
                        this.makeSound(this.getDeathSound());
                        this.playSecondaryHurtSound(damagesource);
                    }

                    this.die(damagesource);
                }
            } else if (flag1) {
                this.playHurtSound(damagesource);
                this.playSecondaryHurtSound(damagesource);
            }

            boolean flag2 = !flag; // CraftBukkit - Ensure to return false if damage is blocked

            if (flag2) {
                this.lastDamageSource = damagesource;
                this.lastDamageStamp = this.level().getGameTime();

                for (MobEffect mobeffect : new LinkedList<>(this.getActiveEffects())) { // CraftBukkit - SPIGOT-7999: copy to prevent CME in unrelated events
                    mobeffect.onMobHurt(worldserver, this, damagesource, f);
                }
            }

            if (this instanceof EntityPlayer) {
                EntityPlayer entityplayer = (EntityPlayer) this;

                CriterionTriggers.ENTITY_HURT_PLAYER.trigger(entityplayer, damagesource, f1, f, flag);
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    entityplayer.awardStat(StatisticList.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f2 * 10.0F));
                }
            }

            Entity entity1 = damagesource.getEntity();

            if (entity1 instanceof EntityPlayer) {
                EntityPlayer entityplayer1 = (EntityPlayer) entity1;

                CriterionTriggers.PLAYER_HURT_ENTITY.trigger(entityplayer1, this, damagesource, f1, f, flag);
            }

            return flag2;
        }
    }

    public float applyItemBlocking(WorldServer worldserver, DamageSource damagesource, float f) {
        // CraftBukkit start
        return actuallyDoItemBlocking(worldserver, damagesource, calculateItemBlocking(damagesource, f));
    }

    private float calculateItemBlocking(DamageSource damagesource, float f) {
        // CraftBukkit end
        if (f <= 0.0F) {
            return 0.0F;
        } else {
            ItemStack itemstack = this.getItemBlockingWith();

            if (itemstack == null) {
                return 0.0F;
            } else {
                BlocksAttacks blocksattacks = (BlocksAttacks) itemstack.get(DataComponents.BLOCKS_ATTACKS);

                if (blocksattacks != null) {
                    Optional<TagKey<DamageType>> optional = blocksattacks.bypassedBy(); // CraftBukkit - decompile error

                    java.util.Objects.requireNonNull(damagesource);
                    if (!(Boolean) optional.map(damagesource::is).orElse(false)) {
                        Entity entity = damagesource.getDirectEntity();

                        if (entity instanceof EntityArrow) {
                            EntityArrow entityarrow = (EntityArrow) entity;

                            if (entityarrow.getPierceLevel() > 0) {
                                return 0.0F;
                            }
                        }

                        Vec3D vec3d = damagesource.getSourcePosition();
                        double d0;

                        if (vec3d != null) {
                            Vec3D vec3d1 = this.calculateViewVector(0.0F, this.getYHeadRot());
                            Vec3D vec3d2 = vec3d.subtract(this.position());

                            vec3d2 = (new Vec3D(vec3d2.x, 0.0D, vec3d2.z)).normalize();
                            d0 = Math.acos(vec3d2.dot(vec3d1));
                        } else {
                            d0 = (double) (float) Math.PI;
                        }

                        float f1 = blocksattacks.resolveBlockedDamage(damagesource, f, d0);

                        // CraftBukkit start
                        return f1;
                    }
                }

                return 0.0F;
            }
        }
    }

    private float actuallyDoItemBlocking(WorldServer worldserver, DamageSource damagesource, float f1) {
        {
            {
                ItemStack itemstack = this.getItemBlockingWith();

                if (itemstack != null) {
                    BlocksAttacks blocksattacks = (BlocksAttacks) itemstack.get(DataComponents.BLOCKS_ATTACKS);

                    if (blocksattacks != null) {
                        // CraftBukkit end
                        blocksattacks.hurtBlockingItem(this.level(), itemstack, this, this.getUsedItemHand(), f1);
                        if (!damagesource.is(DamageTypeTags.IS_PROJECTILE)) {
                            Entity entity1 = damagesource.getDirectEntity();

                            if (entity1 instanceof EntityLiving) {
                                EntityLiving entityliving = (EntityLiving) entity1;

                                this.blockUsingItem(worldserver, entityliving);
                            }
                        }

                        return f1;
                    }
                }

                return 0.0F;
            }
        }
    }

    private void playSecondaryHurtSound(DamageSource damagesource) {
        if (damagesource.is(DamageTypes.THORNS)) {
            SoundCategory soundcategory = this instanceof EntityHuman ? SoundCategory.PLAYERS : SoundCategory.HOSTILE;

            this.level().playSound((Entity) null, this.position().x, this.position().y, this.position().z, SoundEffects.THORNS_HIT, soundcategory);
        }

    }

    protected void resolveMobResponsibleForDamage(DamageSource damagesource) {
        Entity entity = damagesource.getEntity();

        if (entity instanceof EntityLiving entityliving) {
            if (!damagesource.is(DamageTypeTags.NO_ANGER) && (!damagesource.is(DamageTypes.WIND_CHARGE) || !this.getType().is(TagsEntity.NO_ANGER_FROM_WIND_CHARGE))) {
                this.setLastHurtByMob(entityliving);
            }
        }

    }

    @Nullable
    protected EntityHuman resolvePlayerResponsibleForDamage(DamageSource damagesource) {
        Entity entity = damagesource.getEntity();

        if (entity instanceof EntityHuman entityhuman) {
            this.setLastHurtByPlayer(entityhuman, 100);
        } else if (entity instanceof EntityWolf entitywolf) {
            if (entitywolf.isTame()) {
                if (entitywolf.getOwnerReference() != null) {
                    this.setLastHurtByPlayer(entitywolf.getOwnerReference().getUUID(), 100);
                } else {
                    this.lastHurtByPlayer = null;
                    this.lastHurtByPlayerMemoryTime = 0;
                }
            }
        }

        return (EntityHuman) EntityReference.get(this.lastHurtByPlayer, this.level(), EntityHuman.class);
    }

    protected void blockUsingItem(WorldServer worldserver, EntityLiving entityliving) {
        entityliving.blockedByItem(this);
    }

    protected void blockedByItem(EntityLiving entityliving) {
        entityliving.knockback(0.5D, entityliving.getX() - this.getX(), entityliving.getZ() - this.getZ(), null, EntityKnockbackEvent.KnockbackCause.SHIELD_BLOCK); // CraftBukkit
    }

    private boolean checkTotemDeathProtection(DamageSource damagesource) {
        if (damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            ItemStack itemstack = null;
            DeathProtection deathprotection = null;

            // CraftBukkit start
            EnumHand hand = null;
            ItemStack itemstack1 = ItemStack.EMPTY;
            for (EnumHand enumhand : EnumHand.values()) {
                itemstack1 = this.getItemInHand(enumhand);

                deathprotection = (DeathProtection) itemstack1.get(DataComponents.DEATH_PROTECTION);
                if (deathprotection != null) {
                    hand = enumhand; // CraftBukkit
                    itemstack = itemstack1.copy();
                    // itemstack1.shrink(1); // CraftBukkit
                    break;
                }
            }

            org.bukkit.inventory.EquipmentSlot handSlot = (hand != null) ? org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand) : null;
            EntityResurrectEvent event = new EntityResurrectEvent((LivingEntity) this.getBukkitEntity(), handSlot);
            event.setCancelled(itemstack == null);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                if (!itemstack1.isEmpty()) {
                    itemstack1.shrink(1);
                }
                if (itemstack != null && this instanceof EntityPlayer) {
                    // CraftBukkit end
                    EntityPlayer entityplayer = (EntityPlayer) this;

                    entityplayer.awardStat(StatisticList.ITEM_USED.get(itemstack.getItem()));
                    CriterionTriggers.USED_TOTEM.trigger(entityplayer, itemstack);
                    this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                }

                this.setHealth(1.0F);
                deathprotection.applyEffects(itemstack, this);
                this.level().broadcastEntityEvent(this, (byte) 35);
            }

            return deathprotection != null;
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level().getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource damagesource) {
        this.makeSound(this.getHurtSound(damagesource));
    }

    public void makeSound(@Nullable SoundEffect soundeffect) {
        if (soundeffect != null) {
            this.playSound(soundeffect, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    private void breakItem(ItemStack itemstack) {
        if (!itemstack.isEmpty()) {
            Holder<SoundEffect> holder = (Holder) itemstack.get(DataComponents.BREAK_SOUND);

            if (holder != null && !this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), holder.value(), this.getSoundSource(), 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F, false);
            }

            this.spawnItemParticles(itemstack, 5);
        }

    }

    public void die(DamageSource damagesource) {
        if (!this.isRemoved() && !this.dead) {
            Entity entity = damagesource.getEntity();
            EntityLiving entityliving = this.getKillCredit();

            if (entityliving != null) {
                entityliving.awardKillScore(this, damagesource);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            if (!this.level().isClientSide && this.hasCustomName()) {
                EntityLiving.LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString());
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (entity == null || entity.killedEntity(worldserver, this)) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    this.dropAllDeathLoot(worldserver, damagesource);
                    this.createWitherRose(entityliving);
                }

                this.level().broadcastEntityEvent(this, (byte) 3);
            }

            this.setPose(EntityPose.DYING);
        }
    }

    protected void createWitherRose(@Nullable EntityLiving entityliving) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            boolean flag = false;

            if (entityliving instanceof EntityWither) {
                if (worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    BlockPosition blockposition = this.blockPosition();
                    IBlockData iblockdata = Blocks.WITHER_ROSE.defaultBlockState();

                    if (this.level().getBlockState(blockposition).isAir() && iblockdata.canSurvive(this.level(), blockposition)) {
                        // CraftBukkit start - call EntityBlockFormEvent for Wither Rose
                        flag = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this.level(), blockposition, iblockdata, 3, this);
                        // CraftBukkit end
                    }
                }

                if (!flag) {
                    EntityItem entityitem = new EntityItem(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));

                    // CraftBukkit start
                    org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
                    CraftEventFactory.callEvent(event);
                    if (event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.level().addFreshEntity(entityitem);
                }
            }

        }
    }

    protected void dropAllDeathLoot(WorldServer worldserver, DamageSource damagesource) {
        boolean flag = this.lastHurtByPlayerMemoryTime > 0;

        this.dropEquipment(worldserver); // CraftBukkit - from below
        if (this.shouldDropLoot() && worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(worldserver, damagesource, flag);
            this.dropCustomDeathLoot(worldserver, damagesource, flag);
        }
        // CraftBukkit start - Call death event
        CraftEventFactory.callEntityDeathEvent(this, damagesource, this.drops);
        this.drops = new ArrayList<>();
        // CraftBukkit end

        // this.dropEquipment(worldserver);// CraftBukkit - moved up
        this.dropExperience(worldserver, damagesource.getEntity());
    }

    protected void dropEquipment(WorldServer worldserver) {}

    public int getExpReward(WorldServer worldserver, @Nullable Entity entity) { // CraftBukkit
        if (!this.wasExperienceConsumed() && (this.isAlwaysExperienceDropper() || this.lastHurtByPlayerMemoryTime > 0 && this.shouldDropExperience() && worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) {
            return this.getExperienceReward(worldserver, entity); // CraftBukkit
        }

        return 0; // CraftBukkit
    }

    protected void dropExperience(WorldServer worldserver, @Nullable Entity entity) {
        // CraftBukkit start - Update getExpReward() above if the removed if() changes!
        if (!(this instanceof net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon)) { // CraftBukkit - SPIGOT-2420: Special case ender dragon will drop the xp over time
            EntityExperienceOrb.award(worldserver, this.position(), this.expToDrop);
            this.expToDrop = 0;
        }
        // CraftBukkit end
    }

    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {}

    public long getLootTableSeed() {
        return 0L;
    }

    protected float getKnockback(Entity entity, DamageSource damagesource) {
        float f = (float) this.getAttributeValue(GenericAttributes.ATTACK_KNOCKBACK);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            return EnchantmentManager.modifyKnockback(worldserver, this.getWeaponItem(), entity, damagesource, f);
        } else {
            return f;
        }
    }

    protected void dropFromLootTable(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        Optional<ResourceKey<LootTable>> optional = this.getLootTable();

        if (!optional.isEmpty()) {
            LootTable loottable = worldserver.getServer().reloadableRegistries().getLootTable((ResourceKey) optional.get());
            LootParams.a lootparams_a = (new LootParams.a(worldserver)).withParameter(LootContextParameters.THIS_ENTITY, this).withParameter(LootContextParameters.ORIGIN, this.position()).withParameter(LootContextParameters.DAMAGE_SOURCE, damagesource).withOptionalParameter(LootContextParameters.ATTACKING_ENTITY, damagesource.getEntity()).withOptionalParameter(LootContextParameters.DIRECT_ATTACKING_ENTITY, damagesource.getDirectEntity());
            EntityHuman entityhuman = this.getLastHurtByPlayer();

            if (flag && entityhuman != null) {
                lootparams_a = lootparams_a.withParameter(LootContextParameters.LAST_DAMAGE_PLAYER, entityhuman).withLuck(entityhuman.getLuck());
            }

            LootParams lootparams = lootparams_a.create(LootContextParameterSets.ENTITY);

            loottable.getRandomItems(lootparams, this.getLootTableSeed(), (itemstack) -> {
                this.spawnAtLocation(worldserver, itemstack);
            });
        }
    }

    public boolean dropFromGiftLootTable(WorldServer worldserver, ResourceKey<LootTable> resourcekey, BiConsumer<WorldServer, ItemStack> biconsumer) {
        return this.dropFromLootTable(worldserver, resourcekey, (lootparams_a) -> {
            return lootparams_a.withParameter(LootContextParameters.ORIGIN, this.position()).withParameter(LootContextParameters.THIS_ENTITY, this).create(LootContextParameterSets.GIFT);
        }, biconsumer);
    }

    protected void dropFromShearingLootTable(WorldServer worldserver, ResourceKey<LootTable> resourcekey, ItemStack itemstack, BiConsumer<WorldServer, ItemStack> biconsumer) {
        this.dropFromLootTable(worldserver, resourcekey, (lootparams_a) -> {
            return lootparams_a.withParameter(LootContextParameters.ORIGIN, this.position()).withParameter(LootContextParameters.THIS_ENTITY, this).withParameter(LootContextParameters.TOOL, itemstack).create(LootContextParameterSets.SHEARING);
        }, biconsumer);
    }

    protected boolean dropFromLootTable(WorldServer worldserver, ResourceKey<LootTable> resourcekey, Function<LootParams.a, LootParams> function, BiConsumer<WorldServer, ItemStack> biconsumer) {
        LootTable loottable = worldserver.getServer().reloadableRegistries().getLootTable(resourcekey);
        LootParams lootparams = (LootParams) function.apply(new LootParams.a(worldserver));
        List<ItemStack> list = loottable.getRandomItems(lootparams);

        if (!list.isEmpty()) {
            list.forEach((itemstack) -> {
                biconsumer.accept(worldserver, itemstack);
            });
            return true;
        } else {
            return false;
        }
    }

    public void knockback(double d0, double d1, double d2) {
        // CraftBukkit start - EntityKnockbackEvent
        knockback(d0, d1, d2, null, EntityKnockbackEvent.KnockbackCause.UNKNOWN);
    }

    public void knockback(double d0, double d1, double d2, Entity attacker, EntityKnockbackEvent.KnockbackCause cause) {
        d0 *= 1.0D - this.getAttributeValue(GenericAttributes.KNOCKBACK_RESISTANCE);
        if (true || d0 > 0.0D) { // CraftBukkit - Call event even when force is 0
            //this.hasImpulse = true; // CraftBukkit - Move down

            Vec3D vec3d;

            for (vec3d = this.getDeltaMovement(); d1 * d1 + d2 * d2 < (double) 1.0E-5F; d2 = (Math.random() - Math.random()) * 0.01D) {
                d1 = (Math.random() - Math.random()) * 0.01D;
            }

            Vec3D vec3d1 = (new Vec3D(d1, 0.0D, d2)).normalize().scale(d0);

            EntityKnockbackEvent event = CraftEventFactory.callEntityKnockbackEvent((org.bukkit.craftbukkit.entity.CraftLivingEntity) this.getBukkitEntity(), attacker, cause, d0, vec3d1, vec3d.x / 2.0D - vec3d1.x, this.onGround() ? Math.min(0.4D, vec3d.y / 2.0D + d0) : vec3d.y, vec3d.z / 2.0D - vec3d1.z);
            if (event.isCancelled()) {
                return;
            }

            this.hasImpulse = true;
            this.setDeltaMovement(event.getFinalKnockback().getX(), event.getFinalKnockback().getY(), event.getFinalKnockback().getZ());
            // CraftBukkit end
        }
    }

    public void indicateDamage(double d0, double d1) {}

    @Nullable
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.GENERIC_HURT;
    }

    @Nullable
    protected SoundEffect getDeathSound() {
        return SoundEffects.GENERIC_DEATH;
    }

    private SoundEffect getFallDamageSound(int i) {
        return i > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    public float getHurtDir() {
        return 0.0F;
    }

    protected AxisAlignedBB getHitbox() {
        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        Entity entity = this.getVehicle();

        if (entity != null) {
            Vec3D vec3d = entity.getPassengerRidingPosition(this);

            return axisalignedbb.setMinY(Math.max(vec3d.y, axisalignedbb.minY));
        } else {
            return axisalignedbb;
        }
    }

    public Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments(EnumItemSlot enumitemslot) {
        return (Map) this.activeLocationDependentEnchantments.computeIfAbsent(enumitemslot, (enumitemslot1) -> {
            return new Reference2ObjectArrayMap();
        });
    }

    public EntityLiving.a getFallSounds() {
        return new EntityLiving.a(SoundEffects.GENERIC_SMALL_FALL, SoundEffects.GENERIC_BIG_FALL);
    }

    // CraftBukkit start - Add delegate methods
    public SoundEffect getHurtSound0(DamageSource damagesource) {
        return getHurtSound(damagesource);
    }

    public SoundEffect getDeathSound0() {
        return getDeathSound();
    }

    public SoundEffect getFallDamageSound0(int fallHeight) {
        return getFallDamageSound(fallHeight);
    }
    // CraftBukkit end

    public Optional<BlockPosition> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPosition blockposition = this.blockPosition();
            IBlockData iblockdata = this.getInBlockState();

            if (iblockdata.is(TagsBlock.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockposition);
                return true;
            } else if (iblockdata.getBlock() instanceof BlockTrapdoor && this.trapdoorUsableAsLadder(blockposition, iblockdata)) {
                this.lastClimbablePos = Optional.of(blockposition);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPosition blockposition, IBlockData iblockdata) {
        if (!(Boolean) iblockdata.getValue(BlockTrapdoor.OPEN)) {
            return false;
        } else {
            IBlockData iblockdata1 = this.level().getBlockState(blockposition.below());

            return iblockdata1.is(Blocks.LADDER) && iblockdata1.getValue(BlockLadder.FACING) == iblockdata.getValue(BlockTrapdoor.FACING);
        }
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F;
    }

    public boolean isLookingAtMe(EntityLiving entityliving, double d0, boolean flag, boolean flag1, double... adouble) {
        Vec3D vec3d = entityliving.getViewVector(1.0F).normalize();

        for (double d1 : adouble) {
            Vec3D vec3d1 = new Vec3D(this.getX() - entityliving.getX(), d1 - entityliving.getEyeY(), this.getZ() - entityliving.getZ());
            double d2 = vec3d1.length();

            vec3d1 = vec3d1.normalize();
            double d3 = vec3d.dot(vec3d1);

            if (d3 > 1.0D - d0 / (flag ? d2 : 1.0D) && entityliving.hasLineOfSight(this, flag1 ? RayTrace.BlockCollisionOption.VISUAL : RayTrace.BlockCollisionOption.COLLIDER, RayTrace.FluidCollisionOption.NONE, d1)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return this.getComfortableFallDistance(0.0F);
    }

    protected final int getComfortableFallDistance(float f) {
        return MathHelper.floor(f + 3.0F);
    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        boolean flag = super.causeFallDamage(d0, f, damagesource);
        int i = this.calculateFallDamage(d0, f);

        if (i > 0) {
            // CraftBukkit start
            if (!this.hurtServer((WorldServer) this.level(), damagesource, (float) i)) {
                return true;
            }
            // CraftBukkit end
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            // this.damageEntity(damagesource, (float) i); // CraftBukkit - moved up
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(double d0, float f) {
        if (this.getType().is(TagsEntity.FALL_DAMAGE_IMMUNE)) {
            return 0;
        } else {
            double d1 = this.calculateFallPower(d0);

            return MathHelper.floor(d1 * (double) f * this.getAttributeValue(GenericAttributes.FALL_DAMAGE_MULTIPLIER));
        }
    }

    private double calculateFallPower(double d0) {
        return d0 + 1.0E-6D - this.getAttributeValue(GenericAttributes.SAFE_FALL_DISTANCE);
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = MathHelper.floor(this.getX());
            int j = MathHelper.floor(this.getY() - (double) 0.2F);
            int k = MathHelper.floor(this.getZ());
            IBlockData iblockdata = this.level().getBlockState(new BlockPosition(i, j, k));

            if (!iblockdata.isAir()) {
                SoundEffectType soundeffecttype = iblockdata.getSoundType();

                this.playSound(soundeffecttype.getFallSound(), soundeffecttype.getVolume() * 0.5F, soundeffecttype.getPitch() * 0.75F);
            }

        }
    }

    @Override
    public void animateHurt(float f) {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
    }

    public int getArmorValue() {
        return MathHelper.floor(this.getAttributeValue(GenericAttributes.ARMOR));
    }

    protected void hurtArmor(DamageSource damagesource, float f) {}

    protected void hurtHelmet(DamageSource damagesource, float f) {}

    protected void doHurtEquipment(DamageSource damagesource, float f, EnumItemSlot... aenumitemslot) {
        if (f > 0.0F) {
            int i = (int) Math.max(1.0F, f / 4.0F);

            for (EnumItemSlot enumitemslot : aenumitemslot) {
                ItemStack itemstack = this.getItemBySlot(enumitemslot);
                Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

                if (equippable != null && equippable.damageOnHurt() && itemstack.isDamageableItem() && itemstack.canBeHurtBy(damagesource)) {
                    itemstack.hurtAndBreak(i, this, enumitemslot);
                }
            }

        }
    }

    protected float getDamageAfterArmorAbsorb(DamageSource damagesource, float f) {
        if (!damagesource.is(DamageTypeTags.BYPASSES_ARMOR)) {
            // this.hurtArmor(damagesource, f); // CraftBukkit - actuallyHurt(DamageSource, float, EntityDamageEvent) for handle damage
            f = CombatMath.getDamageAfterAbsorb(this, f, damagesource, (float) this.getArmorValue(), (float) this.getAttributeValue(GenericAttributes.ARMOR_TOUGHNESS));
        }

        return f;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource damagesource, float f) {
        if (damagesource.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return f;
        } else {
            // CraftBukkit - Moved to handleEntityDamage(DamageSource, float)
            if (false && this.hasEffect(MobEffects.RESISTANCE) && !damagesource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int i = (this.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - i;
                float f1 = f * (float) j;
                float f2 = f;

                f = Math.max(f1 / 25.0F, 0.0F);
                float f3 = f2 - f;

                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof EntityPlayer) {
                        ((EntityPlayer) this).awardStat(StatisticList.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damagesource.getEntity() instanceof EntityPlayer) {
                        ((EntityPlayer) damagesource.getEntity()).awardStat(StatisticList.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            if (f <= 0.0F) {
                return 0.0F;
            } else if (damagesource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                return f;
            } else {
                World world = this.level();
                float f4;

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    f4 = EnchantmentManager.getDamageProtection(worldserver, this, damagesource);
                } else {
                    f4 = 0.0F;
                }

                if (f4 > 0.0F) {
                    f = CombatMath.getDamageAfterMagicAbsorb(f, f4);
                }

                return f;
            }
        }
    }

    // CraftBukkit start
    private EntityDamageEvent handleEntityDamage(final DamageSource damagesource, float f) {
        float originalDamage = f;

        com.google.common.base.Function<Double, Double> freezing = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                if (damagesource.is(DamageTypeTags.IS_FREEZING) && EntityLiving.this.getType().is(TagsEntity.FREEZE_HURTS_EXTRA_TYPES)) {
                    return -(f - (f * 5.0F));
                }
                return -0.0;
            }
        };
        float freezingModifier = freezing.apply((double) f).floatValue();
        f += freezingModifier;

        com.google.common.base.Function<Double, Double> hardHat = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                if (damagesource.is(DamageTypeTags.DAMAGES_HELMET) && !EntityLiving.this.getItemBySlot(EnumItemSlot.HEAD).isEmpty()) {
                    return -(f - (f * 0.75F));
                }
                return -0.0;
            }
        };
        float hardHatModifier = hardHat.apply((double) f).floatValue();
        f += hardHatModifier;

        com.google.common.base.Function<Double, Double> blocking = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -((double) EntityLiving.this.calculateItemBlocking(damagesource, f.floatValue()));
            }
        };
        float blockingModifier = blocking.apply((double) f).floatValue();
        f += blockingModifier;

        com.google.common.base.Function<Double, Double> armor = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -(f - EntityLiving.this.getDamageAfterArmorAbsorb(damagesource, f.floatValue()));
            }
        };
        float armorModifier = armor.apply((double) f).floatValue();
        f += armorModifier;

        com.google.common.base.Function<Double, Double> resistance = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                if (!damagesource.is(DamageTypeTags.BYPASSES_EFFECTS) && EntityLiving.this.hasEffect(MobEffects.RESISTANCE) && !damagesource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                    int i = (EntityLiving.this.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
                    int j = 25 - i;
                    float f1 = f.floatValue() * (float) j;

                    return -(f - Math.max(f1 / 25.0F, 0.0F));
                }
                return -0.0;
            }
        };
        float resistanceModifier = resistance.apply((double) f).floatValue();
        f += resistanceModifier;

        com.google.common.base.Function<Double, Double> magic = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -(f - EntityLiving.this.getDamageAfterMagicAbsorb(damagesource, f.floatValue()));
            }
        };
        float magicModifier = magic.apply((double) f).floatValue();
        f += magicModifier;

        com.google.common.base.Function<Double, Double> absorption = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -(Math.max(f - Math.max(f - EntityLiving.this.getAbsorptionAmount(), 0.0F), 0.0F));
            }
        };
        float absorptionModifier = absorption.apply((double) f).floatValue();

        return CraftEventFactory.handleLivingEntityDamageEvent(this, damagesource, originalDamage, freezingModifier, hardHatModifier, blockingModifier, armorModifier, resistanceModifier, magicModifier, absorptionModifier, freezing, hardHat, blocking, armor, resistance, magic, absorption);
    }

    protected boolean actuallyHurt(WorldServer worldserver, final DamageSource damagesource, float f, final EntityDamageEvent event) { // void -> boolean, add final
        if (!this.isInvulnerableTo(worldserver, damagesource)) {
            if (event.isCancelled()) {
                return false;
            }

            if (damagesource.getEntity() instanceof EntityHuman) {
                ((EntityHuman) damagesource.getEntity()).resetAttackStrengthTicker(); // Moved from EntityHuman in order to make the cooldown reset get called after the damage event is fired
            }

            // Resistance
            if (event.getDamage(DamageModifier.RESISTANCE) < 0) {
                float f3 = (float) -event.getDamage(DamageModifier.RESISTANCE);
                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof EntityPlayer) {
                        ((EntityPlayer) this).awardStat(StatisticList.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damagesource.getEntity() instanceof EntityPlayer) {
                        ((EntityPlayer) damagesource.getEntity()).awardStat(StatisticList.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            // Apply damage to helmet
            if (damagesource.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EnumItemSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damagesource, f);
            }

            // Apply damage to armor
            if (!damagesource.is(DamageTypeTags.BYPASSES_ARMOR)) {
                float armorDamage = (float) (event.getDamage() + event.getDamage(DamageModifier.BLOCKING) + event.getDamage(DamageModifier.HARD_HAT));
                this.hurtArmor(damagesource, armorDamage);
            }

            // Apply blocking code // PAIL: steal from above
            if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                actuallyDoItemBlocking(worldserver, damagesource, (float) -event.getDamage(DamageModifier.BLOCKING));
            }

            boolean human = this instanceof EntityHuman;
            float originalDamage = (float) event.getDamage();
            float absorptionModifier = (float) -event.getDamage(DamageModifier.ABSORPTION);
            this.setAbsorptionAmount(Math.max(this.getAbsorptionAmount() - absorptionModifier, 0.0F));
            float f2 = absorptionModifier;

            if (f2 > 0.0F && f2 < 3.4028235E37F && this instanceof EntityHuman) {
                ((EntityHuman) this).awardStat(StatisticList.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }
            // CraftBukkit end

            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                Entity entity = damagesource.getEntity();

                if (entity instanceof EntityPlayer) {
                    EntityPlayer entityplayer = (EntityPlayer) entity;

                    entityplayer.awardStat(StatisticList.DAMAGE_DEALT_ABSORBED, Math.round(f2 * 10.0F));
                }
            }

            // CraftBukkit start
            if (f > 0 || !human) {
                if (human) {
                    // PAIL: Be sure to drag all this code from the EntityHuman subclass each update.
                    ((EntityHuman) this).causeFoodExhaustion(damagesource.getFoodExhaustion(), org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                    if (f < 3.4028235E37F) {
                        ((EntityHuman) this).awardStat(StatisticList.DAMAGE_TAKEN, Math.round(f * 10.0F));
                    }
                }
                // CraftBukkit end
                this.getCombatTracker().recordDamage(damagesource, f);
                this.setHealth(this.getHealth() - f);
                // CraftBukkit start
                if (!human) {
                    this.setAbsorptionAmount(this.getAbsorptionAmount() - f);
                }
                this.gameEvent(GameEvent.ENTITY_DAMAGE);

                return true;
            } else {
                // Duplicate triggers if blocking
                if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                    if (this instanceof EntityPlayer) {
                        CriterionTriggers.ENTITY_HURT_PLAYER.trigger((EntityPlayer) this, damagesource, f, originalDamage, true);
                        f2 = (float) -event.getDamage(DamageModifier.BLOCKING);
                        if (f2 > 0.0F && f2 < 3.4028235E37F) {
                            ((EntityPlayer) this).awardStat(StatisticList.DAMAGE_BLOCKED_BY_SHIELD, Math.round(originalDamage * 10.0F));
                        }
                    }

                    if (damagesource.getEntity() instanceof EntityPlayer) {
                        CriterionTriggers.PLAYER_HURT_ENTITY.trigger((EntityPlayer) damagesource.getEntity(), this, damagesource, f, originalDamage, true);
                    }

                    return true;
                } else {
                    return originalDamage > 0;
                }
                // CraftBukkit end
            }
        }
        return false; // CraftBukkit
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public EntityLiving getKillCredit() {
        return this.lastHurtByPlayer != null ? (EntityLiving) this.lastHurtByPlayer.getEntity(this.level(), EntityHuman.class) : (this.lastHurtByMob != null ? (EntityLiving) this.lastHurtByMob.getEntity(this.level(), EntityLiving.class) : null);
    }

    public final float getMaxHealth() {
        return (float) this.getAttributeValue(GenericAttributes.MAX_HEALTH);
    }

    public final float getMaxAbsorption() {
        return (float) this.getAttributeValue(GenericAttributes.MAX_ABSORPTION);
    }

    public final int getArrowCount() {
        return (Integer) this.entityData.get(EntityLiving.DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int i) {
        // CraftBukkit start
        setArrowCount(i, false);
    }

    public final void setArrowCount(int i, boolean flag) {
        ArrowBodyCountChangeEvent event = CraftEventFactory.callArrowBodyCountChangeEvent(this, getArrowCount(), i, flag);
        if (event.isCancelled()) {
            return;
        }
        this.entityData.set(EntityLiving.DATA_ARROW_COUNT_ID, event.getNewAmount());
    }
    // CraftBukkit end

    public final int getStingerCount() {
        return (Integer) this.entityData.get(EntityLiving.DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int i) {
        this.entityData.set(EntityLiving.DATA_STINGER_COUNT_ID, i);
    }

    private int getCurrentSwingDuration() {
        return MobEffectUtil.hasDigSpeed(this) ? 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this)) : (this.hasEffect(MobEffects.MINING_FATIGUE) ? 6 + (1 + this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2 : 6);
    }

    public void swing(EnumHand enumhand) {
        this.swing(enumhand, false);
    }

    public void swing(EnumHand enumhand, boolean flag) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = enumhand;
            if (this.level() instanceof WorldServer) {
                PacketPlayOutAnimation packetplayoutanimation = new PacketPlayOutAnimation(this, enumhand == EnumHand.MAIN_HAND ? 0 : 3);
                ChunkProviderServer chunkproviderserver = ((WorldServer) this.level()).getChunkSource();

                if (flag) {
                    chunkproviderserver.broadcastAndSend(this, packetplayoutanimation);
                } else {
                    chunkproviderserver.broadcast(this, packetplayoutanimation);
                }
            }
        }

    }

    @Override
    public void handleDamageEvent(DamageSource damagesource) {
        this.walkAnimation.setSpeed(1.5F);
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        SoundEffect soundeffect = this.getHurtSound(damagesource);

        if (soundeffect != null) {
            this.playSound(soundeffect, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        this.lastDamageSource = damagesource;
        this.lastDamageStamp = this.level().getGameTime();
    }

    @Override
    public void handleEntityEvent(byte b0) {
        switch (b0) {
            case 3:
                SoundEffect soundeffect = this.getDeathSound();

                if (soundeffect != null) {
                    this.playSound(soundeffect, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof EntityHuman)) {
                    this.setHealth(0.0F);
                    this.die(this.damageSources().generic());
                }
                break;
            case 46:
                int i = 128;

                for (int j = 0; j < 128; ++j) {
                    double d0 = (double) j / 127.0D;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double d1 = MathHelper.lerp(d0, this.xo, this.getX()) + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth() * 2.0D;
                    double d2 = MathHelper.lerp(d0, this.yo, this.getY()) + this.random.nextDouble() * (double) this.getBbHeight();
                    double d3 = MathHelper.lerp(d0, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth() * 2.0D;

                    this.level().addParticle(Particles.PORTAL, d1, d2, d3, (double) f, (double) f1, (double) f2);
                }
                break;
            case 47:
                this.breakItem(this.getItemBySlot(EnumItemSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EnumItemSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EnumItemSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EnumItemSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EnumItemSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EnumItemSlot.FEET));
                break;
            case 54:
                BlockHoney.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
                break;
            case 65:
                this.breakItem(this.getItemBySlot(EnumItemSlot.BODY));
                break;
            case 67:
                this.makeDrownParticles();
                break;
            case 68:
                this.breakItem(this.getItemBySlot(EnumItemSlot.SADDLE));
                break;
            default:
                super.handleEntityEvent(b0);
        }

    }

    public void makePoofParticles() {
        for (int i = 0; i < 20; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;
            double d3 = 10.0D;

            this.level().addParticle(Particles.POOF, this.getRandomX(1.0D) - d0 * 10.0D, this.getRandomY() - d1 * 10.0D, this.getRandomZ(1.0D) - d2 * 10.0D, d0, d1, d2);
        }

    }

    private void makeDrownParticles() {
        Vec3D vec3d = this.getDeltaMovement();

        for (int i = 0; i < 8; ++i) {
            double d0 = this.random.triangle(0.0D, 1.0D);
            double d1 = this.random.triangle(0.0D, 1.0D);
            double d2 = this.random.triangle(0.0D, 1.0D);

            this.level().addParticle(Particles.BUBBLE, this.getX() + d0, this.getY() + d1, this.getZ() + d2, vec3d.x, vec3d.y, vec3d.z);
        }

    }

    private void swapHandItems() {
        ItemStack itemstack = this.getItemBySlot(EnumItemSlot.OFFHAND);

        this.setItemSlot(EnumItemSlot.OFFHAND, this.getItemBySlot(EnumItemSlot.MAINHAND));
        this.setItemSlot(EnumItemSlot.MAINHAND, itemstack);
    }

    @Override
    protected void onBelowWorld() {
        this.hurt(this.damageSources().fellOutOfWorld(), 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();

        if (this.swinging) {
            ++this.swingTime;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float) this.swingTime / (float) i;
    }

    @Nullable
    public AttributeModifiable getAttribute(Holder<AttributeBase> holder) {
        return this.getAttributes().getInstance(holder);
    }

    public double getAttributeValue(Holder<AttributeBase> holder) {
        return this.getAttributes().getValue(holder);
    }

    public double getAttributeBaseValue(Holder<AttributeBase> holder) {
        return this.getAttributes().getBaseValue(holder);
    }

    public AttributeMapBase getAttributes() {
        return this.attributes;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EnumItemSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EnumItemSlot.OFFHAND);
    }

    public ItemStack getItemHeldByArm(EnumMainHand enummainhand) {
        return this.getMainArm() == enummainhand ? this.getMainHandItem() : this.getOffhandItem();
    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.getMainHandItem();
    }

    public boolean isHolding(Item item) {
        return this.isHolding((itemstack) -> {
            return itemstack.is(item);
        });
    }

    public boolean isHolding(Predicate<ItemStack> predicate) {
        return predicate.test(this.getMainHandItem()) || predicate.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(EnumHand enumhand) {
        if (enumhand == EnumHand.MAIN_HAND) {
            return this.getItemBySlot(EnumItemSlot.MAINHAND);
        } else if (enumhand == EnumHand.OFF_HAND) {
            return this.getItemBySlot(EnumItemSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + String.valueOf(enumhand));
        }
    }

    public void setItemInHand(EnumHand enumhand, ItemStack itemstack) {
        if (enumhand == EnumHand.MAIN_HAND) {
            this.setItemSlot(EnumItemSlot.MAINHAND, itemstack);
        } else {
            if (enumhand != EnumHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + String.valueOf(enumhand));
            }

            this.setItemSlot(EnumItemSlot.OFFHAND, itemstack);
        }

    }

    public boolean hasItemInSlot(EnumItemSlot enumitemslot) {
        return !this.getItemBySlot(enumitemslot).isEmpty();
    }

    public boolean canUseSlot(EnumItemSlot enumitemslot) {
        return true;
    }

    public ItemStack getItemBySlot(EnumItemSlot enumitemslot) {
        return this.equipment.get(enumitemslot);
    }

    public void setItemSlot(EnumItemSlot enumitemslot, ItemStack itemstack) {
        // CraftBukkit start
        this.setItemSlot(enumitemslot, itemstack, false);
    }

    public void setItemSlot(EnumItemSlot enumitemslot, ItemStack itemstack, boolean silent) {
        this.onEquipItem(enumitemslot, this.equipment.set(enumitemslot, itemstack), itemstack, silent);
        // CraftBukkit end
    }

    public float getArmorCoverPercentage() {
        int i = 0;
        int j = 0;

        for (EnumItemSlot enumitemslot : EquipmentSlotGroup.ARMOR) {
            if (enumitemslot.getType() == EnumItemSlot.Function.HUMANOID_ARMOR) {
                ItemStack itemstack = this.getItemBySlot(enumitemslot);

                if (!itemstack.isEmpty()) {
                    ++j;
                }

                ++i;
            }
        }

        return i > 0 ? (float) j / (float) i : 0.0F;
    }

    @Override
    public void setSprinting(boolean flag) {
        super.setSprinting(flag);
        AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.MOVEMENT_SPEED);

        attributemodifiable.removeModifier(EntityLiving.SPEED_MODIFIER_SPRINTING.id());
        if (flag) {
            attributemodifiable.addTransientModifier(EntityLiving.SPEED_MODIFIER_SPRINTING);
        }

    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }

    }

    private void dismountVehicle(Entity entity) {
        Vec3D vec3d;

        if (this.isRemoved()) {
            vec3d = this.position();
        } else if (!entity.isRemoved() && !this.level().getBlockState(entity.blockPosition()).is(TagsBlock.PORTALS)) {
            vec3d = entity.getDismountLocationForPassenger(this);
        } else {
            double d0 = Math.max(this.getY(), entity.getY());

            vec3d = new Vec3D(this.getX(), d0, this.getZ());
            boolean flag = this.getBbWidth() <= 4.0F && this.getBbHeight() <= 4.0F;

            if (flag) {
                double d1 = (double) this.getBbHeight() / 2.0D;
                Vec3D vec3d1 = vec3d.add(0.0D, d1, 0.0D);
                VoxelShape voxelshape = VoxelShapes.create(AxisAlignedBB.ofSize(vec3d1, (double) this.getBbWidth(), (double) this.getBbHeight(), (double) this.getBbWidth()));

                vec3d = (Vec3D) this.level().findFreePosition(this, voxelshape, vec3d1, (double) this.getBbWidth(), (double) this.getBbHeight(), (double) this.getBbWidth()).map((vec3d2) -> {
                    return vec3d2.add(0.0D, -d1, 0.0D);
                }).orElse(vec3d);
            }
        }

        this.dismountTo(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return this.getJumpPower(1.0F);
    }

    protected float getJumpPower(float f) {
        return (float) this.getAttributeValue(GenericAttributes.JUMP_STRENGTH) * f * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP_BOOST) ? 0.1F * ((float) this.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1.0F) : 0.0F;
    }

    @VisibleForTesting
    public void jumpFromGround() {
        float f = this.getJumpPower();

        if (f > 1.0E-5F) {
            Vec3D vec3d = this.getDeltaMovement();

            this.setDeltaMovement(vec3d.x, Math.max((double) f, vec3d.y), vec3d.z);
            if (this.isSprinting()) {
                float f1 = this.getYRot() * ((float) Math.PI / 180F);

                this.addDeltaMovement(new Vec3D((double) (-MathHelper.sin(f1)) * 0.2D, 0.0D, (double) MathHelper.cos(f1) * 0.2D));
            }

            this.hasImpulse = true;
        }
    }

    protected void goDownInWater() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) -0.04F, 0.0D));
    }

    protected void jumpInLiquid(TagKey<FluidType> tagkey) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) 0.04F, 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(Fluid fluid) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.getAttributeValue(GenericAttributes.GRAVITY);
    }

    protected double getEffectiveGravity() {
        boolean flag = this.getDeltaMovement().y <= 0.0D;

        return flag && this.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(this.getGravity(), 0.01D) : this.getGravity();
    }

    public void travel(Vec3D vec3d) {
        Fluid fluid = this.level().getFluidState(this.blockPosition());

        if ((this.isInWater() || this.isInLava()) && this.isAffectedByFluids() && !this.canStandOnFluid(fluid)) {
            this.travelInFluid(vec3d);
        } else if (this.isFallFlying()) {
            this.travelFallFlying(vec3d);
        } else {
            this.travelInAir(vec3d);
        }

    }

    private void travelInAir(Vec3D vec3d) {
        BlockPosition blockposition = this.getBlockPosBelowThatAffectsMyMovement();
        float f = this.onGround() ? this.level().getBlockState(blockposition).getBlock().getFriction() : 1.0F;
        float f1 = f * 0.91F;
        Vec3D vec3d1 = this.handleRelativeFrictionAndCalculateMovement(vec3d, f);
        double d0 = vec3d1.y;
        MobEffect mobeffect = this.getEffect(MobEffects.LEVITATION);

        if (mobeffect != null) {
            d0 += (0.05D * (double) (mobeffect.getAmplifier() + 1) - vec3d1.y) * 0.2D;
        } else if (this.level().isClientSide && !this.level().hasChunkAt(blockposition)) {
            if (this.getY() > (double) this.level().getMinY()) {
                d0 = -0.1D;
            } else {
                d0 = 0.0D;
            }
        } else {
            d0 -= this.getEffectiveGravity();
        }

        if (this.shouldDiscardFriction()) {
            this.setDeltaMovement(vec3d1.x, d0, vec3d1.z);
        } else {
            float f2 = this instanceof EntityBird ? f1 : 0.98F;

            this.setDeltaMovement(vec3d1.x * (double) f1, d0 * (double) f2, vec3d1.z * (double) f1);
        }

    }

    private void travelInFluid(Vec3D vec3d) {
        boolean flag = this.getDeltaMovement().y <= 0.0D;
        double d0 = this.getY();
        double d1 = this.getEffectiveGravity();

        if (this.isInWater()) {
            float f = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
            float f1 = 0.02F;
            float f2 = (float) this.getAttributeValue(GenericAttributes.WATER_MOVEMENT_EFFICIENCY);

            if (!this.onGround()) {
                f2 *= 0.5F;
            }

            if (f2 > 0.0F) {
                f += (0.54600006F - f) * f2;
                f1 += (this.getSpeed() - f1) * f2;
            }

            if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                f = 0.96F;
            }

            this.moveRelative(f1, vec3d);
            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            Vec3D vec3d1 = this.getDeltaMovement();

            if (this.horizontalCollision && this.onClimbable()) {
                vec3d1 = new Vec3D(vec3d1.x, 0.2D, vec3d1.z);
            }

            vec3d1 = vec3d1.multiply((double) f, (double) 0.8F, (double) f);
            this.setDeltaMovement(this.getFluidFallingAdjustedMovement(d1, flag, vec3d1));
        } else {
            this.moveRelative(0.02F, vec3d);
            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            if (this.getFluidHeight(TagsFluid.LAVA) <= this.getFluidJumpThreshold()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, (double) 0.8F, 0.5D));
                Vec3D vec3d2 = this.getFluidFallingAdjustedMovement(d1, flag, this.getDeltaMovement());

                this.setDeltaMovement(vec3d2);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            }

            if (d1 != 0.0D) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d1 / 4.0D, 0.0D));
            }
        }

        Vec3D vec3d3 = this.getDeltaMovement();

        if (this.horizontalCollision && this.isFree(vec3d3.x, vec3d3.y + (double) 0.6F - this.getY() + d0, vec3d3.z)) {
            this.setDeltaMovement(vec3d3.x, (double) 0.3F, vec3d3.z);
        }

    }

    private void travelFallFlying(Vec3D vec3d) {
        if (this.onClimbable()) {
            this.travelInAir(vec3d);
            this.stopFallFlying();
        } else {
            Vec3D vec3d1 = this.getDeltaMovement();
            double d0 = vec3d1.horizontalDistance();

            this.setDeltaMovement(this.updateFallFlyingMovement(vec3d1));
            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            if (!this.level().isClientSide) {
                double d1 = this.getDeltaMovement().horizontalDistance();

                this.handleFallFlyingCollisions(d0, d1);
            }

        }
    }

    public void stopFallFlying() {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) {
            return;
        }
        // CraftBukkit end
        this.setSharedFlag(7, true);
        this.setSharedFlag(7, false);
    }

    private Vec3D updateFallFlyingMovement(Vec3D vec3d) {
        Vec3D vec3d1 = this.getLookAngle();
        float f = this.getXRot() * ((float) Math.PI / 180F);
        double d0 = Math.sqrt(vec3d1.x * vec3d1.x + vec3d1.z * vec3d1.z);
        double d1 = vec3d.horizontalDistance();
        double d2 = this.getEffectiveGravity();
        double d3 = MathHelper.square(Math.cos((double) f));

        vec3d = vec3d.add(0.0D, d2 * (-1.0D + d3 * 0.75D), 0.0D);
        if (vec3d.y < 0.0D && d0 > 0.0D) {
            double d4 = vec3d.y * -0.1D * d3;

            vec3d = vec3d.add(vec3d1.x * d4 / d0, d4, vec3d1.z * d4 / d0);
        }

        if (f < 0.0F && d0 > 0.0D) {
            double d5 = d1 * (double) (-MathHelper.sin(f)) * 0.04D;

            vec3d = vec3d.add(-vec3d1.x * d5 / d0, d5 * 3.2D, -vec3d1.z * d5 / d0);
        }

        if (d0 > 0.0D) {
            vec3d = vec3d.add((vec3d1.x / d0 * d1 - vec3d.x) * 0.1D, 0.0D, (vec3d1.z / d0 * d1 - vec3d.z) * 0.1D);
        }

        return vec3d.multiply((double) 0.99F, (double) 0.98F, (double) 0.99F);
    }

    private void handleFallFlyingCollisions(double d0, double d1) {
        if (this.horizontalCollision) {
            double d2 = d0 - d1;
            float f = (float) (d2 * 10.0D - 3.0D);

            if (f > 0.0F) {
                this.playSound(this.getFallDamageSound((int) f), 1.0F, 1.0F);
                this.hurt(this.damageSources().flyIntoWall(), f);
            }
        }

    }

    private void travelRidden(EntityHuman entityhuman, Vec3D vec3d) {
        Vec3D vec3d1 = this.getRiddenInput(entityhuman, vec3d);

        this.tickRidden(entityhuman, vec3d1);
        if (this.canSimulateMovement()) {
            this.setSpeed(this.getRiddenSpeed(entityhuman));
            this.travel(vec3d1);
        } else {
            this.setDeltaMovement(Vec3D.ZERO);
        }

    }

    protected void tickRidden(EntityHuman entityhuman, Vec3D vec3d) {}

    protected Vec3D getRiddenInput(EntityHuman entityhuman, Vec3D vec3d) {
        return vec3d;
    }

    protected float getRiddenSpeed(EntityHuman entityhuman) {
        return this.getSpeed();
    }

    public void calculateEntityAnimation(boolean flag) {
        float f = (float) MathHelper.length(this.getX() - this.xo, flag ? this.getY() - this.yo : 0.0D, this.getZ() - this.zo);

        if (!this.isPassenger() && this.isAlive()) {
            this.updateWalkAnimation(f);
        } else {
            this.walkAnimation.stop();
        }

    }

    protected void updateWalkAnimation(float f) {
        float f1 = Math.min(f * 4.0F, 1.0F);

        this.walkAnimation.update(f1, 0.4F, this.isBaby() ? 3.0F : 1.0F);
    }

    private Vec3D handleRelativeFrictionAndCalculateMovement(Vec3D vec3d, float f) {
        this.moveRelative(this.getFrictionInfluencedSpeed(f), vec3d);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(EnumMoveType.SELF, this.getDeltaMovement());
        Vec3D vec3d1 = this.getDeltaMovement();

        if ((this.horizontalCollision || this.jumping) && (this.onClimbable() || this.wasInPowderSnow && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            vec3d1 = new Vec3D(vec3d1.x, 0.2D, vec3d1.z);
        }

        return vec3d1;
    }

    public Vec3D getFluidFallingAdjustedMovement(double d0, boolean flag, Vec3D vec3d) {
        if (d0 != 0.0D && !this.isSprinting()) {
            double d1;

            if (flag && Math.abs(vec3d.y - 0.005D) >= 0.003D && Math.abs(vec3d.y - d0 / 16.0D) < 0.003D) {
                d1 = -0.003D;
            } else {
                d1 = vec3d.y - d0 / 16.0D;
            }

            return new Vec3D(vec3d.x, d1, vec3d.z);
        } else {
            return vec3d;
        }
    }

    private Vec3D handleOnClimbable(Vec3D vec3d) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d0 = MathHelper.clamp(vec3d.x, (double) -0.15F, (double) 0.15F);
            double d1 = MathHelper.clamp(vec3d.z, (double) -0.15F, (double) 0.15F);
            double d2 = Math.max(vec3d.y, (double) -0.15F);

            if (d2 < 0.0D && !this.getInBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof EntityHuman) {
                d2 = 0.0D;
            }

            vec3d = new Vec3D(d0, d2, d1);
        }

        return vec3d;
    }

    private float getFrictionInfluencedSpeed(float f) {
        return this.onGround() ? this.getSpeed() * (0.21600002F / (f * f * f)) : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return this.getControllingPassenger() instanceof EntityHuman ? this.getSpeed() * 0.1F : 0.02F;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float f) {
        this.speed = f;
    }

    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        this.setLastHurtMob(entity);
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level().isClientSide) {
            int i = this.getArrowCount();

            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                --this.removeArrowTime;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();

            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                --this.removeStingerTime;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdatesPublic(); // CraftBukkit
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d0 = this.getX() - this.xo;
        double d1 = this.getZ() - this.zo;
        float f = (float) (d0 * d0 + d1 * d1);
        float f1 = this.yBodyRot;

        if (f > 0.0025000002F) {
            float f2 = (float) MathHelper.atan2(d1, d0) * (180F / (float) Math.PI) - 90.0F;
            float f3 = MathHelper.abs(MathHelper.wrapDegrees(this.getYRot()) - f2);

            if (95.0F < f3 && f3 < 265.0F) {
                f1 = f2 - 180.0F;
            } else {
                f1 = f2;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.getYRot();
        }

        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("headTurn");
        this.tickHeadTurn(f1);
        gameprofilerfiller.pop();
        gameprofilerfiller.push("rangeChecks");

        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        gameprofilerfiller.pop();
        if (this.isFallFlying()) {
            ++this.fallFlyTicks;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }

        this.refreshDirtyAttributes();
        this.elytraAnimationState.tick();
    }

    public void detectEquipmentUpdatesPublic() { // CraftBukkit
        Map<EnumItemSlot, ItemStack> map = this.collectEquipmentChanges();

        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }

    }

    @Nullable
    private Map<EnumItemSlot, ItemStack> collectEquipmentChanges() {
        Map<EnumItemSlot, ItemStack> map = null;

        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            ItemStack itemstack = (ItemStack) this.lastEquipmentItems.get(enumitemslot);
            ItemStack itemstack1 = this.getItemBySlot(enumitemslot);

            if (this.equipmentHasChanged(itemstack, itemstack1)) {
                if (map == null) {
                    map = Maps.newEnumMap(EnumItemSlot.class);
                }

                map.put(enumitemslot, itemstack1);
                AttributeMapBase attributemapbase = this.getAttributes();

                if (!itemstack.isEmpty()) {
                    this.stopLocationBasedEffects(itemstack, enumitemslot, attributemapbase);
                }
            }
        }

        if (map != null) {
            for (Map.Entry<EnumItemSlot, ItemStack> map_entry : map.entrySet()) {
                EnumItemSlot enumitemslot1 = (EnumItemSlot) map_entry.getKey();
                ItemStack itemstack2 = (ItemStack) map_entry.getValue();

                if (!itemstack2.isEmpty() && !itemstack2.isBroken()) {
                    itemstack2.forEachModifier(enumitemslot1, (holder, attributemodifier) -> {
                        AttributeModifiable attributemodifiable = this.attributes.getInstance(holder);

                        if (attributemodifiable != null) {
                            attributemodifiable.removeModifier(attributemodifier.id());
                            attributemodifiable.addTransientModifier(attributemodifier);
                        }

                    });
                    World world = this.level();

                    if (world instanceof WorldServer) {
                        WorldServer worldserver = (WorldServer) world;

                        EnchantmentManager.runLocationChangedEffects(worldserver, itemstack2, this, enumitemslot1);
                    }
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack itemstack, ItemStack itemstack1) {
        return !ItemStack.matches(itemstack1, itemstack);
    }

    private void handleHandSwap(Map<EnumItemSlot, ItemStack> map) {
        ItemStack itemstack = (ItemStack) map.get(EnumItemSlot.MAINHAND);
        ItemStack itemstack1 = (ItemStack) map.get(EnumItemSlot.OFFHAND);

        if (itemstack != null && itemstack1 != null && ItemStack.matches(itemstack, (ItemStack) this.lastEquipmentItems.get(EnumItemSlot.OFFHAND)) && ItemStack.matches(itemstack1, (ItemStack) this.lastEquipmentItems.get(EnumItemSlot.MAINHAND))) {
            ((WorldServer) this.level()).getChunkSource().broadcast(this, new PacketPlayOutEntityStatus(this, (byte) 55));
            map.remove(EnumItemSlot.MAINHAND);
            map.remove(EnumItemSlot.OFFHAND);
            this.lastEquipmentItems.put(EnumItemSlot.MAINHAND, itemstack.copy());
            this.lastEquipmentItems.put(EnumItemSlot.OFFHAND, itemstack1.copy());
        }

    }

    private void handleEquipmentChanges(Map<EnumItemSlot, ItemStack> map) {
        List<Pair<EnumItemSlot, ItemStack>> list = Lists.newArrayListWithCapacity(map.size());

        map.forEach((enumitemslot, itemstack) -> {
            ItemStack itemstack1 = itemstack.copy();

            list.add(Pair.of(enumitemslot, itemstack1));
            this.lastEquipmentItems.put(enumitemslot, itemstack1);
        });
        ((WorldServer) this.level()).getChunkSource().broadcast(this, new PacketPlayOutEntityEquipment(this.getId(), list));
    }

    protected void tickHeadTurn(float f) {
        float f1 = MathHelper.wrapDegrees(f - this.yBodyRot);

        this.yBodyRot += f1 * 0.3F;
        float f2 = MathHelper.wrapDegrees(this.getYRot() - this.yBodyRot);
        float f3 = this.getMaxHeadRotationRelativeToBody();

        if (Math.abs(f2) > f3) {
            this.yBodyRot += f2 - (float) MathHelper.sign((double) f2) * f3;
        }

    }

    protected float getMaxHeadRotationRelativeToBody() {
        return 50.0F;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            --this.noJumpDelay;
        }

        if (this.isInterpolating()) {
            this.getInterpolation().interpolate();
        } else if (!this.canSimulateMovement()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }

        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            --this.lerpHeadSteps;
        }

        this.equipment.tick(this);
        Vec3D vec3d = this.getDeltaMovement();
        double d0 = vec3d.x;
        double d1 = vec3d.y;
        double d2 = vec3d.z;

        if (this.getType().equals(EntityTypes.PLAYER)) {
            if (vec3d.horizontalDistanceSqr() < 9.0E-6D) {
                d0 = 0.0D;
                d2 = 0.0D;
            }
        } else {
            if (Math.abs(vec3d.x) < 0.003D) {
                d0 = 0.0D;
            }

            if (Math.abs(vec3d.z) < 0.003D) {
                d2 = 0.0D;
            }
        }

        if (Math.abs(vec3d.y) < 0.003D) {
            d1 = 0.0D;
        }

        this.setDeltaMovement(d0, d1, d2);
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("ai");
        this.applyInput();
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi() && !this.level().isClientSide) {
            gameprofilerfiller.push("newAi");
            this.serverAiStep();
            gameprofilerfiller.pop();
        }

        gameprofilerfiller.pop();
        gameprofilerfiller.push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double d3;

            if (this.isInLava()) {
                d3 = this.getFluidHeight(TagsFluid.LAVA);
            } else {
                d3 = this.getFluidHeight(TagsFluid.WATER);
            }

            boolean flag = this.isInWater() && d3 > 0.0D;
            double d4 = this.getFluidJumpThreshold();

            if (!flag || this.onGround() && d3 <= d4) {
                if (!this.isInLava() || this.onGround() && d3 <= d4) {
                    if ((this.onGround() || flag && d3 <= d4) && this.noJumpDelay == 0) {
                        this.jumpFromGround();
                        this.noJumpDelay = 10;
                    }
                } else {
                    this.jumpInLiquid(TagsFluid.LAVA);
                }
            } else {
                this.jumpInLiquid(TagsFluid.WATER);
            }
        } else {
            this.noJumpDelay = 0;
        }

        gameprofilerfiller.pop();
        gameprofilerfiller.push("travel");
        if (this.isFallFlying()) {
            this.updateFallFlying();
        }

        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        Vec3D vec3d1 = new Vec3D((double) this.xxa, (double) this.yya, (double) this.zza);

        if (this.hasEffect(MobEffects.SLOW_FALLING) || this.hasEffect(MobEffects.LEVITATION)) {
            this.resetFallDistance();
        }

        label122:
        {
            EntityLiving entityliving = this.getControllingPassenger();

            if (entityliving instanceof EntityHuman entityhuman) {
                if (this.isAlive()) {
                    this.travelRidden(entityhuman, vec3d1);
                    break label122;
                }
            }

            if (this.canSimulateMovement()) {
                this.travel(vec3d1);
            }
        }

        if (!this.level().isClientSide() || this.isLocalInstanceAuthoritative()) {
            this.applyEffectsFromBlocks();
        }

        if (this.level().isClientSide()) {
            this.calculateEntityAnimation(this instanceof EntityBird);
        }

        gameprofilerfiller.pop();
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            gameprofilerfiller.push("freezing");
            if (!this.isInPowderSnow || !this.canFreeze()) {
                this.setTicksFrozen(Math.max(0, this.getTicksFrozen() - 2));
            }

            this.removeFrost();
            this.tryAddFrost();
            if (this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
                this.hurtServer(worldserver, this.damageSources().freeze(), 1.0F);
            }

            gameprofilerfiller.pop();
        }

        gameprofilerfiller.push("push");
        if (this.autoSpinAttackTicks > 0) {
            --this.autoSpinAttackTicks;
            this.checkAutoSpinAttack(axisalignedbb, this.getBoundingBox());
        }

        this.pushEntities();
        gameprofilerfiller.pop();
        world = this.level();
        if (world instanceof WorldServer worldserver1) {
            if (this.isSensitiveToWater() && this.isInWaterOrRain()) {
                this.hurtServer(worldserver1, this.damageSources().drown(), 1.0F);
            }
        }

    }

    protected void applyInput() {
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
    }

    public boolean isSensitiveToWater() {
        return false;
    }

    protected void updateFallFlying() {
        this.checkSlowFallDistance();
        if (!this.level().isClientSide) {
            if (!this.canGlide()) {
                if (this.getSharedFlag(7) != false && !CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) // CraftBukkit
                this.setSharedFlag(7, false);
                return;
            }

            int i = this.fallFlyTicks + 1;

            if (i % 10 == 0) {
                int j = i / 10;

                if (j % 2 == 0) {
                    List<EnumItemSlot> list = EnumItemSlot.VALUES.stream().filter((enumitemslot) -> {
                        return canGlideUsing(this.getItemBySlot(enumitemslot), enumitemslot);
                    }).toList();
                    EnumItemSlot enumitemslot = (EnumItemSlot) SystemUtils.getRandom(list, this.random);

                    this.getItemBySlot(enumitemslot).hurtAndBreak(1, this, enumitemslot);
                }

                this.gameEvent(GameEvent.ELYTRA_GLIDE);
            }
        }

    }

    protected boolean canGlide() {
        if (!this.onGround() && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
                if (canGlideUsing(this.getItemBySlot(enumitemslot), enumitemslot)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    protected void serverAiStep() {}

    protected void pushEntities() {
        List<Entity> list = this.level().getPushableEntities(this, this.getBoundingBox());

        if (!list.isEmpty()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;
                int i = worldserver.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);

                if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                    int j = 0;

                    for (Entity entity : list) {
                        if (!entity.isPassenger()) {
                            ++j;
                        }
                    }

                    if (j > i - 1) {
                        this.hurtServer(worldserver, this.damageSources().cramming(), 6.0F);
                    }
                }
            }

            for (Entity entity1 : list) {
                this.doPush(entity1);
            }

        }
    }

    protected void checkAutoSpinAttack(AxisAlignedBB axisalignedbb, AxisAlignedBB axisalignedbb1) {
        AxisAlignedBB axisalignedbb2 = axisalignedbb.minmax(axisalignedbb1);
        List<Entity> list = this.level().getEntities(this, axisalignedbb2);

        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof EntityLiving) {
                    this.doAutoAttackOnTouch((EntityLiving) entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2D));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level().isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
            this.autoSpinAttackDmg = 0.0F;
            this.autoSpinAttackItemStack = null;
        }

    }

    protected void doPush(Entity entity) {
        entity.push((Entity) this);
    }

    protected void doAutoAttackOnTouch(EntityLiving entityliving) {}

    public boolean isAutoSpinAttack() {
        return ((Byte) this.entityData.get(EntityLiving.DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();

        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level().isClientSide) {
            this.dismountVehicle(entity);
        }

    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.resetFallDistance();
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    public void lerpHeadTo(float f, int i) {
        this.lerpYHeadRot = (double) f;
        this.lerpHeadSteps = i;
    }

    public void setJumping(boolean flag) {
        this.jumping = flag;
    }

    public void onItemPickup(EntityItem entityitem) {
        Entity entity = entityitem.getOwner();

        if (entity instanceof EntityPlayer) {
            CriterionTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((EntityPlayer) entity, entityitem.getItem(), this);
        }

    }

    public void take(Entity entity, int i) {
        if (!entity.isRemoved() && !this.level().isClientSide && (entity instanceof EntityItem || entity instanceof EntityArrow || entity instanceof EntityExperienceOrb)) {
            ((WorldServer) this.level()).getChunkSource().broadcast(entity, new PacketPlayOutCollect(entity.getId(), this.getId(), i));
        }

    }

    public boolean hasLineOfSight(Entity entity) {
        return this.hasLineOfSight(entity, RayTrace.BlockCollisionOption.COLLIDER, RayTrace.FluidCollisionOption.NONE, entity.getEyeY());
    }

    public boolean hasLineOfSight(Entity entity, RayTrace.BlockCollisionOption raytrace_blockcollisionoption, RayTrace.FluidCollisionOption raytrace_fluidcollisionoption, double d0) {
        if (entity.level() != this.level()) {
            return false;
        } else {
            Vec3D vec3d = new Vec3D(this.getX(), this.getEyeY(), this.getZ());
            Vec3D vec3d1 = new Vec3D(entity.getX(), d0, entity.getZ());

            return vec3d1.distanceTo(vec3d) > 128.0D ? false : this.level().clip(new RayTrace(vec3d, vec3d1, raytrace_blockcollisionoption, raytrace_fluidcollisionoption, this)).getType() == MovingObjectPosition.EnumMovingObjectType.MISS;
        }
    }

    @Override
    public float getViewYRot(float f) {
        return f == 1.0F ? this.yHeadRot : MathHelper.rotLerp(f, this.yHeadRotO, this.yHeadRot);
    }

    public float getAttackAnim(float f) {
        float f1 = this.attackAnim - this.oAttackAnim;

        if (f1 < 0.0F) {
            ++f1;
        }

        return this.oAttackAnim + f1 * f;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved() && this.collides; // CraftBukkit
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.isSpectator() && !this.onClimbable() && this.collides; // CraftBukkit
    }

    // CraftBukkit start - collidable API
    @Override
    public boolean canCollideWithBukkit(Entity entity) {
        return isPushable() && this.collides != this.collidableExemptions.contains(entity.getUUID());
    }
    // CraftBukkit end

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    @Override
    public void setYHeadRot(float f) {
        this.yHeadRot = f;
    }

    @Override
    public void setYBodyRot(float f) {
        this.yBodyRot = f;
    }

    @Override
    public Vec3D getRelativePortalPosition(EnumDirection.EnumAxis enumdirection_enumaxis, BlockUtil.Rectangle blockutil_rectangle) {
        return resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(enumdirection_enumaxis, blockutil_rectangle));
    }

    public static Vec3D resetForwardDirectionOfRelativePortalPosition(Vec3D vec3d) {
        return new Vec3D(vec3d.x, vec3d.y, 0.0D);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public final void setAbsorptionAmount(float f) {
        this.internalSetAbsorptionAmount(MathHelper.clamp(f, 0.0F, this.getMaxAbsorption()));
    }

    protected void internalSetAbsorptionAmount(float f) {
        this.absorptionAmount = f;
    }

    public void onEnterCombat() {}

    public void onLeaveCombat() {}

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract EnumMainHand getMainArm();

    public boolean isUsingItem() {
        return ((Byte) this.entityData.get(EntityLiving.DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public EnumHand getUsedItemHand() {
        return ((Byte) this.entityData.get(EntityLiving.DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSameItem(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }

    }

    @Nullable
    private EntityItem createItemStackToDrop(ItemStack itemstack, boolean flag, boolean flag1) {
        if (itemstack.isEmpty()) {
            return null;
        } else {
            double d0 = this.getEyeY() - (double) 0.3F;
            EntityItem entityitem = new EntityItem(this.level(), this.getX(), d0, this.getZ(), itemstack);

            entityitem.setPickUpDelay(40);
            if (flag1) {
                entityitem.setThrower(this);
            }

            if (flag) {
                float f = this.random.nextFloat() * 0.5F;
                float f1 = this.random.nextFloat() * ((float) Math.PI * 2F);

                entityitem.setDeltaMovement((double) (-MathHelper.sin(f1) * f), (double) 0.2F, (double) (MathHelper.cos(f1) * f));
            } else {
                float f2 = 0.3F;
                float f3 = MathHelper.sin(this.getXRot() * ((float) Math.PI / 180F));
                float f4 = MathHelper.cos(this.getXRot() * ((float) Math.PI / 180F));
                float f5 = MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F));
                float f6 = MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F));
                float f7 = this.random.nextFloat() * ((float) Math.PI * 2F);
                float f8 = 0.02F * this.random.nextFloat();

                entityitem.setDeltaMovement((double) (-f5 * f4 * 0.3F) + Math.cos((double) f7) * (double) f8, (double) (-f3 * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F), (double) (f6 * f4 * 0.3F) + Math.sin((double) f7) * (double) f8);
            }

            return entityitem;
        }
    }

    protected void updateUsingItem(ItemStack itemstack) {
        itemstack.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
        if (--this.useItemRemaining == 0 && !this.level().isClientSide && !itemstack.useOnRelease()) {
            this.completeUsingItem();
        }

    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }

    }

    public void setLivingEntityFlag(int i, boolean flag) {
        int j = (Byte) this.entityData.get(EntityLiving.DATA_LIVING_ENTITY_FLAGS);

        if (flag) {
            j |= i;
        } else {
            j &= ~i;
        }

        this.entityData.set(EntityLiving.DATA_LIVING_ENTITY_FLAGS, (byte) j);
    }

    public void startUsingItem(EnumHand enumhand) {
        ItemStack itemstack = this.getItemInHand(enumhand);

        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            this.useItem = itemstack;
            this.useItemRemaining = itemstack.getUseDuration(this);
            if (!this.level().isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, enumhand == EnumHand.OFF_HAND);
                this.gameEvent(GameEvent.ITEM_INTERACT_START);
            }

        }
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (EntityLiving.SLEEPING_POS_ID.equals(datawatcherobject)) {
            if (this.level().isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (EntityLiving.DATA_LIVING_ENTITY_FLAGS.equals(datawatcherobject) && this.level().isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration(this);
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }

    }

    @Override
    public void lookAt(ArgumentAnchor.Anchor argumentanchor_anchor, Vec3D vec3d) {
        super.lookAt(argumentanchor_anchor, vec3d);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public float getPreciseBodyRotation(float f) {
        return MathHelper.lerp(f, this.yBodyRotO, this.yBodyRot);
    }

    public void spawnItemParticles(ItemStack itemstack, int i) {
        for (int j = 0; j < i; ++j) {
            Vec3D vec3d = new Vec3D(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);

            vec3d = vec3d.xRot(-this.getXRot() * ((float) Math.PI / 180F));
            vec3d = vec3d.yRot(-this.getYRot() * ((float) Math.PI / 180F));
            double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
            Vec3D vec3d1 = new Vec3D(((double) this.random.nextFloat() - 0.5D) * 0.3D, d0, 0.6D);

            vec3d1 = vec3d1.xRot(-this.getXRot() * ((float) Math.PI / 180F));
            vec3d1 = vec3d1.yRot(-this.getYRot() * ((float) Math.PI / 180F));
            vec3d1 = vec3d1.add(this.getX(), this.getEyeY(), this.getZ());
            this.level().addParticle(new ParticleParamItem(Particles.ITEM, itemstack), vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
        }

    }

    protected void completeUsingItem() {
        if (!this.level().isClientSide || this.isUsingItem()) {
            EnumHand enumhand = this.getUsedItemHand();

            if (!this.useItem.equals(this.getItemInHand(enumhand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    // CraftBukkit start - fire PlayerItemConsumeEvent
                    ItemStack itemstack;
                    if (this instanceof EntityPlayer entityPlayer) {
                        org.bukkit.inventory.ItemStack craftItem = CraftItemStack.asBukkitCopy(this.useItem);
                        org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(enumhand);
                        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent((Player) this.getBukkitEntity(), craftItem, hand);
                        this.level().getCraftServer().getPluginManager().callEvent(event);

                        if (event.isCancelled()) {
                            // Update client
                            Consumable consumable = this.useItem.get(DataComponents.CONSUMABLE);
                            if (consumable != null) {
                                consumable.cancelUsingItem(entityPlayer, this.useItem);
                            }
                            entityPlayer.getBukkitEntity().updateInventory();
                            entityPlayer.getBukkitEntity().updateScaledHealth();
                            return;
                        }

                        itemstack = (craftItem.equals(event.getItem())) ? this.useItem.finishUsingItem(this.level(), this) : CraftItemStack.asNMSCopy(event.getItem()).finishUsingItem(this.level(), this);
                    } else {
                        itemstack = this.useItem.finishUsingItem(this.level(), this);
                    }
                    // CraftBukkit end

                    if (itemstack != this.useItem) {
                        this.setItemInHand(enumhand, itemstack);
                    }

                    this.stopUsingItem();
                }

            }
        }
    }

    public void handleExtraItemsCreatedOnUse(ItemStack itemstack) {}

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        ItemStack itemstack = this.getItemInHand(this.getUsedItemHand());

        if (!this.useItem.isEmpty() && ItemStack.isSameItem(itemstack, this.useItem)) {
            this.useItem = itemstack;
            this.useItem.releaseUsing(this.level(), this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level().isClientSide) {
            boolean flag = this.isUsingItem();

            this.setLivingEntityFlag(1, false);
            if (flag) {
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        return this.getItemBlockingWith() != null;
    }

    @Nullable
    public ItemStack getItemBlockingWith() {
        if (!this.isUsingItem()) {
            return null;
        } else {
            BlocksAttacks blocksattacks = (BlocksAttacks) this.useItem.get(DataComponents.BLOCKS_ATTACKS);

            if (blocksattacks != null) {
                int i = this.useItem.getItem().getUseDuration(this.useItem, this) - this.useItemRemaining;

                if (i >= blocksattacks.blockDelayTicks()) {
                    return this.useItem;
                }
            }

            return null;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(EntityPose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double d0, double d1, double d2, boolean flag) {
        // CraftBukkit start
        return randomTeleport(d0, d1, d2, flag, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN).orElse(false);
    }

    public Optional<Boolean> randomTeleport(double d0, double d1, double d2, boolean flag, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        double d3 = this.getX();
        double d4 = this.getY();
        double d5 = this.getZ();
        double d6 = d1;
        boolean flag1 = false;
        BlockPosition blockposition = BlockPosition.containing(d0, d1, d2);
        World world = this.level();

        if (world.hasChunkAt(blockposition)) {
            boolean flag2 = false;

            while (!flag2 && blockposition.getY() > world.getMinY()) {
                BlockPosition blockposition1 = blockposition.below();
                IBlockData iblockdata = world.getBlockState(blockposition1);

                if (iblockdata.blocksMotion()) {
                    flag2 = true;
                } else {
                    --d6;
                    blockposition = blockposition1;
                }
            }

            if (flag2) {
                // CraftBukkit start - Teleport event
                // this.teleportTo(d0, d6, d2);

                // first set position, to check if the place to teleport is valid
                this.setPos(d0, d6, d2);
                if (world.noCollision((Entity) this) && !world.containsAnyLiquid(this.getBoundingBox())) {
                    flag1 = true;
                }
                // now revert and call event if the teleport place is valid
                this.setPos(d3, d4, d5);

                if (flag1) {
                    if (!(this instanceof EntityPlayer)) {
                        EntityTeleportEvent teleport = new EntityTeleportEvent(this.getBukkitEntity(), new Location(this.level().getWorld(), d3, d4, d5), new Location(this.level().getWorld(), d0, d6, d2));
                        this.level().getCraftServer().getPluginManager().callEvent(teleport);
                        if (!teleport.isCancelled()) {
                            Location to = teleport.getTo();
                            this.teleportTo(to.getX(), to.getY(), to.getZ());
                        } else {
                            return Optional.empty();
                        }
                    } else {
                        // player teleport event is called in the underlining code
                        if (!((EntityPlayer) this).connection.teleport(d0, d6, d2, this.getYRot(), this.getXRot(), cause)) {
                            return Optional.empty();
                        }
                    }
                }
                // CraftBukkit end
            }
        }

        if (!flag1) {
            // this.enderTeleportTo(d3, d4, d5); // CraftBukkit - already set the location back
            return Optional.of(false); // CraftBukkit
        } else {
            if (flag) {
                world.broadcastEntityEvent(this, (byte) 46);
            }

            if (this instanceof EntityCreature) {
                EntityCreature entitycreature = (EntityCreature) this;

                entitycreature.getNavigation().stop();
            }

            return Optional.of(true); // CraftBukkit
        }
    }

    public boolean isAffectedByPotions() {
        return !this.isDeadOrDying();
    }

    public boolean attackable() {
        return true;
    }

    public void setRecordPlayingNearby(BlockPosition blockposition, boolean flag) {}

    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public final EntitySize getDimensions(EntityPose entitypose) {
        return entitypose == EntityPose.SLEEPING ? EntityLiving.SLEEPING_DIMENSIONS : this.getDefaultDimensions(entitypose).scale(this.getScale());
    }

    protected EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.getType().getDimensions().scale(this.getAgeScale());
    }

    public ImmutableList<EntityPose> getDismountPoses() {
        return ImmutableList.of(EntityPose.STANDING);
    }

    public AxisAlignedBB getLocalBoundsForPose(EntityPose entitypose) {
        EntitySize entitysize = this.getDimensions(entitypose);

        return new AxisAlignedBB((double) (-entitysize.width() / 2.0F), 0.0D, (double) (-entitysize.width() / 2.0F), (double) (entitysize.width() / 2.0F), (double) entitysize.height(), (double) (entitysize.width() / 2.0F));
    }

    protected boolean wouldNotSuffocateAtTargetPose(EntityPose entitypose) {
        AxisAlignedBB axisalignedbb = this.getDimensions(entitypose).makeBoundingBox(this.position());

        return this.level().noBlockCollision(this, axisalignedbb);
    }

    @Override
    public boolean canUsePortal(boolean flag) {
        return super.canUsePortal(flag) && !this.isSleeping();
    }

    public Optional<BlockPosition> getSleepingPos() {
        return (Optional) this.entityData.get(EntityLiving.SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPosition blockposition) {
        this.entityData.set(EntityLiving.SLEEPING_POS_ID, Optional.of(blockposition));
    }

    public void clearSleepingPos() {
        this.entityData.set(EntityLiving.SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPosition blockposition) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        IBlockData iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.getBlock() instanceof BlockBed) {
            this.level().setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockBed.OCCUPIED, true), 3);
        }

        this.setPose(EntityPose.SLEEPING);
        this.setPosToBed(blockposition);
        this.setSleepingPos(blockposition);
        this.setDeltaMovement(Vec3D.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPosition blockposition) {
        this.setPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.6875D, (double) blockposition.getZ() + 0.5D);
    }

    private boolean checkBedExists() {
        return (Boolean) this.getSleepingPos().map((blockposition) -> {
            return this.level().getBlockState(blockposition).getBlock() instanceof BlockBed;
        }).orElse(false);
    }

    public void stopSleeping() {
        Optional<BlockPosition> optional = this.getSleepingPos(); // CraftBukkit - decompile error
        World world = this.level();

        java.util.Objects.requireNonNull(world);
        optional.filter(world::hasChunkAt).ifPresent((blockposition) -> {
            IBlockData iblockdata = this.level().getBlockState(blockposition);

            if (iblockdata.getBlock() instanceof BlockBed) {
                EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockBed.FACING);

                this.level().setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockBed.OCCUPIED, false), 3);
                Vec3D vec3d = (Vec3D) BlockBed.findStandUpPosition(this.getType(), this.level(), blockposition, enumdirection, this.getYRot()).orElseGet(() -> {
                    BlockPosition blockposition1 = blockposition.above();

                    return new Vec3D((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.1D, (double) blockposition1.getZ() + 0.5D);
                });
                Vec3D vec3d1 = Vec3D.atBottomCenterOf(blockposition).subtract(vec3d).normalize();
                float f = (float) MathHelper.wrapDegrees(MathHelper.atan2(vec3d1.z, vec3d1.x) * (double) (180F / (float) Math.PI) - 90.0D);

                this.setPos(vec3d.x, vec3d.y, vec3d.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }

        });
        Vec3D vec3d = this.position();

        this.setPose(EntityPose.STANDING);
        this.setPos(vec3d.x, vec3d.y, vec3d.z);
        this.clearSleepingPos();
    }

    @Nullable
    public EnumDirection getBedOrientation() {
        BlockPosition blockposition = (BlockPosition) this.getSleepingPos().orElse(null); // CraftBukkit - decompile error

        return blockposition != null ? BlockBed.getBedOrientation(this.level(), blockposition) : null;
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    public ItemStack getProjectile(ItemStack itemstack) {
        return ItemStack.EMPTY;
    }

    private static byte entityEventForEquipmentBreak(EnumItemSlot enumitemslot) {
        byte b0;

        switch (enumitemslot) {
            case MAINHAND:
                b0 = 47;
                break;
            case OFFHAND:
                b0 = 48;
                break;
            case HEAD:
                b0 = 49;
                break;
            case CHEST:
                b0 = 50;
                break;
            case FEET:
                b0 = 52;
                break;
            case LEGS:
                b0 = 51;
                break;
            case BODY:
                b0 = 65;
                break;
            case SADDLE:
                b0 = 68;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return b0;
    }

    public void onEquippedItemBroken(Item item, EnumItemSlot enumitemslot) {
        this.level().broadcastEntityEvent(this, entityEventForEquipmentBreak(enumitemslot));
        this.stopLocationBasedEffects(this.getItemBySlot(enumitemslot), enumitemslot, this.attributes);
    }

    private void stopLocationBasedEffects(ItemStack itemstack, EnumItemSlot enumitemslot, AttributeMapBase attributemapbase) {
        itemstack.forEachModifier(enumitemslot, (holder, attributemodifier) -> {
            AttributeModifiable attributemodifiable = attributemapbase.getInstance(holder);

            if (attributemodifiable != null) {
                attributemodifiable.removeModifier(attributemodifier);
            }

        });
        EnchantmentManager.stopLocationBasedEffects(itemstack, this, enumitemslot);
    }

    public static EnumItemSlot getSlotForHand(EnumHand enumhand) {
        return enumhand == EnumHand.MAIN_HAND ? EnumItemSlot.MAINHAND : EnumItemSlot.OFFHAND;
    }

    public final boolean canEquipWithDispenser(ItemStack itemstack) {
        if (this.isAlive() && !this.isSpectator()) {
            Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

            if (equippable != null && equippable.dispensable()) {
                EnumItemSlot enumitemslot = equippable.slot();

                return this.canUseSlot(enumitemslot) && equippable.canBeEquippedBy(this.getType()) ? this.getItemBySlot(enumitemslot).isEmpty() && this.canDispenserEquipIntoSlot(enumitemslot) : false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected boolean canDispenserEquipIntoSlot(EnumItemSlot enumitemslot) {
        return true;
    }

    public final EnumItemSlot getEquipmentSlotForItem(ItemStack itemstack) {
        Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

        return equippable != null && this.canUseSlot(equippable.slot()) ? equippable.slot() : EnumItemSlot.MAINHAND;
    }

    public final boolean isEquippableInSlot(ItemStack itemstack, EnumItemSlot enumitemslot) {
        Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

        return equippable == null ? enumitemslot == EnumItemSlot.MAINHAND && this.canUseSlot(EnumItemSlot.MAINHAND) : enumitemslot == equippable.slot() && this.canUseSlot(equippable.slot()) && equippable.canBeEquippedBy(this.getType());
    }

    private static SlotAccess createEquipmentSlotAccess(EntityLiving entityliving, EnumItemSlot enumitemslot) {
        return enumitemslot != EnumItemSlot.HEAD && enumitemslot != EnumItemSlot.MAINHAND && enumitemslot != EnumItemSlot.OFFHAND ? SlotAccess.forEquipmentSlot(entityliving, enumitemslot, (itemstack) -> {
            return itemstack.isEmpty() || entityliving.getEquipmentSlotForItem(itemstack) == enumitemslot;
        }) : SlotAccess.forEquipmentSlot(entityliving, enumitemslot);
    }

    @Nullable
    private static EnumItemSlot getEquipmentSlot(int i) {
        return i == 100 + EnumItemSlot.HEAD.getIndex() ? EnumItemSlot.HEAD : (i == 100 + EnumItemSlot.CHEST.getIndex() ? EnumItemSlot.CHEST : (i == 100 + EnumItemSlot.LEGS.getIndex() ? EnumItemSlot.LEGS : (i == 100 + EnumItemSlot.FEET.getIndex() ? EnumItemSlot.FEET : (i == 98 ? EnumItemSlot.MAINHAND : (i == 99 ? EnumItemSlot.OFFHAND : (i == 105 ? EnumItemSlot.BODY : (i == 106 ? EnumItemSlot.SADDLE : null)))))));
    }

    @Override
    public SlotAccess getSlot(int i) {
        EnumItemSlot enumitemslot = getEquipmentSlot(i);

        return enumitemslot != null ? createEquipmentSlotAccess(this, enumitemslot) : super.getSlot(i);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        } else {
            for (EnumItemSlot enumitemslot : EquipmentSlotGroup.ARMOR) {
                if (this.getItemBySlot(enumitemslot).is(TagsItem.FREEZE_IMMUNE_WEARABLES)) {
                    return false;
                }
            }

            return super.canFreeze();
        }
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level().isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        double d0 = packetplayoutspawnentity.getX();
        double d1 = packetplayoutspawnentity.getY();
        double d2 = packetplayoutspawnentity.getZ();
        float f = packetplayoutspawnentity.getYRot();
        float f1 = packetplayoutspawnentity.getXRot();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.yBodyRot = packetplayoutspawnentity.getYHeadRot();
        this.yHeadRot = packetplayoutspawnentity.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(packetplayoutspawnentity.getId());
        this.setUUID(packetplayoutspawnentity.getUUID());
        this.absSnapTo(d0, d1, d2, f, f1);
        this.setDeltaMovement(packetplayoutspawnentity.getXa(), packetplayoutspawnentity.getYa(), packetplayoutspawnentity.getZa());
    }

    public float getSecondsToDisableBlocking() {
        Weapon weapon = (Weapon) this.getWeaponItem().get(DataComponents.WEAPON);

        return weapon != null ? weapon.disableBlockingForSeconds() : 0.0F;
    }

    @Override
    public float maxUpStep() {
        float f = (float) this.getAttributeValue(GenericAttributes.STEP_HEIGHT);

        return this.getControllingPassenger() instanceof EntityHuman ? Math.max(f, 1.0F) : f;
    }

    @Override
    public Vec3D getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.getDimensions(this.getPose()), this.getScale() * this.getAgeScale()));
    }

    protected void lerpHeadRotationStep(int i, double d0) {
        this.yHeadRot = (float) MathHelper.rotLerp(1.0D / (double) i, (double) this.yHeadRot, d0);
    }

    @Override
    public void igniteForTicks(int i) {
        super.igniteForTicks(MathHelper.ceil((double) i * this.getAttributeValue(GenericAttributes.BURNING_TIME)));
    }

    public boolean hasInfiniteMaterials() {
        return false;
    }

    public boolean isInvulnerableTo(WorldServer worldserver, DamageSource damagesource) {
        return this.isInvulnerableToBase(damagesource) || EnchantmentManager.isImmuneToDamage(worldserver, this, damagesource);
    }

    public static boolean canGlideUsing(ItemStack itemstack, EnumItemSlot enumitemslot) {
        if (!itemstack.has(DataComponents.GLIDER)) {
            return false;
        } else {
            Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

            return equippable != null && enumitemslot == equippable.slot() && !itemstack.nextDamageWillBreak();
        }
    }

    @VisibleForTesting
    public int getLastHurtByPlayerMemoryTime() {
        return this.lastHurtByPlayerMemoryTime;
    }

    public static record a(SoundEffect small, SoundEffect big) {

    }
}
