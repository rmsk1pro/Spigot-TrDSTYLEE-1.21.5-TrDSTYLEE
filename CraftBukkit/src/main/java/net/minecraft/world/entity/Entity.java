package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.SystemUtils;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.ICommandListener;
import net.minecraft.commands.arguments.ArgumentAnchor;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleParamBlock;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.chat.ChatClickable;
import net.minecraft.network.chat.ChatHoverable;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatMutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.EntityTrackerEntry;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsEntity;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.INamableTileEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IMaterial;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockFenceGate;
import net.minecraft.world.level.block.BlockHoney;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnumBlockMirror;
import net.minecraft.world.level.block.EnumBlockRotation;
import net.minecraft.world.level.block.EnumRenderType;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.SoundEffectType;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.material.EnumPistonReaction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.portal.BlockPortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.OperatorBoolean;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;
import org.jetbrains.annotations.Contract;

// CraftBukkit start
import net.minecraft.network.protocol.game.PacketPlayOutAttachEntity;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.world.level.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityUnleashEvent.UnleashReason;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
// CraftBukkit end

public abstract class Entity implements SyncedDataHolder, INamableTileEntity, EntityAccess, ScoreHolder, DataComponentGetter {

    // CraftBukkit start
    private static final int CURRENT_LEVEL = 2;
    static boolean isLevelAtLeast(NBTTagCompound tag, int level) {
        return tag.contains("Bukkit.updateLevel") && tag.getIntOr("Bukkit.updateLevel", -1) >= level;
    }

    private CraftEntity bukkitEntity;

    public CraftEntity getBukkitEntity() {
        if (bukkitEntity == null) {
            bukkitEntity = CraftEntity.getEntity(level.getCraftServer(), this);
        }
        return bukkitEntity;
    }

    // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    public int getDefaultMaxAirSupply() {
        return TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    public static final String ID_TAG = "id";
    public static final String PASSENGERS_TAG = "Passengers";
    private static final String DATA_TAG = "data";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    public static final int CONTENTS_SLOT_INDEX = 0;
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    private static final Codec<List<String>> TAG_LIST_CODEC = Codec.STRING.sizeLimitedListOf(1024);
    public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001D;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999D;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    public static final int BASE_SAFE_FALL_DISTANCE = 3;
    private static final ImmutableList<EnumDirection.EnumAxis> YXZ_AXIS_ORDER = ImmutableList.of(EnumDirection.EnumAxis.Y, EnumDirection.EnumAxis.X, EnumDirection.EnumAxis.Z);
    private static final ImmutableList<EnumDirection.EnumAxis> YZX_AXIS_ORDER = ImmutableList.of(EnumDirection.EnumAxis.Y, EnumDirection.EnumAxis.Z, EnumDirection.EnumAxis.X);
    private static final AxisAlignedBB INITIAL_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static final double WATER_FLOW_SCALE = 0.014D;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007D;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335D;
    public static final String UUID_TAG = "UUID";
    private static double viewScale = 1.0D;
    private final EntityTypes<?> type;
    private int id;
    public boolean blocksBuilding;
    public ImmutableList<Entity> passengers;
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    private World level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3D position;
    private BlockPosition blockPosition;
    private ChunkCoordIntPair chunkPosition;
    private Vec3D deltaMovement;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AxisAlignedBB bb;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3D stuckSpeedMultiplier;
    @Nullable
    private Entity.RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float moveDist;
    public float flyDist;
    public double fallDistance;
    private float nextStep;
    public double xOld;
    public double yOld;
    public double zOld;
    public boolean noPhysics;
    public final RandomSource random;
    public int tickCount;
    private int remainingFireTicks;
    public boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<FluidType>> fluidHeight;
    protected boolean wasEyeInWater;
    private final Set<TagKey<FluidType>> fluidOnEyes;
    public int invulnerableTime;
    protected boolean firstTick;
    protected final DataWatcher entityData;
    protected static final DataWatcherObject<Byte> DATA_SHARED_FLAGS_ID = DataWatcher.<Byte>defineId(Entity.class, DataWatcherRegistry.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    private static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final DataWatcherObject<Integer> DATA_AIR_SUPPLY_ID = DataWatcher.<Integer>defineId(Entity.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Optional<IChatBaseComponent>> DATA_CUSTOM_NAME = DataWatcher.<Optional<IChatBaseComponent>>defineId(Entity.class, DataWatcherRegistry.OPTIONAL_COMPONENT);
    private static final DataWatcherObject<Boolean> DATA_CUSTOM_NAME_VISIBLE = DataWatcher.<Boolean>defineId(Entity.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> DATA_SILENT = DataWatcher.<Boolean>defineId(Entity.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> DATA_NO_GRAVITY = DataWatcher.<Boolean>defineId(Entity.class, DataWatcherRegistry.BOOLEAN);
    protected static final DataWatcherObject<EntityPose> DATA_POSE = DataWatcher.<EntityPose>defineId(Entity.class, DataWatcherRegistry.POSE);
    private static final DataWatcherObject<Integer> DATA_TICKS_FROZEN = DataWatcher.<Integer>defineId(Entity.class, DataWatcherRegistry.INT);
    private EntityInLevelCallback levelCallback;
    private final VecDeltaCodec packetPositionCodec;
    public boolean hasImpulse;
    @Nullable
    public PortalProcessor portalProcess;
    public int portalCooldown;
    private boolean invulnerable;
    protected UUID uuid;
    protected String stringUUID;
    private boolean hasGlowingTag;
    private final Set<String> tags;
    private final double[] pistonDeltas;
    private long pistonDeltasGameTime;
    private EntitySize dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public Optional<BlockPosition> mainSupportingBlockPos;
    private boolean onGroundNoBlocks;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    public boolean hasVisualFire;
    @Nullable
    private IBlockData inBlockState;
    private final List<List<Entity.b>> movementThisTick;
    private final List<Entity.b> finalMovementsThisTick;
    private final LongSet visitedBlocks;
    private final InsideBlockEffectApplier.a insideEffectCollector;
    private CustomData customData;
    // CraftBukkit start
    public boolean forceDrops;
    public boolean persist = true;
    public boolean visibleByDefault = true;
    public boolean valid;
    public boolean inWorld = false;
    public boolean generation;
    public int maxAirTicks = getDefaultMaxAirSupply(); // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    public org.bukkit.projectiles.ProjectileSource projectileSource; // For projectiles only
    public boolean lastDamageCancelled; // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Keep track if the event was canceled
    public boolean persistentInvisibility = false;
    public BlockPosition lastLavaContact;
    // Marks an entity, that it was removed by a plugin via Entity#remove
    // Main use case currently is for SPIGOT-7487, preventing dropping of leash when leash is removed
    public boolean pluginRemoved = false;

    public float getBukkitYaw() {
        return this.yRot;
    }

    public boolean isChunkLoaded() {
        return level.hasChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4);
    }
    // CraftBukkit end

    public Entity(EntityTypes<?> entitytypes, World world) {
        this.id = Entity.ENTITY_COUNTER.incrementAndGet();
        this.passengers = ImmutableList.of();
        this.deltaMovement = Vec3D.ZERO;
        this.bb = Entity.INITIAL_AABB;
        this.stuckSpeedMultiplier = Vec3D.ZERO;
        this.nextStep = 1.0F;
        this.random = RandomSource.create();
        this.remainingFireTicks = -this.getFireImmuneTicks();
        this.fluidHeight = new Object2DoubleArrayMap(2);
        this.fluidOnEyes = new HashSet();
        this.firstTick = true;
        this.levelCallback = EntityInLevelCallback.NULL;
        this.packetPositionCodec = new VecDeltaCodec();
        this.uuid = MathHelper.createInsecureUUID(this.random);
        this.stringUUID = this.uuid.toString();
        this.tags = Sets.newHashSet();
        this.pistonDeltas = new double[]{0.0D, 0.0D, 0.0D};
        this.mainSupportingBlockPos = Optional.empty();
        this.onGroundNoBlocks = false;
        this.inBlockState = null;
        this.movementThisTick = new ObjectArrayList();
        this.finalMovementsThisTick = new ObjectArrayList();
        this.visitedBlocks = new LongOpenHashSet();
        this.insideEffectCollector = new InsideBlockEffectApplier.a();
        this.customData = CustomData.EMPTY;
        this.type = entitytypes;
        this.level = world;
        this.dimensions = entitytypes.getDimensions();
        this.position = Vec3D.ZERO;
        this.blockPosition = BlockPosition.ZERO;
        this.chunkPosition = ChunkCoordIntPair.ZERO;
        DataWatcher.a datawatcher_a = new DataWatcher.a(this);

        datawatcher_a.define(Entity.DATA_SHARED_FLAGS_ID, (byte) 0);
        datawatcher_a.define(Entity.DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        datawatcher_a.define(Entity.DATA_CUSTOM_NAME_VISIBLE, false);
        datawatcher_a.define(Entity.DATA_CUSTOM_NAME, Optional.empty());
        datawatcher_a.define(Entity.DATA_SILENT, false);
        datawatcher_a.define(Entity.DATA_NO_GRAVITY, false);
        datawatcher_a.define(Entity.DATA_POSE, EntityPose.STANDING);
        datawatcher_a.define(Entity.DATA_TICKS_FROZEN, 0);
        this.defineSynchedData(datawatcher_a);
        this.entityData = datawatcher_a.build();
        this.setPos(0.0D, 0.0D, 0.0D);
        this.eyeHeight = this.dimensions.eyeHeight();
    }

    public boolean isColliding(BlockPosition blockposition, IBlockData iblockdata) {
        VoxelShape voxelshape = iblockdata.getCollisionShape(this.level(), blockposition, VoxelShapeCollision.of(this)).move((BaseBlockPosition) blockposition);

        return VoxelShapes.joinIsNotEmpty(voxelshape, VoxelShapes.create(this.getBoundingBox()), OperatorBoolean.AND);
    }

    public int getTeamColor() {
        ScoreboardTeamBase scoreboardteambase = this.getTeam();

        return scoreboardteambase != null && scoreboardteambase.getColor().getColor() != null ? scoreboardteambase.getColor().getColor() : 16777215;
    }

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

    }

    public void syncPacketPositionCodec(double d0, double d1, double d2) {
        this.packetPositionCodec.setBase(new Vec3D(d0, d1, d2));
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityTypes<?> getType() {
        return this.type;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public void setId(int i) {
        this.id = i;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String s) {
        return this.tags.size() >= 1024 ? false : this.tags.add(s);
    }

    public boolean removeTag(String s) {
        return this.tags.remove(s);
    }

    public void kill(WorldServer worldserver) {
        this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        // CraftBukkit start - add Bukkit remove cause
        this.discard(null);
    }

    public final void discard(EntityRemoveEvent.Cause cause) {
        this.remove(Entity.RemovalReason.DISCARDED, cause);
        // CraftBukkit end
    }

    protected abstract void defineSynchedData(DataWatcher.a datawatcher_a);

    public DataWatcher getEntityData() {
        return this.entityData;
    }

    // CraftBukkit start
    public void refreshEntityData(EntityPlayer to) {
        List<DataWatcher.c<?>> list = this.getEntityData().getNonDefaultValues();

        if (list != null) {
            to.connection.send(new PacketPlayOutEntityMetadata(this.getId(), list));
        }
    }
    // CraftBukkit end

    public boolean equals(Object object) {
        return object instanceof Entity ? ((Entity) object).id == this.id : false;
    }

    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        this.setRemoved(entity_removalreason, null);
    }

    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        this.setRemoved(entity_removalreason, cause);
        // CraftBukkit end
    }

    public void onClientRemoval() {}

    public void onRemoval(Entity.RemovalReason entity_removalreason) {}

    public void setPose(EntityPose entitypose) {
        // CraftBukkit start
        if (entitypose == this.getPose()) {
            return;
        }
        this.level.getCraftServer().getPluginManager().callEvent(new EntityPoseChangeEvent(this.getBukkitEntity(), Pose.values()[entitypose.ordinal()]));
        // CraftBukkit end
        this.entityData.set(Entity.DATA_POSE, entitypose);
    }

    public EntityPose getPose() {
        return (EntityPose) this.entityData.get(Entity.DATA_POSE);
    }

    public boolean hasPose(EntityPose entitypose) {
        return this.getPose() == entitypose;
    }

    public boolean closerThan(Entity entity, double d0) {
        return this.position().closerThan(entity.position(), d0);
    }

    public boolean closerThan(Entity entity, double d0, double d1) {
        double d2 = entity.getX() - this.getX();
        double d3 = entity.getY() - this.getY();
        double d4 = entity.getZ() - this.getZ();

        return MathHelper.lengthSquared(d2, d4) < MathHelper.square(d0) && MathHelper.square(d3) < MathHelper.square(d1);
    }

    protected void setRot(float f, float f1) {
        // CraftBukkit start - yaw was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(f)) {
            f = 0;
        }

        if (f == Float.POSITIVE_INFINITY || f == Float.NEGATIVE_INFINITY) {
            if (this instanceof EntityPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid yaw");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite yaw (Hacking?)");
            }
            f = 0;
        }

        // pitch was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(f1)) {
            f1 = 0;
        }

        if (f1 == Float.POSITIVE_INFINITY || f1 == Float.NEGATIVE_INFINITY) {
            if (this instanceof EntityPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid pitch");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite pitch (Hacking?)");
            }
            f1 = 0;
        }
        // CraftBukkit end

        this.setYRot(f % 360.0F);
        this.setXRot(f1 % 360.0F);
    }

    public final void setPos(Vec3D vec3d) {
        this.setPos(vec3d.x(), vec3d.y(), vec3d.z());
    }

    public void setPos(double d0, double d1, double d2) {
        this.setPosRaw(d0, d1, d2);
        this.setBoundingBox(this.makeBoundingBox());
    }

    protected final AxisAlignedBB makeBoundingBox() {
        return this.makeBoundingBox(this.position);
    }

    protected AxisAlignedBB makeBoundingBox(Vec3D vec3d) {
        return this.dimensions.makeBoundingBox(vec3d);
    }

    protected void reapplyPosition() {
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void turn(double d0, double d1) {
        float f = (float) d1 * 0.15F;
        float f1 = (float) d0 * 0.15F;

        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + f1);
        this.setXRot(MathHelper.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += f1;
        this.xRotO = MathHelper.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }

    }

    public void tick() {
        this.baseTick();
    }

    // CraftBukkit start
    public void postTick() {
        // No clean way to break out of ticking once the entity has been copied to a new world, so instead we move the portalling later in the tick cycle
        if (!(this instanceof EntityPlayer)) {
            this.handlePortal();
        }
    }
    // CraftBukkit end

    public void baseTick() {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("entityBaseTick");
        this.inBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            --this.boardingCooldown;
        }

        if (this instanceof EntityPlayer) this.handlePortal(); // CraftBukkit - // Moved up to postTick
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.remainingFireTicks > 0) {
                if (this.fireImmune()) {
                    this.setRemainingFireTicks(this.remainingFireTicks - 4);
                    if (this.remainingFireTicks < 0) {
                        this.clearFire();
                    }
                } else {
                    if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                        this.hurtServer(worldserver, this.damageSources().onFire(), 1.0F);
                    }

                    this.setRemainingFireTicks(this.remainingFireTicks - 1);
                }
            }
        } else {
            this.clearFire();
        }

        if (this.isInLava()) {
            this.fallDistance *= 0.5D;
            // CraftBukkit start
        } else {
            this.lastLavaContact = null;
            // CraftBukkit end
        }

        this.checkBelowWorld();
        if (!this.level().isClientSide) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        world = this.level();
        if (world instanceof WorldServer worldserver1) {
            if (this instanceof Leashable) {
                Leashable.tickLeash(worldserver1, (Entity & Leashable) this); // CraftBukkit - decompile error
            }
        }

        gameprofilerfiller.pop();
    }

    public void setSharedFlagOnFire(boolean flag) {
        this.setSharedFlag(0, flag || this.hasVisualFire);
    }

    public void checkBelowWorld() {
        if (this.getY() < (double) (this.level().getMinY() - 64)) {
            this.onBelowWorld();
        }

    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public void setPortalCooldown(int i) {
        this.portalCooldown = i;
    }

    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            --this.portalCooldown;
        }

    }

    public void lavaIgnite() {
        if (!this.fireImmune()) {
            // CraftBukkit start - Fallen in lava TODO: this event spams!
            if (this instanceof EntityLiving && remainingFireTicks <= 0) {
                // not on fire yet
                org.bukkit.block.Block damager = (lastLavaContact == null) ? null : org.bukkit.craftbukkit.block.CraftBlock.at(level, lastLavaContact);
                org.bukkit.entity.Entity damagee = this.getBukkitEntity();
                EntityCombustEvent combustEvent = new org.bukkit.event.entity.EntityCombustByBlockEvent(damager, damagee, 15);
                this.level.getCraftServer().getPluginManager().callEvent(combustEvent);

                if (!combustEvent.isCancelled()) {
                    this.igniteForSeconds(combustEvent.getDuration(), false);
                }
            } else {
                // This will be called every single tick the entity is in lava, so don't throw an event
                this.igniteForSeconds(15.0F, false);
            }
            // CraftBukkit end
        }
    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (this.hurtServer(worldserver, this.damageSources().lava().directBlock(level, lastLavaContact), 4.0F) && this.shouldPlayLavaHurtSound() && !this.isSilent()) { // CraftBukkit
                    worldserver.playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.GENERIC_BURN, this.getSoundSource(), 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
                }
            }

        }
    }

    protected boolean shouldPlayLavaHurtSound() {
        return true;
    }

    public final void igniteForSeconds(float f) {
        // CraftBukkit start
        this.igniteForSeconds(f, true);
    }

    public final void igniteForSeconds(float f, boolean callEvent) {
        if (callEvent) {
            EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity(), f);
            this.level.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            f = event.getDuration();
        }
        // CraftBukkit end
        this.igniteForTicks(MathHelper.floor(f * 20.0F));
    }

    public void igniteForTicks(int i) {
        if (this.remainingFireTicks < i) {
            this.setRemainingFireTicks(i);
        }

        this.clearFreeze();
    }

    public void setRemainingFireTicks(int i) {
        this.remainingFireTicks = i;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void onBelowWorld() {
        this.discard(EntityRemoveEvent.Cause.OUT_OF_WORLD); // CraftBukkit - add Bukkit remove cause
    }

    public boolean isFree(double d0, double d1, double d2) {
        return this.isFree(this.getBoundingBox().move(d0, d1, d2));
    }

    private boolean isFree(AxisAlignedBB axisalignedbb) {
        return this.level().noCollision(this, axisalignedbb) && !this.level().containsAnyLiquid(axisalignedbb);
    }

    public void setOnGround(boolean flag) {
        this.onGround = flag;
        this.checkSupportingBlock(flag, (Vec3D) null);
    }

    public void setOnGroundWithMovement(boolean flag, Vec3D vec3d) {
        this.setOnGroundWithMovement(flag, this.horizontalCollision, vec3d);
    }

    public void setOnGroundWithMovement(boolean flag, boolean flag1, Vec3D vec3d) {
        this.onGround = flag;
        this.horizontalCollision = flag1;
        this.checkSupportingBlock(flag, vec3d);
    }

    public boolean isSupportedBy(BlockPosition blockposition) {
        return this.mainSupportingBlockPos.isPresent() && ((BlockPosition) this.mainSupportingBlockPos.get()).equals(blockposition);
    }

    protected void checkSupportingBlock(boolean flag, @Nullable Vec3D vec3d) {
        if (flag) {
            AxisAlignedBB axisalignedbb = this.getBoundingBox();
            AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(axisalignedbb.minX, axisalignedbb.minY - 1.0E-6D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
            Optional<BlockPosition> optional = this.level.findSupportingBlock(this, axisalignedbb1);

            if (!optional.isPresent() && !this.onGroundNoBlocks) {
                if (vec3d != null) {
                    AxisAlignedBB axisalignedbb2 = axisalignedbb1.move(-vec3d.x, 0.0D, -vec3d.z);

                    optional = this.level.findSupportingBlock(this, axisalignedbb2);
                    this.mainSupportingBlockPos = optional;
                }
            } else {
                this.mainSupportingBlockPos = optional;
            }

            this.onGroundNoBlocks = optional.isEmpty();
        } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
                this.mainSupportingBlockPos = Optional.empty();
            }
        }

    }

    public boolean onGround() {
        return this.onGround;
    }

    public void move(EnumMoveType enummovetype, Vec3D vec3d) {
        if (this.noPhysics) {
            this.setPos(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z);
        } else {
            if (enummovetype == EnumMoveType.PISTON) {
                vec3d = this.limitPistonMovement(vec3d);
                if (vec3d.equals(Vec3D.ZERO)) {
                    return;
                }
            }

            GameProfilerFiller gameprofilerfiller = Profiler.get();

            gameprofilerfiller.push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
                vec3d = vec3d.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3D.ZERO;
                this.setDeltaMovement(Vec3D.ZERO);
            }

            vec3d = this.maybeBackOffFromEdge(vec3d, enummovetype);
            Vec3D vec3d1 = this.collide(vec3d);
            double d0 = vec3d1.lengthSqr();

            if (d0 > 1.0E-7D || vec3d.lengthSqr() - d0 < 1.0E-7D) {
                if (this.fallDistance != 0.0D && d0 >= 1.0D) {
                    MovingObjectPositionBlock movingobjectpositionblock = this.level().clip(new RayTrace(this.position(), this.position().add(vec3d1), RayTrace.BlockCollisionOption.FALLDAMAGE_RESETTING, RayTrace.FluidCollisionOption.WATER, this));

                    if (movingobjectpositionblock.getType() != MovingObjectPosition.EnumMovingObjectType.MISS) {
                        this.resetFallDistance();
                    }
                }

                Vec3D vec3d2 = this.position();
                List<Entity.b> list = new ObjectArrayList();

                for (EnumDirection.EnumAxis enumdirection_enumaxis : axisStepOrder(vec3d1)) {
                    double d1 = vec3d1.get(enumdirection_enumaxis);

                    if (d1 != 0.0D) {
                        Vec3D vec3d3 = vec3d2.relative(enumdirection_enumaxis.getPositive(), d1);

                        list.add(new Entity.b(vec3d2, vec3d3));
                        vec3d2 = vec3d3;
                    }
                }

                this.movementThisTick.add(list);
                this.setPos(vec3d2);
            }

            gameprofilerfiller.pop();
            gameprofilerfiller.push("rest");
            boolean flag = !MathHelper.equal(vec3d.x, vec3d1.x);
            boolean flag1 = !MathHelper.equal(vec3d.z, vec3d1.z);

            this.horizontalCollision = flag || flag1;
            if (Math.abs(vec3d.y) > 0.0D || this.isLocalInstanceAuthoritative()) {
                this.verticalCollision = vec3d.y != vec3d1.y;
                this.verticalCollisionBelow = this.verticalCollision && vec3d.y < 0.0D;
                this.setOnGroundWithMovement(this.verticalCollisionBelow, this.horizontalCollision, vec3d1);
            }

            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec3d1);
            } else {
                this.minorHorizontalCollision = false;
            }

            BlockPosition blockposition = this.getOnPosLegacy();
            IBlockData iblockdata = this.level().getBlockState(blockposition);

            if (this.isLocalInstanceAuthoritative()) {
                this.checkFallDamage(vec3d1.y, this.onGround(), iblockdata, blockposition);
            }

            if (this.isRemoved()) {
                gameprofilerfiller.pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3D vec3d4 = this.getDeltaMovement();

                    this.setDeltaMovement(flag ? 0.0D : vec3d4.x, vec3d4.y, flag1 ? 0.0D : vec3d4.z);
                }

                if (this.canSimulateMovement()) {
                    Block block = iblockdata.getBlock();

                    if (vec3d.y != vec3d1.y) {
                        block.updateEntityMovementAfterFallOn(this.level(), this);
                    }
                }

                // CraftBukkit start
                if (horizontalCollision && getBukkitEntity() instanceof Vehicle) {
                    Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                    org.bukkit.block.Block bl = this.level.getWorld().getBlockAt(MathHelper.floor(this.getX()), MathHelper.floor(this.getY()), MathHelper.floor(this.getZ()));

                    if (vec3d.x > vec3d1.x) {
                        bl = bl.getRelative(BlockFace.EAST);
                    } else if (vec3d.x < vec3d1.x) {
                        bl = bl.getRelative(BlockFace.WEST);
                    } else if (vec3d.z > vec3d1.z) {
                        bl = bl.getRelative(BlockFace.SOUTH);
                    } else if (vec3d.z < vec3d1.z) {
                        bl = bl.getRelative(BlockFace.NORTH);
                    }

                    if (!bl.getType().isAir()) {
                        VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, bl);
                        level.getCraftServer().getPluginManager().callEvent(event);
                    }
                }
                // CraftBukkit end

                if (!this.level().isClientSide() || this.isLocalInstanceAuthoritative()) {
                    Entity.MovementEmission entity_movementemission = this.getMovementEmission();

                    if (entity_movementemission.emitsAnything() && !this.isPassenger()) {
                        this.applyMovementEmissionAndPlaySound(entity_movementemission, vec3d1, blockposition, iblockdata);
                    }
                }

                float f = this.getBlockSpeedFactor();

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 1.0D, (double) f));
                gameprofilerfiller.pop();
            }
        }
    }

    private void applyMovementEmissionAndPlaySound(Entity.MovementEmission entity_movementemission, Vec3D vec3d, BlockPosition blockposition, IBlockData iblockdata) {
        float f = 0.6F;
        float f1 = (float) (vec3d.length() * (double) 0.6F);
        float f2 = (float) (vec3d.horizontalDistance() * (double) 0.6F);
        BlockPosition blockposition1 = this.getOnPos();
        IBlockData iblockdata1 = this.level().getBlockState(blockposition1);
        boolean flag = this.isStateClimbable(iblockdata1);

        this.moveDist += flag ? f1 : f2;
        this.flyDist += f1;
        if (this.moveDist > this.nextStep && !iblockdata1.isAir()) {
            boolean flag1 = blockposition1.equals(blockposition);
            boolean flag2 = this.vibrationAndSoundEffectsFromBlock(blockposition, iblockdata, entity_movementemission.emitsSounds(), flag1, vec3d);

            if (!flag1) {
                flag2 |= this.vibrationAndSoundEffectsFromBlock(blockposition1, iblockdata1, false, entity_movementemission.emitsEvents(), vec3d);
            }

            if (flag2) {
                this.nextStep = this.nextStep();
            } else if (this.isInWater()) {
                this.nextStep = this.nextStep();
                if (entity_movementemission.emitsSounds()) {
                    this.waterSwimSound();
                }

                if (entity_movementemission.emitsEvents()) {
                    this.gameEvent(GameEvent.SWIM);
                }
            }
        } else if (iblockdata1.isAir()) {
            this.processFlappingMovement();
        }

    }

    protected void applyEffectsFromBlocks() {
        this.finalMovementsThisTick.clear();
        List<List<Entity.b>> list = this.movementThisTick; // CraftBukkit - decompile error
        List list1 = this.finalMovementsThisTick;

        Objects.requireNonNull(this.finalMovementsThisTick);
        list.forEach(list1::addAll);
        this.movementThisTick.clear();
        if (this.finalMovementsThisTick.isEmpty()) {
            this.finalMovementsThisTick.add(new Entity.b(this.oldPosition(), this.position()));
        } else if (((Entity.b) this.finalMovementsThisTick.getLast()).to.distanceToSqr(this.position()) > (double) 9.9999994E-11F) {
            this.finalMovementsThisTick.add(new Entity.b(((Entity.b) this.finalMovementsThisTick.getLast()).to, this.position()));
        }

        this.applyEffectsFromBlocks(this.finalMovementsThisTick);
    }

    public void removeLatestMovementRecordingBatch() {
        if (!this.movementThisTick.isEmpty()) {
            this.movementThisTick.removeLast();
        }

    }

    public void applyEffectsFromBlocks(Vec3D vec3d, Vec3D vec3d1) {
        this.applyEffectsFromBlocks(List.of(new Entity.b(vec3d, vec3d1)));
    }

    private void applyEffectsFromBlocks(List<Entity.b> list) {
        if (this.isAffectedByBlocks()) {
            if (this.onGround()) {
                BlockPosition blockposition = this.getOnPosLegacy();
                IBlockData iblockdata = this.level().getBlockState(blockposition);

                iblockdata.getBlock().stepOn(this.level(), blockposition, iblockdata, this);
            }

            boolean flag = this.isOnFire();
            boolean flag1 = this.isFreezing();

            this.checkInsideBlocks(list, this.insideEffectCollector);
            this.insideEffectCollector.applyAndClear(this);
            if (this.isInRain()) {
                this.clearFire();
            }

            if (flag && !this.isOnFire() || flag1 && !this.isFreezing()) {
                this.playEntityOnFireExtinguishedSound();
            }

            if (flag && !this.isOnFire() && this.remainingFireTicks <= 0) {
                this.setRemainingFireTicks(-this.getFireImmuneTicks());
            }

        }
    }

    protected boolean isAffectedByBlocks() {
        return !this.isRemoved() && !this.noPhysics;
    }

    private boolean isStateClimbable(IBlockData iblockdata) {
        return iblockdata.is(TagsBlock.CLIMBABLE) || iblockdata.is(Blocks.POWDER_SNOW);
    }

    private boolean vibrationAndSoundEffectsFromBlock(BlockPosition blockposition, IBlockData iblockdata, boolean flag, boolean flag1, Vec3D vec3d) {
        if (iblockdata.isAir()) {
            return false;
        } else {
            boolean flag2 = this.isStateClimbable(iblockdata);

            if ((this.onGround() || flag2 || this.isCrouching() && vec3d.y == 0.0D || this.isOnRails()) && !this.isSwimming()) {
                if (flag) {
                    this.walkingStepSound(blockposition, iblockdata);
                }

                if (flag1) {
                    this.level().gameEvent(GameEvent.STEP, this.position(), GameEvent.a.of(this, iblockdata));
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3D vec3d) {
        return false;
    }

    protected void playEntityOnFireExtinguishedSound() {
        if (!this.level.isClientSide()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.GENERIC_EXTINGUISH_FIRE, this.getSoundSource(), 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

    }

    public void extinguishFire() {
        if (this.isOnFire()) {
            this.playEntityOnFireExtinguishedSound();
        }

        this.clearFire();
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }

    }

    /** @deprecated */
    @Deprecated
    public BlockPosition getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    public BlockPosition getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.500001F);
    }

    public BlockPosition getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    protected BlockPosition getOnPos(float f) {
        if (this.mainSupportingBlockPos.isPresent()) {
            BlockPosition blockposition = (BlockPosition) this.mainSupportingBlockPos.get();

            if (f <= 1.0E-5F) {
                return blockposition;
            } else {
                IBlockData iblockdata = this.level().getBlockState(blockposition);

                return ((double) f > 0.5D || !iblockdata.is(TagsBlock.FENCES)) && !iblockdata.is(TagsBlock.WALLS) && !(iblockdata.getBlock() instanceof BlockFenceGate) ? blockposition.atY(MathHelper.floor(this.position.y - (double) f)) : blockposition;
            }
        } else {
            int i = MathHelper.floor(this.position.x);
            int j = MathHelper.floor(this.position.y - (double) f);
            int k = MathHelper.floor(this.position.z);

            return new BlockPosition(i, j, k);
        }
    }

    protected float getBlockJumpFactor() {
        float f = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float f1 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();

        return (double) f == 1.0D ? f1 : f;
    }

    protected float getBlockSpeedFactor() {
        IBlockData iblockdata = this.level().getBlockState(this.blockPosition());
        float f = iblockdata.getBlock().getSpeedFactor();

        return !iblockdata.is(Blocks.WATER) && !iblockdata.is(Blocks.BUBBLE_COLUMN) ? ((double) f == 1.0D ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f) : f;
    }

    protected Vec3D maybeBackOffFromEdge(Vec3D vec3d, EnumMoveType enummovetype) {
        return vec3d;
    }

    protected Vec3D limitPistonMovement(Vec3D vec3d) {
        if (vec3d.lengthSqr() <= 1.0E-7D) {
            return vec3d;
        } else {
            long i = this.level().getGameTime();

            if (i != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0D);
                this.pistonDeltasGameTime = i;
            }

            if (vec3d.x != 0.0D) {
                double d0 = this.applyPistonMovementRestriction(EnumDirection.EnumAxis.X, vec3d.x);

                return Math.abs(d0) <= (double) 1.0E-5F ? Vec3D.ZERO : new Vec3D(d0, 0.0D, 0.0D);
            } else if (vec3d.y != 0.0D) {
                double d1 = this.applyPistonMovementRestriction(EnumDirection.EnumAxis.Y, vec3d.y);

                return Math.abs(d1) <= (double) 1.0E-5F ? Vec3D.ZERO : new Vec3D(0.0D, d1, 0.0D);
            } else if (vec3d.z != 0.0D) {
                double d2 = this.applyPistonMovementRestriction(EnumDirection.EnumAxis.Z, vec3d.z);

                return Math.abs(d2) <= (double) 1.0E-5F ? Vec3D.ZERO : new Vec3D(0.0D, 0.0D, d2);
            } else {
                return Vec3D.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(EnumDirection.EnumAxis enumdirection_enumaxis, double d0) {
        int i = enumdirection_enumaxis.ordinal();
        double d1 = MathHelper.clamp(d0 + this.pistonDeltas[i], -0.51D, 0.51D);

        d0 = d1 - this.pistonDeltas[i];
        this.pistonDeltas[i] = d1;
        return d0;
    }

    private Vec3D collide(Vec3D vec3d) {
        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        List<VoxelShape> list = this.level().getEntityCollisions(this, axisalignedbb.expandTowards(vec3d));
        Vec3D vec3d1 = vec3d.lengthSqr() == 0.0D ? vec3d : collideBoundingBox(this, vec3d, axisalignedbb, this.level(), list);
        boolean flag = vec3d.x != vec3d1.x;
        boolean flag1 = vec3d.y != vec3d1.y;
        boolean flag2 = vec3d.z != vec3d1.z;
        boolean flag3 = flag1 && vec3d.y < 0.0D;

        if (this.maxUpStep() > 0.0F && (flag3 || this.onGround()) && (flag || flag2)) {
            AxisAlignedBB axisalignedbb1 = flag3 ? axisalignedbb.move(0.0D, vec3d1.y, 0.0D) : axisalignedbb;
            AxisAlignedBB axisalignedbb2 = axisalignedbb1.expandTowards(vec3d.x, (double) this.maxUpStep(), vec3d.z);

            if (!flag3) {
                axisalignedbb2 = axisalignedbb2.expandTowards(0.0D, (double) -1.0E-5F, 0.0D);
            }

            List<VoxelShape> list1 = collectColliders(this, this.level, list, axisalignedbb2);
            float f = (float) vec3d1.y;
            float[] afloat = collectCandidateStepUpHeights(axisalignedbb1, list1, this.maxUpStep(), f);

            for (float f1 : afloat) {
                Vec3D vec3d2 = collideWithShapes(new Vec3D(vec3d.x, (double) f1, vec3d.z), axisalignedbb1, list1);

                if (vec3d2.horizontalDistanceSqr() > vec3d1.horizontalDistanceSqr()) {
                    double d0 = axisalignedbb.minY - axisalignedbb1.minY;

                    return vec3d2.subtract(0.0D, d0, 0.0D);
                }
            }
        }

        return vec3d1;
    }

    private static float[] collectCandidateStepUpHeights(AxisAlignedBB axisalignedbb, List<VoxelShape> list, float f, float f1) {
        FloatSet floatset = new FloatArraySet(4);

        for (VoxelShape voxelshape : list) {
            DoubleList doublelist = voxelshape.getCoords(EnumDirection.EnumAxis.Y);
            DoubleListIterator doublelistiterator = doublelist.iterator();

            while (doublelistiterator.hasNext()) {
                double d0 = (Double) doublelistiterator.next();
                float f2 = (float) (d0 - axisalignedbb.minY);

                if (f2 >= 0.0F && f2 != f1) {
                    if (f2 > f) {
                        break;
                    }

                    floatset.add(f2);
                }
            }
        }

        float[] afloat = floatset.toFloatArray();

        FloatArrays.unstableSort(afloat);
        return afloat;
    }

    public static Vec3D collideBoundingBox(@Nullable Entity entity, Vec3D vec3d, AxisAlignedBB axisalignedbb, World world, List<VoxelShape> list) {
        List<VoxelShape> list1 = collectColliders(entity, world, list, axisalignedbb.expandTowards(vec3d));

        return collideWithShapes(vec3d, axisalignedbb, list1);
    }

    private static List<VoxelShape> collectColliders(@Nullable Entity entity, World world, List<VoxelShape> list, AxisAlignedBB axisalignedbb) {
        ImmutableList.Builder<VoxelShape> immutablelist_builder = ImmutableList.builderWithExpectedSize(list.size() + 1);

        if (!list.isEmpty()) {
            immutablelist_builder.addAll(list);
        }

        WorldBorder worldborder = world.getWorldBorder();
        boolean flag = entity != null && worldborder.isInsideCloseToBorder(entity, axisalignedbb);

        if (flag) {
            immutablelist_builder.add(worldborder.getCollisionShape());
        }

        immutablelist_builder.addAll(world.getBlockCollisions(entity, axisalignedbb));
        return immutablelist_builder.build();
    }

    private static Vec3D collideWithShapes(Vec3D vec3d, AxisAlignedBB axisalignedbb, List<VoxelShape> list) {
        if (list.isEmpty()) {
            return vec3d;
        } else {
            Vec3D vec3d1 = Vec3D.ZERO;

            for (EnumDirection.EnumAxis enumdirection_enumaxis : axisStepOrder(vec3d)) {
                double d0 = vec3d.get(enumdirection_enumaxis);

                if (d0 != 0.0D) {
                    double d1 = VoxelShapes.collide(enumdirection_enumaxis, axisalignedbb.move(vec3d1), list, d0);

                    vec3d1 = vec3d1.with(enumdirection_enumaxis, d1);
                }
            }

            return vec3d1;
        }
    }

    private static Iterable<EnumDirection.EnumAxis> axisStepOrder(Vec3D vec3d) {
        return Math.abs(vec3d.x) < Math.abs(vec3d.z) ? Entity.YZX_AXIS_ORDER : Entity.YXZ_AXIS_ORDER;
    }

    protected float nextStep() {
        return (float) ((int) this.moveDist + 1);
    }

    protected SoundEffect getSwimSound() {
        return SoundEffects.GENERIC_SWIM;
    }

    protected SoundEffect getSwimSplashSound() {
        return SoundEffects.GENERIC_SPLASH;
    }

    protected SoundEffect getSwimHighSpeedSplashSound() {
        return SoundEffects.GENERIC_SPLASH;
    }

    // CraftBukkit start - Add delegate methods
    public SoundEffect getSwimSound0() {
        return getSwimSound();
    }

    public SoundEffect getSwimSplashSound0() {
        return getSwimSplashSound();
    }

    public SoundEffect getSwimHighSpeedSplashSound0() {
        return getSwimHighSpeedSplashSound();
    }
    // CraftBukkit end

    private void checkInsideBlocks(List<Entity.b> list, InsideBlockEffectApplier.a insideblockeffectapplier_a) {
        if (this.isAffectedByBlocks()) {
            LongSet longset = this.visitedBlocks;

            for (Entity.b entity_b : list) {
                Vec3D vec3d = entity_b.from();
                Vec3D vec3d1 = entity_b.to();
                AxisAlignedBB axisalignedbb = this.makeBoundingBox(vec3d1).deflate((double) 1.0E-5F);

                IBlockAccess.forEachBlockIntersectedBetween(vec3d, vec3d1, axisalignedbb, (blockposition, i) -> {
                    if (this.isAlive()) {
                        IBlockData iblockdata = this.level().getBlockState(blockposition);

                        if (!iblockdata.isAir()) {
                            if (longset.add(blockposition.asLong())) {
                                VoxelShape voxelshape = iblockdata.getEntityInsideCollisionShape(this.level(), blockposition, this);
                                boolean flag = voxelshape == VoxelShapes.block() || this.collidedWithShapeMovingFrom(vec3d, vec3d1, voxelshape.move(new Vec3D(blockposition)).toAabbs());

                                if (flag) {
                                    try {
                                        insideblockeffectapplier_a.advanceStep(i);
                                        iblockdata.entityInside(this.level(), blockposition, this, insideblockeffectapplier_a);
                                        this.onInsideBlock(iblockdata);
                                    } catch (Throwable throwable) {
                                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                                        CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Block being collided with");

                                        CrashReportSystemDetails.populateBlockDetails(crashreportsystemdetails, this.level(), blockposition, iblockdata);
                                        CrashReportSystemDetails crashreportsystemdetails1 = crashreport.addCategory("Entity being checked for collision");

                                        this.fillCrashReportCategory(crashreportsystemdetails1);
                                        throw new ReportedException(crashreport);
                                    }
                                }

                                boolean flag1 = this.collidedWithFluid(iblockdata.getFluidState(), blockposition, vec3d, vec3d1);

                                if (flag1) {
                                    insideblockeffectapplier_a.advanceStep(i);
                                    iblockdata.getFluidState().entityInside(this.level(), blockposition, this, insideblockeffectapplier_a);
                                }

                            }
                        }
                    }
                });
            }

            longset.clear();
        }
    }

    private boolean collidedWithFluid(Fluid fluid, BlockPosition blockposition, Vec3D vec3d, Vec3D vec3d1) {
        AxisAlignedBB axisalignedbb = fluid.getAABB(this.level(), blockposition);

        return axisalignedbb != null && this.collidedWithShapeMovingFrom(vec3d, vec3d1, List.of(axisalignedbb));
    }

    private boolean collidedWithShapeMovingFrom(Vec3D vec3d, Vec3D vec3d1, List<AxisAlignedBB> list) {
        AxisAlignedBB axisalignedbb = this.makeBoundingBox(vec3d);
        Vec3D vec3d2 = vec3d1.subtract(vec3d);

        return axisalignedbb.collidedAlongVector(vec3d2, list);
    }

    protected void onInsideBlock(IBlockData iblockdata) {}

    public BlockPosition adjustSpawnLocation(WorldServer worldserver, BlockPosition blockposition) {
        BlockPosition blockposition1 = worldserver.getSharedSpawnPos();
        Vec3D vec3d = blockposition1.getCenter();
        int i = worldserver.getChunkAt(blockposition1).getHeight(HeightMap.Type.MOTION_BLOCKING_NO_LEAVES, blockposition1.getX(), blockposition1.getZ()) + 1;

        return BlockPosition.containing(vec3d.x, (double) i, vec3d.z);
    }

    public void gameEvent(Holder<GameEvent> holder, @Nullable Entity entity) {
        this.level().gameEvent(entity, holder, this.position);
    }

    public void gameEvent(Holder<GameEvent> holder) {
        this.gameEvent(holder, this);
    }

    private void walkingStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playStepSound(blockposition, iblockdata);
        if (this.shouldPlayAmethystStepSound(iblockdata)) {
            this.playAmethystStepSound();
        }

    }

    protected void waterSwimSound() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.35F : 0.4F;
        Vec3D vec3d = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3d.x * vec3d.x * (double) 0.2F + vec3d.y * vec3d.y + vec3d.z * vec3d.z * (double) 0.2F) * f);

        this.playSwimSound(f1);
    }

    protected BlockPosition getPrimaryStepSoundBlockPos(BlockPosition blockposition) {
        BlockPosition blockposition1 = blockposition.above();
        IBlockData iblockdata = this.level().getBlockState(blockposition1);

        return !iblockdata.is(TagsBlock.INSIDE_STEP_SOUND_BLOCKS) && !iblockdata.is(TagsBlock.COMBINATION_STEP_SOUND_BLOCKS) ? blockposition : blockposition1;
    }

    protected void playCombinationStepSounds(IBlockData iblockdata, IBlockData iblockdata1) {
        SoundEffectType soundeffecttype = iblockdata.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
        this.playMuffledStepSound(iblockdata1);
    }

    protected void playMuffledStepSound(IBlockData iblockdata) {
        SoundEffectType soundeffecttype = iblockdata.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.05F, soundeffecttype.getPitch() * 0.8F);
    }

    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        SoundEffectType soundeffecttype = iblockdata.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
    }

    private boolean shouldPlayAmethystStepSound(IBlockData iblockdata) {
        return iblockdata.is(TagsBlock.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
    }

    private void playAmethystStepSound() {
        this.crystalSoundIntensity *= (float) Math.pow(0.997D, (double) (this.tickCount - this.lastCrystalSoundPlayTick));
        this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
        float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
        float f1 = 0.1F + this.crystalSoundIntensity * 1.2F;

        this.playSound(SoundEffects.AMETHYST_BLOCK_CHIME, f1, f);
        this.lastCrystalSoundPlayTick = this.tickCount;
    }

    protected void playSwimSound(float f) {
        this.playSound(this.getSwimSound(), f, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {}

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEffect soundeffect, float f, float f1) {
        if (!this.isSilent()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), f, f1);
        }

    }

    public void playSound(SoundEffect soundeffect) {
        if (!this.isSilent()) {
            this.playSound(soundeffect, 1.0F, 1.0F);
        }

    }

    public boolean isSilent() {
        return (Boolean) this.entityData.get(Entity.DATA_SILENT);
    }

    public void setSilent(boolean flag) {
        this.entityData.set(Entity.DATA_SILENT, flag);
    }

    public boolean isNoGravity() {
        return (Boolean) this.entityData.get(Entity.DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean flag) {
        this.entityData.set(Entity.DATA_NO_GRAVITY, flag);
    }

    protected double getDefaultGravity() {
        return 0.0D;
    }

    public final double getGravity() {
        return this.isNoGravity() ? 0.0D : this.getDefaultGravity();
    }

    protected void applyGravity() {
        double d0 = this.getGravity();

        if (d0 != 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d0, 0.0D));
        }

    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    public final void doCheckFallDamage(double d0, double d1, double d2, boolean flag) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(flag, new Vec3D(d0, d1, d2));
            BlockPosition blockposition = this.getOnPosLegacy();
            IBlockData iblockdata = this.level().getBlockState(blockposition);

            this.checkFallDamage(d1, flag, iblockdata, blockposition);
        }
    }

    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {
        if (!this.isInWater() && d0 < 0.0D) {
            this.fallDistance -= (double) ((float) d0);
        }

        if (flag) {
            if (this.fallDistance > 0.0D) {
                iblockdata.getBlock().fallOn(this.level(), iblockdata, blockposition, this, this.fallDistance);
                this.level().gameEvent(GameEvent.HIT_GROUND, this.position, GameEvent.a.of(this, (IBlockData) this.mainSupportingBlockPos.map((blockposition1) -> {
                    return this.level().getBlockState(blockposition1);
                }).orElse(iblockdata)));
            }

            this.resetFallDistance();
        }

    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        if (this.type.is(TagsEntity.FALL_DAMAGE_IMMUNE)) {
            return false;
        } else {
            this.propagateFallToPassengers(d0, f, damagesource);
            return false;
        }
    }

    protected void propagateFallToPassengers(double d0, float f, DamageSource damagesource) {
        if (this.isVehicle()) {
            for (Entity entity : this.getPassengers()) {
                entity.causeFallDamage(d0, f, damagesource);
            }
        }

    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    boolean isInRain() {
        BlockPosition blockposition = this.blockPosition();

        return this.level().isRainingAt(blockposition) || this.level().isRainingAt(BlockPosition.containing((double) blockposition.getX(), this.getBoundingBox().maxY, (double) blockposition.getZ()));
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInLiquid() {
        return this.isInWater() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(TagsFluid.WATER));
        }

    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d0 = this.level().dimensionType().ultraWarm() ? 0.007D : 0.0023333333333333335D;
        boolean flag = this.updateFluidHeightAndDoFluidPushing(TagsFluid.LAVA, d0);

        return this.isInWater() || flag;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        Entity entity = this.getVehicle();

        if (entity instanceof AbstractBoat abstractboat) {
            if (!abstractboat.isUnderWater()) {
                this.wasTouchingWater = false;
                return;
            }
        }

        if (this.updateFluidHeightAndDoFluidPushing(TagsFluid.WATER, 0.014D)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
        } else {
            this.wasTouchingWater = false;
        }

    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(TagsFluid.WATER);
        this.fluidOnEyes.clear();
        double d0 = this.getEyeY();
        Entity entity = this.getVehicle();

        if (entity instanceof AbstractBoat abstractboat) {
            if (!abstractboat.isUnderWater() && abstractboat.getBoundingBox().maxY >= d0 && abstractboat.getBoundingBox().minY <= d0) {
                return;
            }
        }

        BlockPosition blockposition = BlockPosition.containing(this.getX(), d0, this.getZ());
        Fluid fluid = this.level().getFluidState(blockposition);
        double d1 = (double) ((float) blockposition.getY() + fluid.getHeight(this.level(), blockposition));

        if (d1 > d0) {
            Stream stream = fluid.getTags();
            Set set = this.fluidOnEyes;

            Objects.requireNonNull(this.fluidOnEyes);
            stream.forEach(set::add);
        }

    }

    protected void doWaterSplashEffect() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.2F : 0.9F;
        Vec3D vec3d = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3d.x * vec3d.x * (double) 0.2F + vec3d.y * vec3d.y + vec3d.z * vec3d.z * (double) 0.2F) * f);

        if (f1 < 0.25F) {
            this.playSound(this.getSwimSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f2 = (float) MathHelper.floor(this.getY());

        for (int i = 0; (float) i < 1.0F + this.dimensions.width() * 20.0F; ++i) {
            double d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            double d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();

            this.level().addParticle(Particles.BUBBLE, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y - this.random.nextDouble() * (double) 0.2F, vec3d.z);
        }

        for (int j = 0; (float) j < 1.0F + this.dimensions.width() * 20.0F; ++j) {
            double d2 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            double d3 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();

            this.level().addParticle(Particles.SPLASH, this.getX() + d2, (double) (f2 + 1.0F), this.getZ() + d3, vec3d.x, vec3d.y, vec3d.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    /** @deprecated */
    @Deprecated
    protected IBlockData getBlockStateOnLegacy() {
        return this.level().getBlockState(this.getOnPosLegacy());
    }

    public IBlockData getBlockStateOn() {
        return this.level().getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        BlockPosition blockposition = this.getOnPosLegacy();
        IBlockData iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.getRenderShape() != EnumRenderType.INVISIBLE) {
            Vec3D vec3d = this.getDeltaMovement();
            BlockPosition blockposition1 = this.blockPosition();
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width();
            double d1 = this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width();

            if (blockposition1.getX() != blockposition.getX()) {
                d0 = MathHelper.clamp(d0, (double) blockposition.getX(), (double) blockposition.getX() + 1.0D);
            }

            if (blockposition1.getZ() != blockposition.getZ()) {
                d1 = MathHelper.clamp(d1, (double) blockposition.getZ(), (double) blockposition.getZ() + 1.0D);
            }

            this.level().addParticle(new ParticleParamBlock(Particles.BLOCK, iblockdata), d0, this.getY() + 0.1D, d1, vec3d.x * -4.0D, 1.5D, vec3d.z * -4.0D);
        }

    }

    public boolean isEyeInFluid(TagKey<FluidType> tagkey) {
        return this.fluidOnEyes.contains(tagkey);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(TagsFluid.LAVA) > 0.0D;
    }

    public void moveRelative(float f, Vec3D vec3d) {
        Vec3D vec3d1 = getInputVector(vec3d, f, this.getYRot());

        this.setDeltaMovement(this.getDeltaMovement().add(vec3d1));
    }

    protected static Vec3D getInputVector(Vec3D vec3d, float f, float f1) {
        double d0 = vec3d.lengthSqr();

        if (d0 < 1.0E-7D) {
            return Vec3D.ZERO;
        } else {
            Vec3D vec3d1 = (d0 > 1.0D ? vec3d.normalize() : vec3d).scale((double) f);
            float f2 = MathHelper.sin(f1 * ((float) Math.PI / 180F));
            float f3 = MathHelper.cos(f1 * ((float) Math.PI / 180F));

            return new Vec3D(vec3d1.x * (double) f3 - vec3d1.z * (double) f2, vec3d1.y, vec3d1.z * (double) f3 + vec3d1.x * (double) f2);
        }
    }

    /** @deprecated */
    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ()) ? this.level().getLightLevelDependentMagicValue(BlockPosition.containing(this.getX(), this.getEyeY(), this.getZ())) : 0.0F;
    }

    public void absSnapTo(double d0, double d1, double d2, float f, float f1) {
        this.absSnapTo(d0, d1, d2);
        this.absSnapRotationTo(f, f1);
    }

    public void absSnapRotationTo(float f, float f1) {
        this.setYRot(f % 360.0F);
        this.setXRot(MathHelper.clamp(f1, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void absSnapTo(double d0, double d1, double d2) {
        double d3 = MathHelper.clamp(d0, -3.0E7D, 3.0E7D);
        double d4 = MathHelper.clamp(d2, -3.0E7D, 3.0E7D);

        this.xo = d3;
        this.yo = d1;
        this.zo = d4;
        this.setPos(d3, d1, d4);
        if (valid) level.getChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4); // CraftBukkit
    }

    public void snapTo(Vec3D vec3d) {
        this.snapTo(vec3d.x, vec3d.y, vec3d.z);
    }

    public void snapTo(double d0, double d1, double d2) {
        this.snapTo(d0, d1, d2, this.getYRot(), this.getXRot());
    }

    public void snapTo(BlockPosition blockposition, float f, float f1) {
        this.snapTo(blockposition.getBottomCenter(), f, f1);
    }

    public void snapTo(Vec3D vec3d, float f, float f1) {
        this.snapTo(vec3d.x, vec3d.y, vec3d.z, f, f1);
    }

    public void snapTo(double d0, double d1, double d2, float f, float f1) {
        this.setPosRaw(d0, d1, d2);
        this.setYRot(f);
        this.setXRot(f1);
        this.setOldPosAndRot();
        this.reapplyPosition();
    }

    public final void setOldPosAndRot() {
        this.setOldPos();
        this.setOldRot();
    }

    public final void setOldPosAndRot(Vec3D vec3d, float f, float f1) {
        this.setOldPos(vec3d);
        this.setOldRot(f, f1);
    }

    protected void setOldPos() {
        this.setOldPos(this.position);
    }

    public void setOldRot() {
        this.setOldRot(this.getYRot(), this.getXRot());
    }

    private void setOldPos(Vec3D vec3d) {
        this.xo = this.xOld = vec3d.x;
        this.yo = this.yOld = vec3d.y;
        this.zo = this.zOld = vec3d.z;
    }

    private void setOldRot(float f, float f1) {
        this.yRotO = f;
        this.xRotO = f1;
    }

    public final Vec3D oldPosition() {
        return new Vec3D(this.xOld, this.yOld, this.zOld);
    }

    public float distanceTo(Entity entity) {
        float f = (float) (this.getX() - entity.getX());
        float f1 = (float) (this.getY() - entity.getY());
        float f2 = (float) (this.getZ() - entity.getZ());

        return MathHelper.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double distanceToSqr(double d0, double d1, double d2) {
        double d3 = this.getX() - d0;
        double d4 = this.getY() - d1;
        double d5 = this.getZ() - d2;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3D vec3d) {
        double d0 = this.getX() - vec3d.x;
        double d1 = this.getY() - vec3d.y;
        double d2 = this.getZ() - vec3d.z;

        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public void playerTouch(EntityHuman entityhuman) {}

    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                double d0 = entity.getX() - this.getX();
                double d1 = entity.getZ() - this.getZ();
                double d2 = MathHelper.absMax(d0, d1);

                if (d2 >= (double) 0.01F) {
                    d2 = Math.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;

                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= (double) 0.05F;
                    d1 *= (double) 0.05F;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d0, 0.0D, -d1);
                    }

                    if (!entity.isVehicle() && entity.isPushable()) {
                        entity.push(d0, 0.0D, d1);
                    }
                }

            }
        }
    }

    public void push(Vec3D vec3d) {
        this.push(vec3d.x, vec3d.y, vec3d.z);
    }

    public void push(double d0, double d1, double d2) {
        this.setDeltaMovement(this.getDeltaMovement().add(d0, d1, d2));
        this.hasImpulse = true;
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    /** @deprecated */
    @Deprecated
    public final void hurt(DamageSource damagesource, float f) {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            this.hurtServer(worldserver, damagesource, f);
        }

    }

    /** @deprecated */
    @Deprecated
    public final boolean hurtOrSimulate(DamageSource damagesource, float f) {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            return this.hurtServer(worldserver, damagesource, f);
        } else {
            return this.hurtClient(damagesource);
        }
    }

    public abstract boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f);

    public boolean hurtClient(DamageSource damagesource) {
        return false;
    }

    public final Vec3D getViewVector(float f) {
        return this.calculateViewVector(this.getViewXRot(f), this.getViewYRot(f));
    }

    public EnumDirection getNearestViewDirection() {
        return EnumDirection.getApproximateNearest(this.getViewVector(1.0F));
    }

    public float getViewXRot(float f) {
        return this.getXRot(f);
    }

    public float getViewYRot(float f) {
        return this.getYRot(f);
    }

    public float getXRot(float f) {
        return f == 1.0F ? this.getXRot() : MathHelper.lerp(f, this.xRotO, this.getXRot());
    }

    public float getYRot(float f) {
        return f == 1.0F ? this.getYRot() : MathHelper.rotLerp(f, this.yRotO, this.getYRot());
    }

    public final Vec3D calculateViewVector(float f, float f1) {
        float f2 = f * ((float) Math.PI / 180F);
        float f3 = -f1 * ((float) Math.PI / 180F);
        float f4 = MathHelper.cos(f3);
        float f5 = MathHelper.sin(f3);
        float f6 = MathHelper.cos(f2);
        float f7 = MathHelper.sin(f2);

        return new Vec3D((double) (f5 * f6), (double) (-f7), (double) (f4 * f6));
    }

    public final Vec3D getUpVector(float f) {
        return this.calculateUpVector(this.getViewXRot(f), this.getViewYRot(f));
    }

    protected final Vec3D calculateUpVector(float f, float f1) {
        return this.calculateViewVector(f - 90.0F, f1);
    }

    public final Vec3D getEyePosition() {
        return new Vec3D(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3D getEyePosition(float f) {
        double d0 = MathHelper.lerp((double) f, this.xo, this.getX());
        double d1 = MathHelper.lerp((double) f, this.yo, this.getY()) + (double) this.getEyeHeight();
        double d2 = MathHelper.lerp((double) f, this.zo, this.getZ());

        return new Vec3D(d0, d1, d2);
    }

    public Vec3D getLightProbePosition(float f) {
        return this.getEyePosition(f);
    }

    public final Vec3D getPosition(float f) {
        double d0 = MathHelper.lerp((double) f, this.xo, this.getX());
        double d1 = MathHelper.lerp((double) f, this.yo, this.getY());
        double d2 = MathHelper.lerp((double) f, this.zo, this.getZ());

        return new Vec3D(d0, d1, d2);
    }

    public MovingObjectPosition pick(double d0, float f, boolean flag) {
        Vec3D vec3d = this.getEyePosition(f);
        Vec3D vec3d1 = this.getViewVector(f);
        Vec3D vec3d2 = vec3d.add(vec3d1.x * d0, vec3d1.y * d0, vec3d1.z * d0);

        return this.level().clip(new RayTrace(vec3d, vec3d2, RayTrace.BlockCollisionOption.OUTLINE, flag ? RayTrace.FluidCollisionOption.ANY : RayTrace.FluidCollisionOption.NONE, this));
    }

    public boolean canBeHitByProjectile() {
        return this.isAlive() && this.isPickable();
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    // CraftBukkit start - collidable API
    public boolean canCollideWithBukkit(Entity entity) {
        return isPushable();
    }
    // CraftBukkit end

    public void awardKillScore(Entity entity, DamageSource damagesource) {
        if (entity instanceof EntityPlayer) {
            CriterionTriggers.ENTITY_KILLED_PLAYER.trigger((EntityPlayer) entity, this, damagesource);
        }

    }

    public boolean shouldRender(double d0, double d1, double d2) {
        double d3 = this.getX() - d0;
        double d4 = this.getY() - d1;
        double d5 = this.getZ() - d2;
        double d6 = d3 * d3 + d4 * d4 + d5 * d5;

        return this.shouldRenderAtSqrDistance(d6);
    }

    public boolean shouldRenderAtSqrDistance(double d0) {
        double d1 = this.getBoundingBox().getSize();

        if (Double.isNaN(d1)) {
            d1 = 1.0D;
        }

        d1 *= 64.0D * Entity.viewScale;
        return d0 < d1 * d1;
    }

    public boolean saveAsPassenger(NBTTagCompound nbttagcompound) {
        // CraftBukkit start - allow excluding certain data when saving
        return saveAsPassenger(nbttagcompound, true);
    }

    public boolean saveAsPassenger(NBTTagCompound nbttagcompound, boolean includeAll) {
        // CraftBukkit end
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else {
            String s = this.getEncodeId();

            if (!this.persist || s == null) { // CraftBukkit - persist flag
                return false;
            } else {
                nbttagcompound.putString("id", s);
                this.saveWithoutId(nbttagcompound, includeAll); // CraftBukkit - pass on includeAll
                return true;
            }
        }
    }

    public boolean save(NBTTagCompound nbttagcompound) {
        return this.isPassenger() ? false : this.saveAsPassenger(nbttagcompound);
    }

    public NBTTagCompound saveWithoutId(NBTTagCompound nbttagcompound) {
        // CraftBukkit start - allow excluding certain data when saving
        return saveWithoutId(nbttagcompound, true);
    }

    public NBTTagCompound saveWithoutId(NBTTagCompound nbttagcompound, boolean includeAll) {
        // CraftBukkit end
        try {
            // CraftBukkit start - selectively save position
            if (includeAll) {
                if (this.vehicle != null) {
                    nbttagcompound.store("Pos", Vec3D.CODEC, new Vec3D(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
                } else {
                    nbttagcompound.store("Pos", Vec3D.CODEC, this.position());
                }
            }
            // CraftBukkit end

            nbttagcompound.store("Motion", Vec3D.CODEC, this.getDeltaMovement());
            // CraftBukkit start - Checking for NaN pitch/yaw and resetting to zero
            // TODO: make sure this is the best way to address this.
            if (Float.isNaN(this.yRot)) {
                this.yRot = 0;
            }

            if (Float.isNaN(this.xRot)) {
                this.xRot = 0;
            }
            // CraftBukkit end
            nbttagcompound.store("Rotation", Vec2F.CODEC, new Vec2F(this.getYRot(), this.getXRot()));
            nbttagcompound.putDouble("fall_distance", this.fallDistance);
            nbttagcompound.putShort("Fire", (short) this.remainingFireTicks);
            nbttagcompound.putShort("Air", (short) this.getAirSupply());
            nbttagcompound.putBoolean("OnGround", this.onGround());
            nbttagcompound.putBoolean("Invulnerable", this.invulnerable);
            nbttagcompound.putInt("PortalCooldown", this.portalCooldown);
            // CraftBukkit start - selectively save uuid and world
            if (includeAll) {
                nbttagcompound.store("UUID", UUIDUtil.CODEC, this.getUUID());
                // PAIL: Check above UUID reads 1.8 properly, ie: UUIDMost / UUIDLeast
                nbttagcompound.putLong("WorldUUIDLeast", ((WorldServer) this.level).getWorld().getUID().getLeastSignificantBits());
                nbttagcompound.putLong("WorldUUIDMost", ((WorldServer) this.level).getWorld().getUID().getMostSignificantBits());
            }
            nbttagcompound.putInt("Bukkit.updateLevel", CURRENT_LEVEL);
            if (!this.persist) {
                nbttagcompound.putBoolean("Bukkit.persist", this.persist);
            }
            if (!this.visibleByDefault) {
                nbttagcompound.putBoolean("Bukkit.visibleByDefault", this.visibleByDefault);
            }
            if (this.persistentInvisibility) {
                nbttagcompound.putBoolean("Bukkit.invisible", this.persistentInvisibility);
            }
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (maxAirTicks != getDefaultMaxAirSupply()) {
                nbttagcompound.putInt("Bukkit.MaxAirSupply", getMaxAirSupply());
            }
            // CraftBukkit end
            IChatBaseComponent ichatbasecomponent = this.getCustomName();

            if (ichatbasecomponent != null) {
                RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

                nbttagcompound.store("CustomName", ComponentSerialization.CODEC, registryops, ichatbasecomponent);
            }

            if (this.isCustomNameVisible()) {
                nbttagcompound.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                nbttagcompound.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                nbttagcompound.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                nbttagcompound.putBoolean("Glowing", true);
            }

            int i = this.getTicksFrozen();

            if (i > 0) {
                nbttagcompound.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                nbttagcompound.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            if (!this.tags.isEmpty()) {
                nbttagcompound.store("Tags", Entity.TAG_LIST_CODEC, List.copyOf(this.tags));
            }

            if (!this.customData.isEmpty()) {
                nbttagcompound.store("data", CustomData.CODEC, this.customData);
            }

            this.addAdditionalSaveData(nbttagcompound, includeAll); // CraftBukkit - pass on includeAll
            if (this.isVehicle()) {
                NBTTagList nbttaglist = new NBTTagList();

                for (Entity entity : this.getPassengers()) {
                    NBTTagCompound nbttagcompound1 = new NBTTagCompound();

                    if (entity.saveAsPassenger(nbttagcompound1, includeAll)) { // CraftBukkit - pass on includeAll
                        nbttaglist.add(nbttagcompound1);
                    }
                }

                if (!nbttaglist.isEmpty()) {
                    nbttagcompound.put("Passengers", nbttaglist);
                }
            }

            // CraftBukkit start - stores eventually existing bukkit values
            if (this.bukkitEntity != null) {
                this.bukkitEntity.storeBukkitValues(nbttagcompound);
            }
            // CraftBukkit end
            return nbttagcompound;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Entity being saved");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    public void load(NBTTagCompound nbttagcompound) {
        try {
            Vec3D vec3d = (Vec3D) nbttagcompound.read("Pos", Vec3D.CODEC).orElse(Vec3D.ZERO);
            Vec3D vec3d1 = (Vec3D) nbttagcompound.read("Motion", Vec3D.CODEC).orElse(Vec3D.ZERO);
            Vec2F vec2f = (Vec2F) nbttagcompound.read("Rotation", Vec2F.CODEC).orElse(Vec2F.ZERO);

            this.setDeltaMovement(Math.abs(vec3d1.x) > 10.0D ? 0.0D : vec3d1.x, Math.abs(vec3d1.y) > 10.0D ? 0.0D : vec3d1.y, Math.abs(vec3d1.z) > 10.0D ? 0.0D : vec3d1.z);
            this.hasImpulse = true;
            double d0 = 3.0000512E7D;

            this.setPosRaw(MathHelper.clamp(vec3d.x, -3.0000512E7D, 3.0000512E7D), MathHelper.clamp(vec3d.y, -2.0E7D, 2.0E7D), MathHelper.clamp(vec3d.z, -3.0000512E7D, 3.0000512E7D));
            this.setYRot(vec2f.x);
            this.setXRot(vec2f.y);
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = nbttagcompound.getDoubleOr("fall_distance", 0.0D);
            this.remainingFireTicks = nbttagcompound.getShortOr("Fire", (short) 0);
            this.setAirSupply(nbttagcompound.getIntOr("Air", this.getMaxAirSupply()));
            this.onGround = nbttagcompound.getBooleanOr("OnGround", false);
            this.invulnerable = nbttagcompound.getBooleanOr("Invulnerable", false);
            this.portalCooldown = nbttagcompound.getIntOr("PortalCooldown", 0);
            nbttagcompound.read("UUID", UUIDUtil.CODEC).ifPresent((uuid) -> {
                this.uuid = uuid;
                this.stringUUID = this.uuid.toString();
            });
            if (Double.isFinite(this.getX()) && Double.isFinite(this.getY()) && Double.isFinite(this.getZ())) {
                if (Double.isFinite((double) this.getYRot()) && Double.isFinite((double) this.getXRot())) {
                    this.reapplyPosition();
                    this.setRot(this.getYRot(), this.getXRot());
                    RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

                    this.setCustomName((IChatBaseComponent) nbttagcompound.read("CustomName", ComponentSerialization.CODEC, registryops).orElse(null)); // CraftBukkit - decompile error
                    this.setCustomNameVisible(nbttagcompound.getBooleanOr("CustomNameVisible", false));
                    this.setSilent(nbttagcompound.getBooleanOr("Silent", false));
                    this.setNoGravity(nbttagcompound.getBooleanOr("NoGravity", false));
                    this.setGlowingTag(nbttagcompound.getBooleanOr("Glowing", false));
                    this.setTicksFrozen(nbttagcompound.getIntOr("TicksFrozen", 0));
                    this.hasVisualFire = nbttagcompound.getBooleanOr("HasVisualFire", false);
                    this.customData = (CustomData) nbttagcompound.read("data", CustomData.CODEC).orElse(CustomData.EMPTY);
                    this.tags.clear();
                    Optional<List<String>> optional = nbttagcompound.read("Tags", Entity.TAG_LIST_CODEC); // CraftBukkit - deocmpile error
                    Set set = this.tags;

                    Objects.requireNonNull(this.tags);
                    optional.ifPresent(set::addAll);
                    this.readAdditionalSaveData(nbttagcompound);
                    if (this.repositionEntityAfterLoad()) {
                        this.reapplyPosition();
                    }

                } else {
                    throw new IllegalStateException("Entity has invalid rotation");
                }
            } else {
                throw new IllegalStateException("Entity has invalid position");
            }

            // CraftBukkit start
            this.persist = nbttagcompound.getBooleanOr("Bukkit.persist", this.persist);
            this.visibleByDefault = nbttagcompound.getBooleanOr("Bukkit.visibleByDefault", this.visibleByDefault);
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            this.maxAirTicks = nbttagcompound.getIntOr("Bukkit.MaxAirSupply", this.maxAirTicks);
            // CraftBukkit end

            // CraftBukkit start - Reset world
            if (this instanceof EntityPlayer) {
                Server server = Bukkit.getServer();
                org.bukkit.World bworld = null;

                // TODO: Remove World related checks, replaced with WorldUID
                String worldName = nbttagcompound.getStringOr("world", "");

                if (nbttagcompound.contains("WorldUUIDMost") && nbttagcompound.contains("WorldUUIDLeast")) {
                    UUID uid = new UUID(nbttagcompound.getLongOr("WorldUUIDMost", -1), nbttagcompound.getLongOr("WorldUUIDLeast", -1));
                    bworld = server.getWorld(uid);
                } else {
                    bworld = server.getWorld(worldName);
                }

                if (bworld == null) {
                    bworld = ((org.bukkit.craftbukkit.CraftServer) server).getServer().getLevel(World.OVERWORLD).getWorld();
                }

                ((EntityPlayer) this).setLevel(bworld == null ? null : ((CraftWorld) bworld).getHandle());
            }
            this.getBukkitEntity().readBukkitValues(nbttagcompound);
            if (nbttagcompound.contains("Bukkit.invisible")) {
                boolean bukkitInvisible = nbttagcompound.getBooleanOr("Bukkit.invisible", false);
                this.setInvisible(bukkitInvisible);
                this.persistentInvisibility = bukkitInvisible;
            }
            // CraftBukkit end

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Entity being loaded");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    public final String getEncodeId() {
        EntityTypes<?> entitytypes = this.getType();
        MinecraftKey minecraftkey = EntityTypes.getKey(entitytypes);

        return entitytypes.canSerialize() && minecraftkey != null ? minecraftkey.toString() : null;
    }

    // CraftBukkit start - allow excluding certain data when saving
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound, boolean includeAll) {
        addAdditionalSaveData(nbttagcompound);
    }
    // CraftBukkit end

    protected abstract void readAdditionalSaveData(NBTTagCompound nbttagcompound);

    protected abstract void addAdditionalSaveData(NBTTagCompound nbttagcompound);

    @Nullable
    public EntityItem spawnAtLocation(WorldServer worldserver, IMaterial imaterial) {
        return this.spawnAtLocation(worldserver, imaterial, 0);
    }

    @Nullable
    public EntityItem spawnAtLocation(WorldServer worldserver, IMaterial imaterial, int i) {
        return this.spawnAtLocation(worldserver, new ItemStack(imaterial), (float) i);
    }

    @Nullable
    public EntityItem spawnAtLocation(WorldServer worldserver, ItemStack itemstack) {
        return this.spawnAtLocation(worldserver, itemstack, 0.0F);
    }

    @Nullable
    public EntityItem spawnAtLocation(WorldServer worldserver, ItemStack itemstack, float f) {
        if (itemstack.isEmpty()) {
            return null;
        } else {
            // CraftBukkit start - Capture drops for death event
            if (this instanceof EntityLiving && !((EntityLiving) this).forceDrops) {
                ((EntityLiving) this).drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack));
                return null;
            }
            // CraftBukkit end
            EntityItem entityitem = new EntityItem(worldserver, this.getX(), this.getY() + (double) f, this.getZ(), itemstack);

            entityitem.setDefaultPickUpDelay();
            // CraftBukkit start
            EntityDropItemEvent event = new EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }
            // CraftBukkit end
            worldserver.addFreshEntity(entityitem);
            return entityitem;
        }
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        } else {
            float f = this.dimensions.width() * 0.8F;
            AxisAlignedBB axisalignedbb = AxisAlignedBB.ofSize(this.getEyePosition(), (double) f, 1.0E-6D, (double) f);

            return BlockPosition.betweenClosedStream(axisalignedbb).anyMatch((blockposition) -> {
                IBlockData iblockdata = this.level().getBlockState(blockposition);

                return !iblockdata.isAir() && iblockdata.isSuffocating(this.level(), blockposition) && VoxelShapes.joinIsNotEmpty(iblockdata.getCollisionShape(this.level(), blockposition).move((BaseBlockPosition) blockposition), VoxelShapes.create(axisalignedbb), OperatorBoolean.AND);
            });
        }
    }

    public EnumInteractionResult interact(EntityHuman entityhuman, EnumHand enumhand) {
        if (this.isAlive() && this instanceof Leashable leashable) {
            if (leashable.getLeashHolder() == entityhuman) {
                if (!this.level().isClientSide()) {
                    // CraftBukkit start - fire PlayerUnleashEntityEvent
                    if (CraftEventFactory.callPlayerUnleashEntityEvent(this, entityhuman, enumhand).isCancelled()) {
                        ((EntityPlayer) entityhuman).connection.send(new PacketPlayOutAttachEntity(this, leashable.getLeashHolder()));
                        return EnumInteractionResult.PASS;
                    }
                    // CraftBukkit end
                    if (entityhuman.hasInfiniteMaterials()) {
                        leashable.removeLeash();
                    } else {
                        leashable.dropLeash();
                    }

                    this.gameEvent(GameEvent.ENTITY_INTERACT, entityhuman);
                }

                return EnumInteractionResult.SUCCESS.withoutItem();
            }

            ItemStack itemstack = entityhuman.getItemInHand(enumhand);

            if (itemstack.is(Items.LEAD) && leashable.canHaveALeashAttachedToIt()) {
                if (!this.level().isClientSide()) {
                    // CraftBukkit start - fire PlayerLeashEntityEvent
                    if (CraftEventFactory.callPlayerLeashEntityEvent(this, entityhuman, entityhuman, enumhand).isCancelled()) {
                        ((EntityPlayer) entityhuman).resendItemInHands(); // SPIGOT-7615: Resend to fix client desync with used item
                        ((EntityPlayer) entityhuman).connection.send(new PacketPlayOutAttachEntity(this, leashable.getLeashHolder()));
                        return EnumInteractionResult.PASS;
                    }
                    // CraftBukkit end
                    leashable.setLeashedTo(entityhuman, true);
                }

                itemstack.shrink(1);
                return EnumInteractionResult.SUCCESS;
            }
        }

        return EnumInteractionResult.PASS;
    }

    public boolean canCollideWith(Entity entity) {
        return entity.canBeCollidedWith() && !this.isPassengerOfSameVehicle(entity);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3D.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public final void positionRider(Entity entity) {
        if (this.hasPassenger(entity)) {
            this.positionRider(entity, Entity::setPos);
        }
    }

    protected void positionRider(Entity entity, Entity.MoveFunction entity_movefunction) {
        Vec3D vec3d = this.getPassengerRidingPosition(entity);
        Vec3D vec3d1 = entity.getVehicleAttachmentPoint(this);

        entity_movefunction.accept(entity, vec3d.x - vec3d1.x, vec3d.y - vec3d1.y, vec3d.z - vec3d1.z);
    }

    public void onPassengerTurned(Entity entity) {}

    public Vec3D getVehicleAttachmentPoint(Entity entity) {
        return this.getAttachments().get(EntityAttachment.VEHICLE, 0, this.yRot);
    }

    public Vec3D getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.dimensions, 1.0F));
    }

    protected Vec3D getPassengerAttachmentPoint(Entity entity, EntitySize entitysize, float f) {
        return getDefaultPassengerAttachmentPoint(this, entity, entitysize.attachments());
    }

    protected static Vec3D getDefaultPassengerAttachmentPoint(Entity entity, Entity entity1, EntityAttachments entityattachments) {
        int i = entity.getPassengers().indexOf(entity1);

        return entityattachments.getClamped(EntityAttachment.PASSENGER, i, entity.yRot);
    }

    public boolean startRiding(Entity entity) {
        return this.startRiding(entity, false);
    }

    public boolean showVehicleHealth() {
        return this instanceof EntityLiving;
    }

    public boolean startRiding(Entity entity, boolean flag) {
        if (entity == this.vehicle) {
            return false;
        } else if (!entity.couldAcceptPassenger()) {
            return false;
        } else if (!flag && !this.level().isClientSide() && !entity.type.canSerialize()) { // SPIGOT-7947: Allow force riding all entities
            return false;
        } else {
            for (Entity entity1 = entity; entity1.vehicle != null; entity1 = entity1.vehicle) {
                if (entity1.vehicle == this) {
                    return false;
                }
            }

            if (flag || this.canRide(entity) && entity.canAddPassenger(this)) {
                // CraftBukkit start
                if (entity.getBukkitEntity() instanceof Vehicle && this.getBukkitEntity() instanceof LivingEntity) {
                    VehicleEnterEvent event = new VehicleEnterEvent((Vehicle) entity.getBukkitEntity(), this.getBukkitEntity());
                    // Suppress during worldgen
                    if (this.valid) {
                        Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event.isCancelled()) {
                        return false;
                    }
                }

                EntityMountEvent event = new EntityMountEvent(this.getBukkitEntity(), entity.getBukkitEntity());
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                if (event.isCancelled()) {
                    return false;
                }
                // CraftBukkit end
                if (this.isPassenger()) {
                    this.stopRiding();
                }

                this.setPose(EntityPose.STANDING);
                this.vehicle = entity;
                this.vehicle.addPassenger(this);
                entity.getIndirectPassengersStream().filter((entity2) -> {
                    return entity2 instanceof EntityPlayer;
                }).forEach((entity2) -> {
                    CriterionTriggers.START_RIDING_TRIGGER.trigger((EntityPlayer) entity2);
                });
                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean canRide(Entity entity) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; --i) {
            ((Entity) this.passengers.get(i)).stopRiding();
        }

    }

    public void removeVehicle() {
        if (this.vehicle != null) {
            Entity entity = this.vehicle;

            this.vehicle = null;
            if (!entity.removePassenger(this)) this.vehicle = entity; // CraftBukkit
        }

    }

    public void stopRiding() {
        this.removeVehicle();
    }

    protected void addPassenger(Entity entity) {
        if (entity.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(entity);
            } else {
                List<Entity> list = Lists.newArrayList(this.passengers);

                if (!this.level().isClientSide && entity instanceof EntityHuman && !(this.getFirstPassenger() instanceof EntityHuman)) {
                    list.add(0, entity);
                } else {
                    list.add(entity);
                }

                this.passengers = ImmutableList.copyOf(list);
            }

            this.gameEvent(GameEvent.ENTITY_MOUNT, entity);
        }
    }

    protected boolean removePassenger(Entity entity) { // CraftBukkit
        if (entity.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            // CraftBukkit start
            CraftEntity craft = (CraftEntity) entity.getBukkitEntity().getVehicle();
            Entity orig = craft == null ? null : craft.getHandle();
            if (getBukkitEntity() instanceof Vehicle && entity.getBukkitEntity() instanceof LivingEntity) {
                VehicleExitEvent event = new VehicleExitEvent(
                        (Vehicle) getBukkitEntity(),
                        (LivingEntity) entity.getBukkitEntity()
                );
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                CraftEntity craftn = (CraftEntity) entity.getBukkitEntity().getVehicle();
                Entity n = craftn == null ? null : craftn.getHandle();
                if (event.isCancelled() || n != orig) {
                    return false;
                }
            }

            EntityDismountEvent event = new EntityDismountEvent(entity.getBukkitEntity(), this.getBukkitEntity());
            // Suppress during worldgen
            if (this.valid) {
                Bukkit.getPluginManager().callEvent(event);
            }
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end
            if (this.passengers.size() == 1 && this.passengers.get(0) == entity) {
                this.passengers = ImmutableList.of();
            } else {
                this.passengers = (ImmutableList) this.passengers.stream().filter((entity1) -> {
                    return entity1 != entity;
                }).collect(ImmutableList.toImmutableList());
            }

            entity.boardingCooldown = 60;
            this.gameEvent(GameEvent.ENTITY_DISMOUNT, entity);
        }
        return true; // CraftBukkit
    }

    protected boolean canAddPassenger(Entity entity) {
        return this.passengers.isEmpty();
    }

    protected boolean couldAcceptPassenger() {
        return true;
    }

    public final boolean isInterpolating() {
        return this.getInterpolation() != null && this.getInterpolation().hasActiveInterpolation();
    }

    public final void moveOrInterpolateTo(Vec3D vec3d, float f, float f1) {
        InterpolationHandler interpolationhandler = this.getInterpolation();

        if (interpolationhandler != null) {
            interpolationhandler.interpolateTo(vec3d, f, f1);
        } else {
            this.setPos(vec3d);
            this.setRot(f, f1);
        }

    }

    @Nullable
    public InterpolationHandler getInterpolation() {
        return null;
    }

    public void lerpHeadTo(float f, int i) {
        this.setYHeadRot(f);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3D getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3D getHandHoldingItemAngle(Item item) {
        if (!(this instanceof EntityHuman entityhuman)) {
            return Vec3D.ZERO;
        } else {
            boolean flag = entityhuman.getOffhandItem().is(item) && !entityhuman.getMainHandItem().is(item);
            EnumMainHand enummainhand = flag ? entityhuman.getMainArm().getOpposite() : entityhuman.getMainArm();

            return this.calculateViewVector(0.0F, this.getYRot() + (float) (enummainhand == EnumMainHand.RIGHT ? 80 : -80)).scale(0.5D);
        }
    }

    public Vec2F getRotationVector() {
        return new Vec2F(this.getXRot(), this.getYRot());
    }

    public Vec3D getForward() {
        return Vec3D.directionFromRotation(this.getRotationVector());
    }

    public void setAsInsidePortal(Portal portal, BlockPosition blockposition) {
        if (this.isOnPortalCooldown()) {
            this.setPortalCooldown();
        } else {
            if (this.portalProcess != null && this.portalProcess.isSamePortal(portal)) {
                if (!this.portalProcess.isInsidePortalThisTick()) {
                    this.portalProcess.updateEntryPosition(blockposition.immutable());
                    this.portalProcess.setAsInsidePortalThisTick(true);
                }
            } else {
                this.portalProcess = new PortalProcessor(portal, blockposition.immutable());
            }

        }
    }

    protected void handlePortal() {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            this.processPortalCooldown();
            if (this.portalProcess != null) {
                if (this.portalProcess.processPortalTeleportation(worldserver, this, this.canUsePortal(false))) {
                    GameProfilerFiller gameprofilerfiller = Profiler.get();

                    gameprofilerfiller.push("portal");
                    this.setPortalCooldown();
                    TeleportTransition teleporttransition = this.portalProcess.getPortalDestination(worldserver, this);

                    if (teleporttransition != null) {
                        WorldServer worldserver1 = teleporttransition.newLevel();

                        if (this instanceof EntityPlayer || (worldserver1 != null && (worldserver1.dimension() == worldserver.dimension() || this.canTeleport(worldserver, worldserver1)))) { // CraftBukkit - always call event for players
                            this.teleport(teleporttransition);
                        }
                    }

                    gameprofilerfiller.pop();
                } else if (this.portalProcess.hasExpired()) {
                    this.portalProcess = null;
                }

            }
        }
    }

    public int getDimensionChangingDelay() {
        Entity entity = this.getFirstPassenger();

        return entity instanceof EntityPlayer ? entity.getDimensionChangingDelay() : 300;
    }

    public void lerpMotion(double d0, double d1, double d2) {
        this.setDeltaMovement(d0, d1, d2);
    }

    public void handleDamageEvent(DamageSource damagesource) {}

    public void handleEntityEvent(byte b0) {
        switch (b0) {
            case 53:
                BlockHoney.showSlideParticles(this);
            default:
        }
    }

    public void animateHurt(float f) {}

    public boolean isOnFire() {
        boolean flag = this.level() != null && this.level().isClientSide;

        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean dismountsUnderwater() {
        return this.getType().is(TagsEntity.DISMOUNTS_UNDERWATER);
    }

    public boolean canControlVehicle() {
        return !this.getType().is(TagsEntity.NON_CONTROLLING_RIDER);
    }

    public void setShiftKeyDown(boolean flag) {
        this.setSharedFlag(1, flag);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(EntityPose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean flag) {
        this.setSharedFlag(3, flag);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(EntityPose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean flag) {
        // CraftBukkit start
        if (valid && this.isSwimming() != flag && this instanceof EntityLiving) {
            if (CraftEventFactory.callToggleSwimEvent((EntityLiving) this, flag).isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.setSharedFlag(4, flag);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean flag) {
        this.hasGlowingTag = flag;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    public boolean isInvisibleTo(EntityHuman entityhuman) {
        if (entityhuman.isSpectator()) {
            return false;
        } else {
            ScoreboardTeamBase scoreboardteambase = this.getTeam();

            return scoreboardteambase != null && entityhuman != null && entityhuman.getTeam() == scoreboardteambase && scoreboardteambase.canSeeFriendlyInvisibles() ? false : this.isInvisible();
        }
    }

    public boolean isOnRails() {
        return false;
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, WorldServer> biconsumer) {}

    @Nullable
    public ScoreboardTeam getTeam() {
        return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public final boolean isAlliedTo(@Nullable Entity entity) {
        return entity == null ? false : this == entity || this.considersEntityAsAlly(entity) || entity.considersEntityAsAlly(this);
    }

    protected boolean considersEntityAsAlly(Entity entity) {
        return this.isAlliedTo((ScoreboardTeamBase) entity.getTeam());
    }

    public boolean isAlliedTo(@Nullable ScoreboardTeamBase scoreboardteambase) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(scoreboardteambase) : false;
    }

    // CraftBukkit - start
    public void setInvisible(boolean flag) {
        if (!this.persistentInvisibility) { // Prevent Minecraft from removing our invisibility flag
            this.setSharedFlag(5, flag);
        }
        // CraftBukkit - end
    }

    public boolean getSharedFlag(int i) {
        return ((Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) & 1 << i) != 0;
    }

    public void setSharedFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID);

        if (flag) {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 | 1 << i));
        } else {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 & ~(1 << i)));
        }

    }

    public int getMaxAirSupply() {
        return maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    public int getAirSupply() {
        return (Integer) this.entityData.get(Entity.DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int i) {
        // CraftBukkit start
        EntityAirChangeEvent event = new EntityAirChangeEvent(this.getBukkitEntity(), i);
        // Suppress during worldgen
        if (this.valid) {
            event.getEntity().getServer().getPluginManager().callEvent(event);
        }
        if (event.isCancelled() && this.getAirSupply() != i) {
            this.entityData.markDirty(Entity.DATA_AIR_SUPPLY_ID);
            return;
        }
        this.entityData.set(Entity.DATA_AIR_SUPPLY_ID, event.getAmount());
        // CraftBukkit end
    }

    public void clearFreeze() {
        this.setTicksFrozen(0);
    }

    public int getTicksFrozen() {
        return (Integer) this.entityData.get(Entity.DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int i) {
        this.entityData.set(Entity.DATA_TICKS_FROZEN, i);
    }

    public float getPercentFrozen() {
        int i = this.getTicksRequiredToFreeze();

        return (float) Math.min(this.getTicksFrozen(), i) / (float) i;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(WorldServer worldserver, EntityLightning entitylightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        // CraftBukkit start
        final org.bukkit.entity.Entity thisBukkitEntity = this.getBukkitEntity();
        final org.bukkit.entity.Entity stormBukkitEntity = entitylightning.getBukkitEntity();
        final PluginManager pluginManager = Bukkit.getPluginManager();
        // CraftBukkit end

        if (this.remainingFireTicks == 0) {
            // CraftBukkit start - Call a combust event when lightning strikes
            EntityCombustByEntityEvent entityCombustEvent = new EntityCombustByEntityEvent(stormBukkitEntity, thisBukkitEntity, 8.0F);
            pluginManager.callEvent(entityCombustEvent);
            if (!entityCombustEvent.isCancelled()) {
                this.igniteForSeconds(entityCombustEvent.getDuration(), false);
            }
            // CraftBukkit end
        }

        // CraftBukkit start
        if (thisBukkitEntity instanceof Hanging) {
            HangingBreakByEntityEvent hangingEvent = new HangingBreakByEntityEvent((Hanging) thisBukkitEntity, stormBukkitEntity);
            pluginManager.callEvent(hangingEvent);

            if (hangingEvent.isCancelled()) {
                return;
            }
        }

        if (this.fireImmune()) {
            return;
        }

        if (!this.hurtServer(worldserver, this.damageSources().lightningBolt().customEntityDamager(entitylightning), 5.0F)) {
            return;
        }
        // CraftBukkit end
    }

    public void onAboveBubbleColumn(boolean flag, BlockPosition blockposition) {
        handleOnAboveBubbleColumn(this, flag, blockposition);
    }

    protected static void handleOnAboveBubbleColumn(Entity entity, boolean flag, BlockPosition blockposition) {
        Vec3D vec3d = entity.getDeltaMovement();
        double d0;

        if (flag) {
            d0 = Math.max(-0.9D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(1.8D, vec3d.y + 0.1D);
        }

        entity.setDeltaMovement(vec3d.x, d0, vec3d.z);
        sendBubbleColumnParticles(entity.level, blockposition);
    }

    protected static void sendBubbleColumnParticles(World world, BlockPosition blockposition) {
        if (world instanceof WorldServer worldserver) {
            for (int i = 0; i < 2; ++i) {
                worldserver.sendParticles(Particles.SPLASH, (double) blockposition.getX() + world.random.nextDouble(), (double) (blockposition.getY() + 1), (double) blockposition.getZ() + world.random.nextDouble(), 1, 0.0D, 0.0D, 0.0D, 1.0D);
                worldserver.sendParticles(Particles.BUBBLE, (double) blockposition.getX() + world.random.nextDouble(), (double) (blockposition.getY() + 1), (double) blockposition.getZ() + world.random.nextDouble(), 1, 0.0D, 0.01D, 0.0D, 0.2D);
            }
        }

    }

    public void onInsideBubbleColumn(boolean flag) {
        handleOnInsideBubbleColumn(this, flag);
    }

    protected static void handleOnInsideBubbleColumn(Entity entity, boolean flag) {
        Vec3D vec3d = entity.getDeltaMovement();
        double d0;

        if (flag) {
            d0 = Math.max(-0.3D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(0.7D, vec3d.y + 0.06D);
        }

        entity.setDeltaMovement(vec3d.x, d0, vec3d.z);
        entity.resetFallDistance();
    }

    public boolean killedEntity(WorldServer worldserver, EntityLiving entityliving) {
        return true;
    }

    public void checkSlowFallDistance() {
        if (this.getDeltaMovement().y() > -0.5D && this.fallDistance > 1.0D) {
            this.fallDistance = 1.0D;
        }

    }

    public void resetFallDistance() {
        this.fallDistance = 0.0D;
    }

    protected void moveTowardsClosestSpace(double d0, double d1, double d2) {
        BlockPosition blockposition = BlockPosition.containing(d0, d1, d2);
        Vec3D vec3d = new Vec3D(d0 - (double) blockposition.getX(), d1 - (double) blockposition.getY(), d2 - (double) blockposition.getZ());
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
        EnumDirection enumdirection = EnumDirection.UP;
        double d3 = Double.MAX_VALUE;

        for (EnumDirection enumdirection1 : new EnumDirection[]{EnumDirection.NORTH, EnumDirection.SOUTH, EnumDirection.WEST, EnumDirection.EAST, EnumDirection.UP}) {
            blockposition_mutableblockposition.setWithOffset(blockposition, enumdirection1);
            if (!this.level().getBlockState(blockposition_mutableblockposition).isCollisionShapeFullBlock(this.level(), blockposition_mutableblockposition)) {
                double d4 = vec3d.get(enumdirection1.getAxis());
                double d5 = enumdirection1.getAxisDirection() == EnumDirection.EnumAxisDirection.POSITIVE ? 1.0D - d4 : d4;

                if (d5 < d3) {
                    d3 = d5;
                    enumdirection = enumdirection1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = (float) enumdirection.getAxisDirection().getStep();
        Vec3D vec3d1 = this.getDeltaMovement().scale(0.75D);

        if (enumdirection.getAxis() == EnumDirection.EnumAxis.X) {
            this.setDeltaMovement((double) (f1 * f), vec3d1.y, vec3d1.z);
        } else if (enumdirection.getAxis() == EnumDirection.EnumAxis.Y) {
            this.setDeltaMovement(vec3d1.x, (double) (f1 * f), vec3d1.z);
        } else if (enumdirection.getAxis() == EnumDirection.EnumAxis.Z) {
            this.setDeltaMovement(vec3d1.x, vec3d1.y, (double) (f1 * f));
        }

    }

    public void makeStuckInBlock(IBlockData iblockdata, Vec3D vec3d) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = vec3d;
    }

    private static IChatBaseComponent removeAction(IChatBaseComponent ichatbasecomponent) {
        IChatMutableComponent ichatmutablecomponent = ichatbasecomponent.plainCopy().setStyle(ichatbasecomponent.getStyle().withClickEvent((ChatClickable) null));

        for (IChatBaseComponent ichatbasecomponent1 : ichatbasecomponent.getSiblings()) {
            ichatmutablecomponent.append(removeAction(ichatbasecomponent1));
        }

        return ichatmutablecomponent;
    }

    @Override
    public IChatBaseComponent getName() {
        IChatBaseComponent ichatbasecomponent = this.getCustomName();

        return ichatbasecomponent != null ? removeAction(ichatbasecomponent) : this.getTypeName();
    }

    protected IChatBaseComponent getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float f) {}

    public void setYBodyRot(float f) {}

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity entity) {
        return false;
    }

    public String toString() {
        String s = this.level() == null ? "~NULL~" : this.level().toString();

        return this.removalReason != null ? String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f, removed=%s]", this.getClass().getSimpleName(), this.getName().getString(), this.id, s, this.getX(), this.getY(), this.getZ(), this.removalReason) : String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]", this.getClass().getSimpleName(), this.getName().getString(), this.id, s, this.getX(), this.getY(), this.getZ());
    }

    public final boolean isInvulnerableToBase(DamageSource damagesource) {
        return this.isRemoved() || this.invulnerable && !damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !damagesource.isCreativePlayer() || damagesource.is(DamageTypeTags.IS_FIRE) && this.fireImmune() || damagesource.is(DamageTypeTags.IS_FALL) && this.getType().is(TagsEntity.FALL_DAMAGE_IMMUNE);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean flag) {
        this.invulnerable = flag;
    }

    public void copyPosition(Entity entity) {
        this.snapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    public void restoreFrom(Entity entity) {
        NBTTagCompound nbttagcompound = entity.saveWithoutId(new NBTTagCompound());

        nbttagcompound.remove("Dimension");
        this.load(nbttagcompound);
        this.portalCooldown = entity.portalCooldown;
        this.portalProcess = entity.portalProcess;
    }

    @Nullable
    public Entity teleport(TeleportTransition teleporttransition) {
        World worldserver = this.level(); // CraftBukkit - decompile error

        if (worldserver instanceof WorldServer worldserver1) {
            if (!this.isRemoved()) {
                // CraftBukkit start
                PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this), PositionMoveRotation.of(teleporttransition), teleporttransition.relatives());
                Location to = CraftLocation.toBukkit(absolutePosition.position(), teleporttransition.newLevel().getWorld(), absolutePosition.yRot(), absolutePosition.xRot());
                EntityTeleportEvent teleEvent = CraftEventFactory.callEntityTeleportEvent(this, to);
                if (teleEvent.isCancelled()) {
                    return null;
                }
                if (!to.equals(teleEvent.getTo())) {
                    to = teleEvent.getTo();
                    teleporttransition = new TeleportTransition(((CraftWorld) to.getWorld()).getHandle(), CraftLocation.toVec3D(to), Vec3D.ZERO, to.getYaw(), to.getPitch(), teleporttransition.missingRespawnBlock(), teleporttransition.asPassenger(), Set.of(), teleporttransition.postTeleportTransition(), teleporttransition.cause());
                }
                // CraftBukkit end
                worldserver1 = teleporttransition.newLevel();
                boolean flag = worldserver.dimension() != worldserver1.dimension();

                if (!teleporttransition.asPassenger()) {
                    this.stopRiding();
                }

                if (flag) {
                    return this.teleportCrossDimension(worldserver1, teleporttransition); // CraftBukkit - decompile error
                }

                return this.teleportSameDimension(worldserver1, teleporttransition);
            }
        }

        return null;
    }

    private Entity teleportSameDimension(WorldServer worldserver, TeleportTransition teleporttransition) {
        for (Entity entity : this.getPassengers()) {
            entity.teleport(this.calculatePassengerTransition(teleporttransition, entity));
        }

        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("teleportSameDimension");
        this.teleportSetPosition(PositionMoveRotation.of(teleporttransition), teleporttransition.relatives());
        if (!teleporttransition.asPassenger()) {
            this.sendTeleportTransitionToRidingPlayers(teleporttransition);
        }

        teleporttransition.postTeleportTransition().onTransition(this);
        gameprofilerfiller.pop();
        return this;
    }

    private Entity teleportCrossDimension(WorldServer worldserver, TeleportTransition teleporttransition) {
        List<Entity> list = this.getPassengers();
        List<Entity> list1 = new ArrayList(list.size());

        this.ejectPassengers();

        for (Entity entity : list) {
            Entity entity1 = entity.teleport(this.calculatePassengerTransition(teleporttransition, entity));

            if (entity1 != null) {
                list1.add(entity1);
            }
        }

        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("teleportCrossDimension");
        Entity entity2 = this.getType().create(worldserver, EntitySpawnReason.DIMENSION_TRAVEL);

        if (entity2 == null) {
            gameprofilerfiller.pop();
            return null;
        } else {
            entity2.restoreFrom(this);
            this.removeAfterChangingDimensions();
            // CraftBukkit start - Forward the CraftEntity to the new entity
            this.getBukkitEntity().setHandle(entity2);
            entity2.bukkitEntity = this.getBukkitEntity();
            // CraftBukkit end
            entity2.teleportSetPosition(PositionMoveRotation.of(teleporttransition), teleporttransition.relatives());
            if (this.inWorld) worldserver.addDuringTeleport(entity2); // CraftBukkit - Don't spawn the new entity if the current entity isn't spawned

            for (Entity entity3 : list1) {
                entity3.startRiding(entity2, true);
            }

            worldserver.resetEmptyTime();
            teleporttransition.postTeleportTransition().onTransition(entity2);
            gameprofilerfiller.pop();
            return entity2;
        }
    }

    private TeleportTransition calculatePassengerTransition(TeleportTransition teleporttransition, Entity entity) {
        float f = teleporttransition.yRot() + (teleporttransition.relatives().contains(Relative.Y_ROT) ? 0.0F : entity.getYRot() - this.getYRot());
        float f1 = teleporttransition.xRot() + (teleporttransition.relatives().contains(Relative.X_ROT) ? 0.0F : entity.getXRot() - this.getXRot());
        Vec3D vec3d = entity.position().subtract(this.position());
        Vec3D vec3d1 = teleporttransition.position().add(teleporttransition.relatives().contains(Relative.X) ? 0.0D : vec3d.x(), teleporttransition.relatives().contains(Relative.Y) ? 0.0D : vec3d.y(), teleporttransition.relatives().contains(Relative.Z) ? 0.0D : vec3d.z());

        return teleporttransition.withPosition(vec3d1).withRotation(f, f1).transitionAsPassenger();
    }

    private void sendTeleportTransitionToRidingPlayers(TeleportTransition teleporttransition) {
        Entity entity = this.getControllingPassenger();

        for (Entity entity1 : this.getIndirectPassengers()) {
            if (entity1 instanceof EntityPlayer entityplayer) {
                if (entity != null && entityplayer.getId() == entity.getId()) {
                    entityplayer.connection.send(PacketPlayOutEntityTeleport.teleport(this.getId(), PositionMoveRotation.of(teleporttransition), teleporttransition.relatives(), this.onGround));
                } else {
                    entityplayer.connection.send(PacketPlayOutEntityTeleport.teleport(this.getId(), PositionMoveRotation.of(this), Set.of(), this.onGround));
                }
            }
        }

    }

    public void teleportSetPosition(PositionMoveRotation positionmoverotation, Set<Relative> set) {
        PositionMoveRotation positionmoverotation1 = PositionMoveRotation.of(this);
        PositionMoveRotation positionmoverotation2 = PositionMoveRotation.calculateAbsolute(positionmoverotation1, positionmoverotation, set);

        this.setPosRaw(positionmoverotation2.position().x, positionmoverotation2.position().y, positionmoverotation2.position().z);
        this.setYRot(positionmoverotation2.yRot());
        this.setYHeadRot(positionmoverotation2.yRot());
        this.setXRot(positionmoverotation2.xRot());
        this.reapplyPosition();
        this.setOldPosAndRot();
        this.setDeltaMovement(positionmoverotation2.deltaMovement());
        this.movementThisTick.clear();
    }

    public void forceSetRotation(float f, float f1) {
        this.setYRot(f);
        this.setYHeadRot(f);
        this.setXRot(f1);
        this.setOldRot();
    }

    public void placePortalTicket(BlockPosition blockposition) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            worldserver.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkCoordIntPair(blockposition), 3);
        }

    }

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION, null); // CraftBukkit - add Bukkit remove cause
        if (this instanceof Leashable leashable) {
            this.level().getCraftServer().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), UnleashReason.UNKNOWN)); // CraftBukkit
            leashable.removeLeash();
        }

    }

    public Vec3D getRelativePortalPosition(EnumDirection.EnumAxis enumdirection_enumaxis, BlockUtil.Rectangle blockutil_rectangle) {
        return BlockPortalShape.getRelativePosition(blockutil_rectangle, enumdirection_enumaxis, this.position(), this.getDimensions(this.getPose()));
    }

    // CraftBukkit start
    public CraftPortalEvent callPortalEvent(Entity entity, Location exit, PlayerTeleportEvent.TeleportCause cause, int searchRadius, int creationRadius) {
        org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
        Location enter = bukkitEntity.getLocation();

        EntityPortalEvent event = new EntityPortalEvent(bukkitEntity, enter, exit, searchRadius, true, creationRadius);
        event.getEntity().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null || !entity.isAlive()) {
            return null;
        }
        return new CraftPortalEvent(event);
    }
    // CraftBukkit end

    public boolean canUsePortal(boolean flag) {
        return (flag || !this.isPassenger()) && this.isAlive();
    }

    public boolean canTeleport(World world, World world1) {
        if (world.dimension() == World.END && world1.dimension() == World.OVERWORLD) {
            for (Entity entity : this.getPassengers()) {
                if (entity instanceof EntityPlayer) {
                    EntityPlayer entityplayer = (EntityPlayer) entity;

                    if (!entityplayer.seenCredits) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public float getBlockExplosionResistance(Explosion explosion, IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata, Fluid fluid, float f) {
        return f;
    }

    public boolean shouldBlockExplode(Explosion explosion, IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata, float f) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportSystemDetails crashreportsystemdetails) {
        crashreportsystemdetails.setDetail("Entity Type", () -> {
            String s = String.valueOf(EntityTypes.getKey(this.getType()));

            return s + " (" + this.getClass().getCanonicalName() + ")";
        });
        crashreportsystemdetails.setDetail("Entity ID", this.id);
        crashreportsystemdetails.setDetail("Entity Name", () -> {
            return this.getName().getString();
        });
        crashreportsystemdetails.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        crashreportsystemdetails.setDetail("Entity's Block location", CrashReportSystemDetails.formatLocation(this.level(), MathHelper.floor(this.getX()), MathHelper.floor(this.getY()), MathHelper.floor(this.getZ())));
        Vec3D vec3d = this.getDeltaMovement();

        crashreportsystemdetails.setDetail("Entity's Momentum", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3d.x, vec3d.y, vec3d.z));
        crashreportsystemdetails.setDetail("Entity's Passengers", () -> {
            return this.getPassengers().toString();
        });
        crashreportsystemdetails.setDetail("Entity's Vehicle", () -> {
            return String.valueOf(this.getVehicle());
        });
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
        this.stringUUID = this.uuid.toString();
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    @Override
    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return Entity.viewScale;
    }

    public static void setViewScale(double d0) {
        Entity.viewScale = d0;
    }

    @Override
    public IChatBaseComponent getDisplayName() {
        return ScoreboardTeam.formatNameForTeam(this.getTeam(), this.getName()).withStyle((chatmodifier) -> {
            return chatmodifier.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID());
        });
    }

    public void setCustomName(@Nullable IChatBaseComponent ichatbasecomponent) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME, Optional.ofNullable(ichatbasecomponent));
    }

    @Nullable
    @Override
    public IChatBaseComponent getCustomName() {
        return (IChatBaseComponent) ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).orElse((Object) null);
    }

    @Override
    public boolean hasCustomName() {
        return ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).isPresent();
    }

    public void setCustomNameVisible(boolean flag) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME_VISIBLE, flag);
    }

    public boolean isCustomNameVisible() {
        return (Boolean) this.entityData.get(Entity.DATA_CUSTOM_NAME_VISIBLE);
    }

    // CraftBukkit start
    public final boolean teleportTo(WorldServer worldserver, double d0, double d1, double d2, Set<Relative> set, float f, float f1, boolean flag) {
        return teleportTo(worldserver, d0, d1, d2, set, f, f1, flag, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public boolean teleportTo(WorldServer worldserver, double d0, double d1, double d2, Set<Relative> set, float f, float f1, boolean flag, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        Entity entity = this.teleport(new TeleportTransition(worldserver, new Vec3D(d0, d1, d2), Vec3D.ZERO, f, f1, set, TeleportTransition.DO_NOTHING, cause));
        // CraftBukkit end

        return entity != null;
    }

    public void dismountTo(double d0, double d1, double d2) {
        this.teleportTo(d0, d1, d2);
    }

    public void teleportTo(double d0, double d1, double d2) {
        if (this.level() instanceof WorldServer) {
            this.snapTo(d0, d1, d2, this.getYRot(), this.getXRot());
            this.teleportPassengers();
        }
    }

    private void teleportPassengers() {
        this.getSelfAndPassengers().forEach((entity) -> {
            UnmodifiableIterator unmodifiableiterator = entity.passengers.iterator();

            while (unmodifiableiterator.hasNext()) {
                Entity entity1 = (Entity) unmodifiableiterator.next();

                entity.positionRider(entity1, Entity::snapTo);
            }

        });
    }

    public void teleportRelative(double d0, double d1, double d2) {
        this.teleportTo(this.getX() + d0, this.getY() + d1, this.getZ() + d2);
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    @Override
    public void onSyncedDataUpdated(List<DataWatcher.c<?>> list) {}

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (Entity.DATA_POSE.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

    }

    /** @deprecated */
    @Deprecated
    protected void fixupDimensions() {
        EntityPose entitypose = this.getPose();
        EntitySize entitysize = this.getDimensions(entitypose);

        this.dimensions = entitysize;
        this.eyeHeight = entitysize.eyeHeight();
    }

    public void refreshDimensions() {
        EntitySize entitysize = this.dimensions;
        EntityPose entitypose = this.getPose();
        EntitySize entitysize1 = this.getDimensions(entitypose);

        this.dimensions = entitysize1;
        this.eyeHeight = entitysize1.eyeHeight();
        this.reapplyPosition();
        boolean flag = entitysize1.width() <= 4.0F && entitysize1.height() <= 4.0F;

        if (!this.level.isClientSide && !this.firstTick && !this.noPhysics && flag && (entitysize1.width() > entitysize.width() || entitysize1.height() > entitysize.height()) && !(this instanceof EntityHuman)) {
            this.fudgePositionAfterSizeChange(entitysize);
        }

    }

    public boolean fudgePositionAfterSizeChange(EntitySize entitysize) {
        EntitySize entitysize1 = this.getDimensions(this.getPose());
        Vec3D vec3d = this.position().add(0.0D, (double) entitysize.height() / 2.0D, 0.0D);
        double d0 = (double) Math.max(0.0F, entitysize1.width() - entitysize.width()) + 1.0E-6D;
        double d1 = (double) Math.max(0.0F, entitysize1.height() - entitysize.height()) + 1.0E-6D;
        VoxelShape voxelshape = VoxelShapes.create(AxisAlignedBB.ofSize(vec3d, d0, d1, d0));
        Optional<Vec3D> optional = this.level.findFreePosition(this, voxelshape, vec3d, (double) entitysize1.width(), (double) entitysize1.height(), (double) entitysize1.width());

        if (optional.isPresent()) {
            this.setPos(((Vec3D) optional.get()).add(0.0D, (double) (-entitysize1.height()) / 2.0D, 0.0D));
            return true;
        } else {
            if (entitysize1.width() > entitysize.width() && entitysize1.height() > entitysize.height()) {
                VoxelShape voxelshape1 = VoxelShapes.create(AxisAlignedBB.ofSize(vec3d, d0, 1.0E-6D, d0));
                Optional<Vec3D> optional1 = this.level.findFreePosition(this, voxelshape1, vec3d, (double) entitysize1.width(), (double) entitysize.height(), (double) entitysize1.width());

                if (optional1.isPresent()) {
                    this.setPos(((Vec3D) optional1.get()).add(0.0D, (double) (-entitysize.height()) / 2.0D + 1.0E-6D, 0.0D));
                    return true;
                }
            }

            return false;
        }
    }

    public EnumDirection getDirection() {
        return EnumDirection.fromYRot((double) this.getYRot());
    }

    public EnumDirection getMotionDirection() {
        return this.getDirection();
    }

    protected ChatHoverable createHoverEvent() {
        return new ChatHoverable.c(new ChatHoverable.b(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(EntityPlayer entityplayer) {
        return true;
    }

    @Override
    public final AxisAlignedBB getBoundingBox() {
        return this.bb;
    }

    public final void setBoundingBox(AxisAlignedBB axisalignedbb) {
        // CraftBukkit start - block invalid bounding boxes
        double minX = axisalignedbb.minX,
                minY = axisalignedbb.minY,
                minZ = axisalignedbb.minZ,
                maxX = axisalignedbb.maxX,
                maxY = axisalignedbb.maxY,
                maxZ = axisalignedbb.maxZ;
        double len = axisalignedbb.maxX - axisalignedbb.minX;
        if (len < 0) maxX = minX;
        if (len > 64) maxX = minX + 64.0;

        len = axisalignedbb.maxY - axisalignedbb.minY;
        if (len < 0) maxY = minY;
        if (len > 64) maxY = minY + 64.0;

        len = axisalignedbb.maxZ - axisalignedbb.minZ;
        if (len < 0) maxZ = minZ;
        if (len > 64) maxZ = minZ + 64.0;
        this.bb = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        // CraftBukkit end
    }

    public final float getEyeHeight(EntityPose entitypose) {
        return this.getDimensions(entitypose).eyeHeight();
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public Vec3D getLeashOffset(float f) {
        return this.getLeashOffset();
    }

    protected Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) this.getEyeHeight(), (double) (this.getBbWidth() * 0.4F));
    }

    public SlotAccess getSlot(int i) {
        return SlotAccess.NULL;
    }

    public World getCommandSenderWorld() {
        return this.level();
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level().getServer();
    }

    public EnumInteractionResult interactAt(EntityHuman entityhuman, Vec3D vec3d, EnumHand enumhand) {
        return EnumInteractionResult.PASS;
    }

    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    public void startSeenByPlayer(EntityPlayer entityplayer) {}

    public void stopSeenByPlayer(EntityPlayer entityplayer) {}

    public float rotate(EnumBlockRotation enumblockrotation) {
        float f = MathHelper.wrapDegrees(this.getYRot());

        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    public float mirror(EnumBlockMirror enumblockmirror) {
        float f = MathHelper.wrapDegrees(this.getYRot());

        switch (enumblockmirror) {
            case FRONT_BACK:
                return -f;
            case LEFT_RIGHT:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public ProjectileDeflection deflection(IProjectile iprojectile) {
        return this.getType().is(TagsEntity.DEFLECTS_PROJECTILES) ? ProjectileDeflection.REVERSE : ProjectileDeflection.NONE;
    }

    @Nullable
    public EntityLiving getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    @Nullable
    public Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : (Entity) this.passengers.get(0);
    }

    public boolean hasPassenger(Entity entity) {
        return this.passengers.contains(entity);
    }

    public boolean hasPassenger(Predicate<Entity> predicate) {
        UnmodifiableIterator unmodifiableiterator = this.passengers.iterator();

        while (unmodifiableiterator.hasNext()) {
            Entity entity = (Entity) unmodifiableiterator.next();

            if (predicate.test(entity)) {
                return true;
            }
        }

        return false;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    @Override
    public Stream<Entity> getSelfAndPassengers() {
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    @Override
    public Stream<Entity> getPassengersAndSelf() {
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        return () -> {
            return this.getIndirectPassengersStream().iterator();
        };
    }

    public int countPlayerPassengers() {
        return (int) this.getIndirectPassengersStream().filter((entity) -> {
            return entity instanceof EntityHuman;
        }).count();
    }

    public boolean hasExactlyOnePlayerPassenger() {
        return this.countPlayerPassengers() == 1;
    }

    public Entity getRootVehicle() {
        Entity entity;

        for (entity = this; entity.isPassenger(); entity = entity.getVehicle()) {
            ;
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity entity) {
        return this.getRootVehicle() == entity.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity entity) {
        if (!entity.isPassenger()) {
            return false;
        } else {
            Entity entity1 = entity.getVehicle();

            return entity1 == this ? true : this.hasIndirectPassenger(entity1);
        }
    }

    public final boolean isLocalInstanceAuthoritative() {
        return this.level.isClientSide() ? this.isLocalClientAuthoritative() : !this.isClientAuthoritative();
    }

    protected boolean isLocalClientAuthoritative() {
        EntityLiving entityliving = this.getControllingPassenger();

        return entityliving != null && entityliving.isLocalClientAuthoritative();
    }

    public boolean isClientAuthoritative() {
        EntityLiving entityliving = this.getControllingPassenger();

        return entityliving != null && entityliving.isClientAuthoritative();
    }

    public boolean canSimulateMovement() {
        return this.isLocalInstanceAuthoritative();
    }

    public boolean isEffectiveAi() {
        return this.isLocalInstanceAuthoritative();
    }

    protected static Vec3D getCollisionHorizontalEscapeVector(double d0, double d1, float f) {
        double d2 = (d0 + d1 + (double) 1.0E-5F) / 2.0D;
        float f1 = -MathHelper.sin(f * ((float) Math.PI / 180F));
        float f2 = MathHelper.cos(f * ((float) Math.PI / 180F));
        float f3 = Math.max(Math.abs(f1), Math.abs(f2));

        return new Vec3D((double) f1 * d2 / (double) f3, 0.0D, (double) f2 * d2 / (double) f3);
    }

    public Vec3D getDismountLocationForPassenger(EntityLiving entityliving) {
        return new Vec3D(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    @Nullable
    public Entity getControlledVehicle() {
        return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
    }

    public EnumPistonReaction getPistonPushReaction() {
        return EnumPistonReaction.NORMAL;
    }

    public SoundCategory getSoundSource() {
        return SoundCategory.NEUTRAL;
    }

    public int getFireImmuneTicks() {
        return 1;
    }

    // CraftBukkit start
    private final ICommandListener commandSource = new ICommandListener() {

        @Override
        public void sendSystemMessage(IChatBaseComponent ichatbasecomponent) {
        }

        @Override
        public CommandSender getBukkitSender(CommandListenerWrapper wrapper) {
            return Entity.this.getBukkitEntity();
        }

        @Override
        public boolean acceptsSuccess() {
            return ((WorldServer) Entity.this.level()).getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return true;
        }
    };
    // CraftBukkit end

    public CommandListenerWrapper createCommandSourceStackForNameResolution(WorldServer worldserver) {
        return new CommandListenerWrapper(commandSource, this.position(), this.getRotationVector(), worldserver, 0, this.getName().getString(), this.getDisplayName(), worldserver.getServer(), this); // CraftBukkit
    }

    public void lookAt(ArgumentAnchor.Anchor argumentanchor_anchor, Vec3D vec3d) {
        Vec3D vec3d1 = argumentanchor_anchor.apply(this);
        double d0 = vec3d.x - vec3d1.x;
        double d1 = vec3d.y - vec3d1.y;
        double d2 = vec3d.z - vec3d1.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        this.setXRot(MathHelper.wrapDegrees((float) (-(MathHelper.atan2(d1, d3) * (double) (180F / (float) Math.PI)))));
        this.setYRot(MathHelper.wrapDegrees((float) (MathHelper.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public float getPreciseBodyRotation(float f) {
        return MathHelper.lerp(f, this.yRotO, this.yRot);
    }

    public boolean updateFluidHeightAndDoFluidPushing(TagKey<FluidType> tagkey, double d0) {
        if (this.touchingUnloadedChunk()) {
            return false;
        } else {
            AxisAlignedBB axisalignedbb = this.getBoundingBox().deflate(0.001D);
            int i = MathHelper.floor(axisalignedbb.minX);
            int j = MathHelper.ceil(axisalignedbb.maxX);
            int k = MathHelper.floor(axisalignedbb.minY);
            int l = MathHelper.ceil(axisalignedbb.maxY);
            int i1 = MathHelper.floor(axisalignedbb.minZ);
            int j1 = MathHelper.ceil(axisalignedbb.maxZ);
            double d1 = 0.0D;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3D vec3d = Vec3D.ZERO;
            int k1 = 0;
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();

            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        blockposition_mutableblockposition.set(l1, i2, j2);
                        Fluid fluid = this.level().getFluidState(blockposition_mutableblockposition);

                        if (fluid.is(tagkey)) {
                            double d2 = (double) ((float) i2 + fluid.getHeight(this.level(), blockposition_mutableblockposition));

                            if (d2 >= axisalignedbb.minY) {
                                flag1 = true;
                                d1 = Math.max(d2 - axisalignedbb.minY, d1);
                                if (flag) {
                                    Vec3D vec3d1 = fluid.getFlow(this.level(), blockposition_mutableblockposition);

                                    if (d1 < 0.4D) {
                                        vec3d1 = vec3d1.scale(d1);
                                    }

                                    vec3d = vec3d.add(vec3d1);
                                    ++k1;
                                }
                                // CraftBukkit start - store last lava contact location
                                if (tagkey == TagsFluid.LAVA) {
                                    this.lastLavaContact = blockposition_mutableblockposition.immutable();
                                }
                                // CraftBukkit end
                            }
                        }
                    }
                }
            }

            if (vec3d.length() > 0.0D) {
                if (k1 > 0) {
                    vec3d = vec3d.scale(1.0D / (double) k1);
                }

                if (!(this instanceof EntityHuman)) {
                    vec3d = vec3d.normalize();
                }

                Vec3D vec3d2 = this.getDeltaMovement();

                vec3d = vec3d.scale(d0);
                double d3 = 0.003D;

                if (Math.abs(vec3d2.x) < 0.003D && Math.abs(vec3d2.z) < 0.003D && vec3d.length() < 0.0045000000000000005D) {
                    vec3d = vec3d.normalize().scale(0.0045000000000000005D);
                }

                this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
            }

            this.fluidHeight.put(tagkey, d1);
            return flag1;
        }
    }

    public boolean touchingUnloadedChunk() {
        AxisAlignedBB axisalignedbb = this.getBoundingBox().inflate(1.0D);
        int i = MathHelper.floor(axisalignedbb.minX);
        int j = MathHelper.ceil(axisalignedbb.maxX);
        int k = MathHelper.floor(axisalignedbb.minZ);
        int l = MathHelper.ceil(axisalignedbb.maxZ);

        return !this.level().hasChunksAt(i, k, j, l);
    }

    public double getFluidHeight(TagKey<FluidType> tagkey) {
        return this.fluidHeight.getDouble(tagkey);
    }

    public double getFluidJumpThreshold() {
        return (double) this.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
    }

    public final float getBbWidth() {
        return this.dimensions.width();
    }

    public final float getBbHeight() {
        return this.dimensions.height();
    }

    public Packet<PacketListenerPlayOut> getAddEntityPacket(EntityTrackerEntry entitytrackerentry) {
        return new PacketPlayOutSpawnEntity(this, entitytrackerentry);
    }

    public EntitySize getDimensions(EntityPose entitypose) {
        return this.type.getDimensions();
    }

    public final EntityAttachments getAttachments() {
        return this.dimensions.attachments();
    }

    public Vec3D position() {
        return this.position;
    }

    public Vec3D trackingPosition() {
        return this.position();
    }

    @Override
    public BlockPosition blockPosition() {
        return this.blockPosition;
    }

    public IBlockData getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.level().getBlockState(this.blockPosition());
        }

        return this.inBlockState;
    }

    public ChunkCoordIntPair chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3D getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3D vec3d) {
        this.deltaMovement = vec3d;
    }

    public void addDeltaMovement(Vec3D vec3d) {
        this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
    }

    public void setDeltaMovement(double d0, double d1, double d2) {
        this.setDeltaMovement(new Vec3D(d0, d1, d2));
    }

    public final int getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double d0) {
        return this.position.x + (double) this.getBbWidth() * d0;
    }

    public double getRandomX(double d0) {
        return this.getX((2.0D * this.random.nextDouble() - 1.0D) * d0);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double d0) {
        return this.position.y + (double) this.getBbHeight() * d0;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + (double) this.eyeHeight;
    }

    public final int getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double d0) {
        return this.position.z + (double) this.getBbWidth() * d0;
    }

    public double getRandomZ(double d0) {
        return this.getZ((2.0D * this.random.nextDouble() - 1.0D) * d0);
    }

    public final void setPosRaw(double d0, double d1, double d2) {
        if (this.position.x != d0 || this.position.y != d1 || this.position.z != d2) {
            this.position = new Vec3D(d0, d1, d2);
            int i = MathHelper.floor(d0);
            int j = MathHelper.floor(d1);
            int k = MathHelper.floor(d2);

            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPosition(i, j, k);
                this.inBlockState = null;
                if (SectionPosition.blockToSectionCoord(i) != this.chunkPosition.x || SectionPosition.blockToSectionCoord(k) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkCoordIntPair(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
        }

    }

    public void checkDespawn() {}

    public Vec3D getRopeHoldPosition(float f) {
        return this.getPosition(f).add(0.0D, (double) this.eyeHeight * 0.7D, 0.0D);
    }

    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        int i = packetplayoutspawnentity.getId();
        double d0 = packetplayoutspawnentity.getX();
        double d1 = packetplayoutspawnentity.getY();
        double d2 = packetplayoutspawnentity.getZ();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.snapTo(d0, d1, d2, packetplayoutspawnentity.getYRot(), packetplayoutspawnentity.getXRot());
        this.setId(i);
        this.setUUID(packetplayoutspawnentity.getUUID());
        Vec3D vec3d = new Vec3D(packetplayoutspawnentity.getXa(), packetplayoutspawnentity.getYa(), packetplayoutspawnentity.getZa());

        this.setDeltaMovement(vec3d);
    }

    @Nullable
    public ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean flag) {
        this.isInPowderSnow = flag;
    }

    public boolean canFreeze() {
        return !this.getType().is(TagsEntity.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return this.getTicksFrozen() > 0;
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    public void setYRot(float f) {
        if (!Float.isFinite(f)) {
            SystemUtils.logAndPauseIfInIde("Invalid entity rotation: " + f + ", discarding.");
        } else {
            this.yRot = f;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(float f) {
        if (!Float.isFinite(f)) {
            SystemUtils.logAndPauseIfInIde("Invalid entity rotation: " + f + ", discarding.");
        } else {
            this.xRot = Math.clamp(f % 360.0F, -90.0F, 90.0F);
        }
    }

    public boolean canSprint() {
        return false;
    }

    public float maxUpStep() {
        return 0.0F;
    }

    public void onExplosionHit(@Nullable Entity entity) {}

    @Override
    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    @Nullable
    public Entity.RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    @Override
    public final void setRemoved(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        setRemoved(entity_removalreason, null);
    }

    @Override
    public final void setRemoved(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        CraftEventFactory.callEntityRemoveEvent(this, cause);
        // CraftBukkit end
        if (this.removalReason == null) {
            this.removalReason = entity_removalreason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        this.getPassengers().forEach(Entity::stopRiding);
        this.levelCallback.onRemove(entity_removalreason);
        this.onRemoval(entity_removalreason);
    }

    public void unsetRemoved() {
        this.removalReason = null;
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback entityinlevelcallback) {
        this.levelCallback = entityinlevelcallback;
    }

    @Override
    public boolean shouldBeSaved() {
        return this.removalReason != null && !this.removalReason.shouldSave() ? false : (this.isPassenger() ? false : !this.isVehicle() || !this.hasExactlyOnePlayerPassenger());
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(WorldServer worldserver, BlockPosition blockposition) {
        return true;
    }

    public World level() {
        return this.level;
    }

    protected void setLevel(World world) {
        this.level = world;
    }

    public DamageSources damageSources() {
        return this.level().damageSources();
    }

    public IRegistryCustom registryAccess() {
        return this.level().registryAccess();
    }

    protected void lerpPositionAndRotationStep(int i, double d0, double d1, double d2, double d3, double d4) {
        double d5 = 1.0D / (double) i;
        double d6 = MathHelper.lerp(d5, this.getX(), d0);
        double d7 = MathHelper.lerp(d5, this.getY(), d1);
        double d8 = MathHelper.lerp(d5, this.getZ(), d2);
        float f = (float) MathHelper.rotLerp(d5, (double) this.getYRot(), d3);
        float f1 = (float) MathHelper.lerp(d5, (double) this.getXRot(), d4);

        this.setPos(d6, d7, d8);
        this.setRot(f, f1);
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public Vec3D getKnownMovement() {
        EntityLiving entityliving = this.getControllingPassenger();

        if (entityliving instanceof EntityHuman entityhuman) {
            if (this.isAlive()) {
                return entityhuman.getKnownMovement();
            }
        }

        return this.getDeltaMovement();
    }

    @Nullable
    public ItemStack getWeaponItem() {
        return null;
    }

    public Optional<ResourceKey<LootTable>> getLootTable() {
        return this.type.getDefaultLootTable();
    }

    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.CUSTOM_NAME);
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.CUSTOM_DATA);
    }

    public final void applyComponentsFromItemStack(ItemStack itemstack) {
        this.applyImplicitComponents(itemstack.getComponents());
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.CUSTOM_NAME ? castComponentValue(datacomponenttype, this.getCustomName()) : (datacomponenttype == DataComponents.CUSTOM_DATA ? castComponentValue(datacomponenttype, this.customData) : null));
    }

    @Nullable
    @Contract("_,!null->!null;_,_->_")
    protected static <T> T castComponentValue(DataComponentType<T> datacomponenttype, @Nullable Object object) {
        return (T) object;
    }

    public <T> void setComponent(DataComponentType<T> datacomponenttype, T t0) {
        this.applyImplicitComponent(datacomponenttype, t0);
    }

    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.CUSTOM_NAME) {
            this.setCustomName((IChatBaseComponent) castComponentValue(DataComponents.CUSTOM_NAME, t0));
            return true;
        } else if (datacomponenttype == DataComponents.CUSTOM_DATA) {
            this.customData = (CustomData) castComponentValue(DataComponents.CUSTOM_DATA, t0);
            return true;
        } else {
            return false;
        }
    }

    protected <T> boolean applyImplicitComponentIfPresent(DataComponentGetter datacomponentgetter, DataComponentType<T> datacomponenttype) {
        T t0 = (T) datacomponentgetter.get(datacomponenttype);

        return t0 != null ? this.applyImplicitComponent(datacomponenttype, t0) : false;
    }

    private static record b(Vec3D from, Vec3D to) {

    }

    public static enum MovementEmission {

        NONE(false, false), SOUNDS(true, false), EVENTS(false, true), ALL(true, true);

        final boolean sounds;
        final boolean events;

        private MovementEmission(final boolean flag, final boolean flag1) {
            this.sounds = flag;
            this.events = flag1;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    public static enum RemovalReason {

        KILLED(true, false), DISCARDED(true, false), UNLOADED_TO_CHUNK(false, true), UNLOADED_WITH_PLAYER(false, false), CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        private RemovalReason(final boolean flag, final boolean flag1) {
            this.destroy = flag;
            this.save = flag1;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }

    @FunctionalInterface
    public interface MoveFunction {

        void accept(Entity entity, double d0, double d1, double d2);
    }
}
