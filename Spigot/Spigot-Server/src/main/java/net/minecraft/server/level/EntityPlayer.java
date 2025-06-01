package net.minecraft.server.level;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.EnumChatFormat;
import net.minecraft.ReportedException;
import net.minecraft.SystemUtils;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.ICommandListener;
import net.minecraft.commands.arguments.ArgumentAnchor;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.particles.ParticleParamBlock;
import net.minecraft.core.particles.Particles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatHoverable;
import net.minecraft.network.chat.ChatMessageType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.PacketPlayOutAbilities;
import net.minecraft.network.protocol.game.PacketPlayOutAnimation;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.network.protocol.game.PacketPlayOutCamera;
import net.minecraft.network.protocol.game.PacketPlayOutCloseWindow;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEffect;
import net.minecraft.network.protocol.game.PacketPlayOutEntityStatus;
import net.minecraft.network.protocol.game.PacketPlayOutExperience;
import net.minecraft.network.protocol.game.PacketPlayOutGameStateChange;
import net.minecraft.network.protocol.game.PacketPlayOutLookAt;
import net.minecraft.network.protocol.game.PacketPlayOutMount;
import net.minecraft.network.protocol.game.PacketPlayOutNamedSoundEffect;
import net.minecraft.network.protocol.game.PacketPlayOutOpenBook;
import net.minecraft.network.protocol.game.PacketPlayOutOpenSignEditor;
import net.minecraft.network.protocol.game.PacketPlayOutOpenWindow;
import net.minecraft.network.protocol.game.PacketPlayOutOpenWindowHorse;
import net.minecraft.network.protocol.game.PacketPlayOutOpenWindowMerchant;
import net.minecraft.network.protocol.game.PacketPlayOutRemoveEntityEffect;
import net.minecraft.network.protocol.game.PacketPlayOutRespawn;
import net.minecraft.network.protocol.game.PacketPlayOutServerDifficulty;
import net.minecraft.network.protocol.game.PacketPlayOutSetSlot;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.network.protocol.game.PacketPlayOutUpdateHealth;
import net.minecraft.network.protocol.game.PacketPlayOutWindowData;
import net.minecraft.network.protocol.game.PacketPlayOutWindowItems;
import net.minecraft.network.protocol.status.ServerPing;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.AdvancementDataPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ITextFilter;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.stats.RecipeBookServer;
import net.minecraft.stats.ServerStatisticManager;
import net.minecraft.stats.Statistic;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.HashOps;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumHand;
import net.minecraft.world.IInventory;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.EnumMainHand;
import net.minecraft.world.entity.IEntityAngerable;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.animal.EntityPig;
import net.minecraft.world.entity.animal.horse.EntityHorseAbstract;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.monster.EntityStrider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.EnumChatVisibility;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.EntityEnderPearl;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.entity.vehicle.EntityMinecartAbstract;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerHorse;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.ICrafting;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SlotResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldown;
import net.minecraft.world.item.ItemCooldownPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemWorldMap;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.IRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.trading.MerchantRecipeList;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockBed;
import net.minecraft.world.level.block.BlockFacingHorizontal;
import net.minecraft.world.level.block.BlockRespawnAnchor;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityCommand;
import net.minecraft.world.level.block.entity.TileEntitySign;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;
import net.minecraft.world.scores.criteria.IScoreboardCriteria;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.food.FoodMetaData;
import net.minecraft.world.inventory.ContainerPlayer;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.level.block.BlockChest;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftWorldBorder;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftDimensionUtil;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.MainHand;
// CraftBukkit end

public class EntityPlayer extends EntityHuman {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double BLOCK_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0D;
    public static final double ENTITY_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 3.0D;
    public static final int ENDER_PEARL_TICKET_RADIUS = 2;
    public static final String ENDER_PEARLS_TAG = "ender_pearls";
    public static final String ENDER_PEARL_DIMENSION_TAG = "ender_pearl_dimension";
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(MinecraftKey.withDefaultNamespace("creative_mode_block_range"), 0.5D, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(MinecraftKey.withDefaultNamespace("creative_mode_entity_range"), 2.0D, AttributeModifier.Operation.ADD_VALUE);
    private static final IChatBaseComponent SPAWN_SET_MESSAGE = IChatBaseComponent.translatable("block.minecraft.set_spawn");
    private static final boolean DEFAULT_SEEN_CREDITS = false;
    private static final boolean DEFAULT_SPAWN_EXTRA_PARTICLES_ON_FALL = false;
    public PlayerConnection connection;
    public final MinecraftServer server;
    public final PlayerInteractManager gameMode;
    private final AdvancementDataPlayer advancements;
    private final ServerStatisticManager stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    private EnumChatVisibility chatVisibility;
    private ParticleStatus particleStatus;
    private boolean canChatColor;
    private long lastActionTime;
    @Nullable
    private Entity camera;
    public boolean isChangingDimension;
    public boolean seenCredits;
    private final RecipeBookServer recipeBook;
    @Nullable
    private Vec3D levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance;
    public String language = "en_us"; // CraftBukkit - default
    @Nullable
    private Vec3D startingToFallPosition;
    @Nullable
    private Vec3D enteredNetherPosition;
    @Nullable
    private Vec3D enteredLavaOnVehiclePosition;
    private SectionPosition lastSectionPos;
    private ChunkTrackingView chunkTrackingView;
    @Nullable
    private EntityPlayer.RespawnConfig respawnConfig;
    private final ITextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall;
    private WardenSpawnTracker wardenSpawnTracker;
    @Nullable
    private BlockPosition raidOmenPosition;
    private Vec3D lastKnownClientMovement;
    private Input lastClientInput;
    private final Set<EntityEnderPearl> enderPearls;
    private final ContainerSynchronizer containerSynchronizer;
    private final ICrafting containerListener;
    @Nullable
    private RemoteChatSession chatSession;
    @Nullable
    public final Object object;
    private final ICommandListener commandSource;
    private int containerCounter;
    public boolean wonGame;

    // CraftBukkit start
    public CraftPlayer.TransferCookieConnection transferCookieConnection;
    public String displayName;
    public IChatBaseComponent listName;
    public int listOrder = 0;
    public org.bukkit.Location compassTarget;
    public int newExp = 0;
    public int newLevel = 0;
    public int newTotalExp = 0;
    public boolean keepLevel = false;
    public double maxHealthCache;
    public boolean joining = true;
    public boolean sentListPacket = false;
    public String kickLeaveMessage = null; // SPIGOT-3034: Forward leave message to PlayerQuitEvent
    // CraftBukkit end

    public EntityPlayer(MinecraftServer minecraftserver, WorldServer worldserver, GameProfile gameprofile, ClientInformation clientinformation) {
        super(worldserver, worldserver.getSharedSpawnPos(), worldserver.getSharedSpawnAngle(), gameprofile);
        this.chatVisibility = EnumChatVisibility.FULL;
        this.particleStatus = ParticleStatus.ALL;
        this.canChatColor = true;
        this.lastActionTime = SystemUtils.getMillis();
        this.seenCredits = false;
        this.requestedViewDistance = 2;
        this.language = "en_us";
        this.lastSectionPos = SectionPosition.of(0, 0, 0);
        this.chunkTrackingView = ChunkTrackingView.EMPTY;
        this.spawnExtraParticlesOnFall = false;
        this.wardenSpawnTracker = new WardenSpawnTracker();
        this.lastKnownClientMovement = Vec3D.ZERO;
        this.lastClientInput = Input.EMPTY;
        this.enderPearls = new HashSet();
        this.containerSynchronizer = new ContainerSynchronizer() {
            private final LoadingCache<TypedDataComponent<?>, Integer> cache = CacheBuilder.newBuilder().maximumSize(256L).build(new CacheLoader<TypedDataComponent<?>, Integer>() {
                private final DynamicOps<HashCode> registryHashOps;

                {
                    this.registryHashOps = EntityPlayer.this.registryAccess().<HashCode>createSerializationContext(HashOps.CRC32C_INSTANCE);
                }

                public Integer load(TypedDataComponent<?> typeddatacomponent) {
                    return ((HashCode) typeddatacomponent.encodeValue(this.registryHashOps).getOrThrow((s) -> {
                        String s1 = String.valueOf(typeddatacomponent);

                        return new IllegalArgumentException("Failed to hash " + s1 + ": " + s);
                    })).asInt();
                }
            });

            @Override
            public void sendInitialData(Container container, List<ItemStack> list, ItemStack itemstack, int[] aint) {
                EntityPlayer.this.connection.send(new PacketPlayOutWindowItems(container.containerId, container.incrementStateId(), list, itemstack));

                for (int i = 0; i < aint.length; ++i) {
                    this.broadcastDataValue(container, i, aint[i]);
                }

            }

            @Override
            public void sendSlotChange(Container container, int i, ItemStack itemstack) {
                EntityPlayer.this.connection.send(new PacketPlayOutSetSlot(container.containerId, container.incrementStateId(), i, itemstack));
            }

            @Override
            public void sendCarriedChange(Container container, ItemStack itemstack) {
                EntityPlayer.this.connection.send(new ClientboundSetCursorItemPacket(itemstack));
            }

            @Override
            public void sendDataChange(Container container, int i, int j) {
                this.broadcastDataValue(container, i, j);
            }

            private void broadcastDataValue(Container container, int i, int j) {
                EntityPlayer.this.connection.send(new PacketPlayOutWindowData(container.containerId, i, j));
            }

            @Override
            public RemoteSlot createSlot() {
                LoadingCache<TypedDataComponent<?>,Integer> loadingcache = this.cache; // CraftBukkit - decompile error

                Objects.requireNonNull(this.cache);
                return new RemoteSlot.a(loadingcache::getUnchecked);
            }
        };
        this.containerListener = new ICrafting() {
            @Override
            public void slotChanged(Container container, int i, ItemStack itemstack) {
                Slot slot = container.getSlot(i);

                if (!(slot instanceof SlotResult)) {
                    if (slot.container == EntityPlayer.this.getInventory()) {
                        CriterionTriggers.INVENTORY_CHANGED.trigger(EntityPlayer.this, EntityPlayer.this.getInventory(), itemstack);
                    }

                }
            }

            @Override
            public void dataChanged(Container container, int i, int j) {}
        };
        this.commandSource = new ICommandListener() {
            @Override
            public boolean acceptsSuccess() {
                return EntityPlayer.this.serverLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return true;
            }

            @Override
            public void sendSystemMessage(IChatBaseComponent ichatbasecomponent) {
                EntityPlayer.this.sendSystemMessage(ichatbasecomponent);
            }

            // CraftBukkit start
            @Override
            public CommandSender getBukkitSender(CommandListenerWrapper wrapper) {
                return getBukkitEntity();
            }
            // CraftBukkit end
        };
        this.textFilter = minecraftserver.createTextFilterForPlayer(this);
        this.gameMode = minecraftserver.createGameModeForPlayer(this);
        this.recipeBook = new RecipeBookServer((resourcekey, consumer) -> {
            minecraftserver.getRecipeManager().listDisplaysForRecipe(resourcekey, consumer);
        });
        this.server = minecraftserver;
        this.stats = minecraftserver.getPlayerList().getPlayerStats(this);
        this.advancements = minecraftserver.getPlayerList().getPlayerAdvancements(this);
        this.snapTo(this.adjustSpawnLocation(worldserver, worldserver.getSharedSpawnPos()).getBottomCenter(), 0.0F, 0.0F);
        this.updateOptions(clientinformation);
        this.object = null;

        // CraftBukkit start
        this.displayName = this.getScoreboardName();
        this.bukkitPickUpLoot = true;
        this.maxHealthCache = this.getMaxHealth();
    }

    // Use method to resend items in hands in case of client desync, because the item use got cancelled.
    // For example, when cancelling the leash event
    public void resendItemInHands() {
        containerMenu.findSlot(getInventory(), getInventory().getSelectedSlot()).ifPresent(s -> {
            containerSynchronizer.sendSlotChange(containerMenu, s, getMainHandItem());
        });
        containerSynchronizer.sendSlotChange(inventoryMenu, ContainerPlayer.SHIELD_SLOT, getOffhandItem());
    }

    // Yes, this doesn't match Vanilla, but it's the best we can do for now.
    // If this is an issue, PRs are welcome
    public final BlockPosition getSpawnPoint(WorldServer worldserver) {
        BlockPosition blockposition = worldserver.getSharedSpawnPos();

        if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != EnumGamemode.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = MathHelper.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPosition blockposition1 = WorldProviderNormal.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i);

                if (blockposition1 != null) {
                    return blockposition1;
                }
            }
        }

        return blockposition;
    }
    // CraftBukkit end

    @Override
    public BlockPosition adjustSpawnLocation(WorldServer worldserver, BlockPosition blockposition) {
        AxisAlignedBB axisalignedbb = this.getDimensions(EntityPose.STANDING).makeBoundingBox(Vec3D.ZERO);
        BlockPosition blockposition1 = blockposition;

        if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != EnumGamemode.ADVENTURE) { // CraftBukkit
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = MathHelper.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                int l2 = blockposition.getX() + j2 - i;
                int i3 = blockposition.getZ() + k2 - i;

                try {
                    blockposition1 = WorldProviderNormal.getOverworldRespawnPos(worldserver, l2, i3);
                    if (blockposition1 != null && this.noCollisionNoLiquid(worldserver, axisalignedbb.move(blockposition1.getBottomCenter()))) {
                        return blockposition1;
                    }
                } catch (Exception exception) {
                    CrashReport crashreport = CrashReport.forThrowable(exception, "Searching for spawn");
                    CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Spawn Lookup");

                    Objects.requireNonNull(blockposition);
                    crashreportsystemdetails.setDetail("Origin", blockposition::toString);
                    // CraftBukkit start - decompile error
                    int finalI = i;
                    crashreportsystemdetails.setDetail("Radius", () -> {
                        return Integer.toString(finalI);
                        // CraftBukkit end
                    });
                    crashreportsystemdetails.setDetail("Candidate", () -> {
                        return "[" + l2 + "," + i3 + "]";
                    });
                    // CraftBukkit start - decompile error
                    int finalL1 = l1;
                    crashreportsystemdetails.setDetail("Progress", () -> {
                        return finalL1 + " out of " + i1;
                        // CraftBukkit end
                    });
                    throw new ReportedException(crashreport);
                }
            }

            blockposition1 = blockposition;
        }

        while (!this.noCollisionNoLiquid(worldserver, axisalignedbb.move(blockposition1.getBottomCenter())) && blockposition1.getY() < worldserver.getMaxY()) {
            blockposition1 = blockposition1.above();
        }

        while (this.noCollisionNoLiquid(worldserver, axisalignedbb.move(blockposition1.below().getBottomCenter())) && blockposition1.getY() > worldserver.getMinY() + 1) {
            blockposition1 = blockposition1.below();
        }

        return blockposition1;
    }

    private boolean noCollisionNoLiquid(WorldServer worldserver, AxisAlignedBB axisalignedbb) {
        return worldserver.noCollision(this, axisalignedbb, true);
    }

    private int getCoprime(int i) {
        return i <= 16 ? i - 1 : 17;
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.wardenSpawnTracker = (WardenSpawnTracker) nbttagcompound.read("warden_spawn_tracker", WardenSpawnTracker.CODEC).orElseGet(WardenSpawnTracker::new);
        this.enteredNetherPosition = (Vec3D) nbttagcompound.read("entered_nether_pos", Vec3D.CODEC).orElse(null); // CraftBukkit - decompile error
        this.seenCredits = nbttagcompound.getBooleanOr("seenCredits", false);
        this.recipeBook.fromNbt(nbttagcompound.getCompoundOrEmpty("recipeBook"), (resourcekey) -> {
            return this.server.getRecipeManager().byKey(resourcekey).isPresent();
        });
        this.getBukkitEntity().readExtraData(nbttagcompound); // CraftBukkit
        if (this.isSleeping()) {
            this.stopSleeping();
        }

        this.respawnConfig = (EntityPlayer.RespawnConfig) nbttagcompound.read("respawn", EntityPlayer.RespawnConfig.CODEC).orElse(null); // CraftBukkit - decompile error
        // CraftBukkit start
        String spawnWorld = nbttagcompound.getStringOr("SpawnWorld", "");
        CraftWorld oldWorld = (CraftWorld) Bukkit.getWorld(spawnWorld);
        if (oldWorld != null) {
            EntityPlayer.RespawnConfig respawnConfig = this.respawnConfig;
            this.respawnConfig = new RespawnConfig(oldWorld.getHandle().dimension(), respawnConfig.pos(), respawnConfig.angle(), respawnConfig.forced());
        }
        // CraftBukkit end
        this.spawnExtraParticlesOnFall = nbttagcompound.getBooleanOr("spawn_extra_particles_on_fall", false);
        this.raidOmenPosition = (BlockPosition) nbttagcompound.read("raid_omen_position", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.store("warden_spawn_tracker", WardenSpawnTracker.CODEC, this.wardenSpawnTracker);
        this.storeGameTypes(nbttagcompound);
        nbttagcompound.putBoolean("seenCredits", this.seenCredits);
        nbttagcompound.storeNullable("entered_nether_pos", Vec3D.CODEC, this.enteredNetherPosition);
        this.saveParentVehicle(nbttagcompound);
        nbttagcompound.put("recipeBook", this.recipeBook.toNbt());
        nbttagcompound.putString("Dimension", this.level().dimension().location().toString());
        nbttagcompound.storeNullable("respawn", EntityPlayer.RespawnConfig.CODEC, this.respawnConfig);
        this.getBukkitEntity().setExtraData(nbttagcompound); // CraftBukkit
        nbttagcompound.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        nbttagcompound.storeNullable("raid_omen_position", BlockPosition.CODEC, this.raidOmenPosition);
        this.saveEnderPearls(nbttagcompound);
    }

    private void saveParentVehicle(NBTTagCompound nbttagcompound) {
        Entity entity = this.getRootVehicle();
        Entity entity1 = this.getVehicle();

        // CraftBukkit start - handle non-persistent vehicles
        boolean persistVehicle = true;
        if (entity1 != null) {
            Entity vehicle;
            for (vehicle = entity1; vehicle != null; vehicle = vehicle.getVehicle()) {
                if (!vehicle.persist) {
                    persistVehicle = false;
                    break;
                }
            }
        }

        if (persistVehicle && entity1 != null && entity != this && entity.hasExactlyOnePlayerPassenger()) {
            // CraftBukkit end
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            NBTTagCompound nbttagcompound2 = new NBTTagCompound();

            entity.save(nbttagcompound2);
            nbttagcompound1.store("Attach", UUIDUtil.CODEC, entity1.getUUID());
            nbttagcompound1.put("Entity", nbttagcompound2);
            nbttagcompound.put("RootVehicle", nbttagcompound1);
        }

    }

    public void loadAndSpawnParentVehicle(NBTTagCompound nbttagcompound) {
        Optional<NBTTagCompound> optional = nbttagcompound.getCompound("RootVehicle");

        if (!optional.isEmpty()) {
            WorldServer worldserver = this.serverLevel();
            Entity entity = EntityTypes.loadEntityRecursive(((NBTTagCompound) optional.get()).getCompoundOrEmpty("Entity"), worldserver, EntitySpawnReason.LOAD, (entity1) -> {
                return !worldserver.addWithUUID(entity1) ? null : entity1;
            });

            if (entity != null) {
                UUID uuid = (UUID) ((NBTTagCompound) optional.get()).read("Attach", UUIDUtil.CODEC).orElse(null); // CraftBukkit - decompile error

                if (entity.getUUID().equals(uuid)) {
                    this.startRiding(entity, true);
                } else {
                    for (Entity entity1 : entity.getIndirectPassengers()) {
                        if (entity1.getUUID().equals(uuid)) {
                            this.startRiding(entity1, true);
                            break;
                        }
                    }
                }

                if (!this.isPassenger()) {
                    EntityPlayer.LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause

                    for (Entity entity2 : entity.getIndirectPassengers()) {
                        entity2.discard(null); // CraftBukkit - add Bukkit remove cause
                    }
                }

            }
        }
    }

    private void saveEnderPearls(NBTTagCompound nbttagcompound) {
        if (!this.enderPearls.isEmpty()) {
            NBTTagList nbttaglist = new NBTTagList();

            for (EntityEnderPearl entityenderpearl : this.enderPearls) {
                if (entityenderpearl.isRemoved()) {
                    EntityPlayer.LOGGER.warn("Trying to save removed ender pearl, skipping");
                } else {
                    NBTTagCompound nbttagcompound1 = new NBTTagCompound();

                    entityenderpearl.save(nbttagcompound1);
                    nbttagcompound1.store("ender_pearl_dimension", World.RESOURCE_KEY_CODEC, entityenderpearl.level().dimension());
                    nbttaglist.add(nbttagcompound1);
                }
            }

            nbttagcompound.put("ender_pearls", nbttaglist);
        }

    }

    public void loadAndSpawnEnderPearls(NBTTagCompound nbttagcompound) {
        nbttagcompound.getList("ender_pearls").ifPresent((nbttaglist) -> {
            nbttaglist.compoundStream().forEach(this::loadAndSpawnEnderPearl);
        });
    }

    private void loadAndSpawnEnderPearl(NBTTagCompound nbttagcompound) {
        Optional<ResourceKey<World>> optional = nbttagcompound.read("ender_pearl_dimension", World.RESOURCE_KEY_CODEC);

        if (!optional.isEmpty()) {
            WorldServer worldserver = this.serverLevel().getServer().getLevel((ResourceKey) optional.get());

            if (worldserver != null) {
                Entity entity = EntityTypes.loadEntityRecursive(nbttagcompound, worldserver, EntitySpawnReason.LOAD, (entity1) -> {
                    return !worldserver.addWithUUID(entity1) ? null : entity1;
                });

                if (entity != null) {
                    placeEnderPearlTicket(worldserver, entity.chunkPosition());
                } else {
                    EntityPlayer.LOGGER.warn("Failed to spawn player ender pearl in level ({}), skipping", optional.get());
                }
            } else {
                EntityPlayer.LOGGER.warn("Trying to load ender pearl without level ({}) being loaded, skipping", optional.get());
            }

        }
    }

    // CraftBukkit start - World fallback code, either respawn location or global spawn
    public void spawnIn(World world, boolean flag) {
        this.setLevel(world);
        if (world == null) {
            this.unsetRemoved();
            TeleportTransition teleporttransition = this.findRespawnPositionAndUseSpawnBlock(!flag, TeleportTransition.DO_NOTHING, null);

            this.setLevel(teleporttransition.newLevel());
            this.setPos(teleporttransition.position());
        }
        this.gameMode.setLevel((WorldServer) world);
    }
    // CraftBukkit end

    public void setExperiencePoints(int i) {
        float f = (float) this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;

        this.experienceProgress = MathHelper.clamp((float) i / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int i) {
        this.experienceLevel = i;
        this.lastSentExp = -1;
    }

    @Override
    public void giveExperienceLevels(int i) {
        super.giveExperienceLevels(i);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack itemstack, int i) {
        super.onEnchantmentPerformed(itemstack, i);
        this.lastSentExp = -1;
    }

    public void initMenu(Container container) {
        container.addSlotListener(this.containerListener);
        container.setSynchronizer(this.containerSynchronizer);
        container.startOpen(); // CraftBukkit - don't force startOpen until container actually opens
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    public void onInsideBlock(IBlockData iblockdata) {
        CriterionTriggers.ENTER_BLOCK.trigger(this, iblockdata);
    }

    @Override
    protected ItemCooldown createItemCooldowns() {
        return new ItemCooldownPlayer(this);
    }

    @Override
    public void tick() {
        // CraftBukkit start
        if (this.joining) {
            this.joining = false;
        }
        // CraftBukkit end
        this.tickClientLoadTimeout();
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        if (this.invulnerableTime > 0) {
            --this.invulnerableTime;
        }

        this.containerMenu.broadcastChanges();
        if (!this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        Entity entity = this.getCamera();

        if (entity != this) {
            if (entity.isAlive()) {
                this.absSnapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                this.serverLevel().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriterionTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriterionTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this, true);
    }

    private void updatePlayerAttributes() {
        AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.BLOCK_INTERACTION_RANGE);

        if (attributemodifiable != null) {
            if (this.isCreative()) {
                attributemodifiable.addOrUpdateTransientModifier(EntityPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attributemodifiable.removeModifier(EntityPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeModifiable attributemodifiable1 = this.getAttribute(GenericAttributes.ENTITY_INTERACTION_RANGE);

        if (attributemodifiable1 != null) {
            if (this.isCreative()) {
                attributemodifiable1.addOrUpdateTransientModifier(EntityPlayer.CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attributemodifiable1.removeModifier(EntityPlayer.CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }

    }

    public void doTick() {
        try {
            if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
                super.tick();
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                ItemStack itemstack = this.getInventory().getItem(i);

                if (!itemstack.isEmpty()) {
                    this.synchronizeSpecialItemUpdates(itemstack);
                }
            }

            if (this.getHealth() != this.lastSentHealth || this.lastSentFood != this.foodData.getFoodLevel() || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new PacketPlayOutUpdateHealth(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(IScoreboardCriteria.HEALTH, MathHelper.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(IScoreboardCriteria.FOOD, MathHelper.ceil((float) this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(IScoreboardCriteria.AIR, MathHelper.ceil((float) this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(IScoreboardCriteria.ARMOR, MathHelper.ceil((float) this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(IScoreboardCriteria.EXPERIENCE, MathHelper.ceil((float) this.lastRecordedExperience));
            }

            // CraftBukkit start - Force max health updates
            if (this.maxHealthCache != this.getMaxHealth()) {
                this.getBukkitEntity().updateScaledHealth();
            }
            // CraftBukkit end

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(IScoreboardCriteria.LEVEL, MathHelper.ceil((float) this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new PacketPlayOutExperience(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriterionTriggers.LOCATION.trigger(this);
            }

            // CraftBukkit start - initialize oldLevel, fire PlayerLevelChangeEvent, and tick client-sided world border
            if (this.oldLevel == -1) {
                this.oldLevel = this.experienceLevel;
            }

            if (this.oldLevel != this.experienceLevel) {
                CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.oldLevel, this.experienceLevel);
                this.oldLevel = this.experienceLevel;
            }

            if (this.getBukkitEntity().hasClientWorldBorder()) {
                ((CraftWorldBorder) this.getBukkitEntity().getWorldBorder()).getHandle().tick();
            }
            // CraftBukkit end
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Player being ticked");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    private void synchronizeSpecialItemUpdates(ItemStack itemstack) {
        MapId mapid = (MapId) itemstack.get(DataComponents.MAP_ID);
        WorldMap worldmap = ItemWorldMap.getSavedData(mapid, this.level());

        if (worldmap != null) {
            Packet<?> packet = worldmap.getUpdatePacket(mapid, this);

            if (packet != null) {
                this.connection.send(packet);
            }
        }

    }

    @Override
    protected void tickRegeneration() {
        if (this.level().getDifficulty() == EnumDifficulty.PEACEFUL && this.serverLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            if (this.tickCount % 20 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit - added regain reason of "REGEN" for filtering purposes.
                }

                float f = this.foodData.getSaturationLevel();

                if (f < 20.0F) {
                    this.foodData.setSaturation(f + 1.0F);
                }
            }

            if (this.tickCount % 10 == 0 && this.foodData.needsFood()) {
                this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
            }
        }

    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriterionTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0D && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null && this.currentImpulseImpactPos.y <= this.startingToFallPosition.y) {
                CriterionTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }

    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriterionTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }

    }

    private void updateScoreForCriteria(IScoreboardCriteria iscoreboardcriteria, int i) {
        // CraftBukkit - Use our scores instead
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(iscoreboardcriteria, this, (scoreaccess) -> {
            scoreaccess.set(i);
        });
    }

    @Override
    public void die(DamageSource damagesource) {
        this.gameEvent(GameEvent.ENTITY_DIE);
        boolean flag = this.serverLevel().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.isRemoved()) {
            return;
        }
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<>(this.getInventory().getContainerSize());
        boolean keepInventory = this.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();

        if (!keepInventory) {
            for (ItemStack item : this.getInventory()) {
                if (!item.isEmpty() && !EnchantmentManager.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    loot.add(CraftItemStack.asCraftMirror(item).markForInventoryDrop());
                }
            }
        }
        // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
        this.dropFromLootTable(this.serverLevel(), damagesource, this.lastHurtByPlayerMemoryTime > 0);
        this.dropCustomDeathLoot(this.serverLevel(), damagesource, flag);

        loot.addAll(this.drops);
        this.drops.clear(); // SPIGOT-5188: make sure to clear

        IChatBaseComponent defaultMessage = this.getCombatTracker().getDeathMessage();

        String deathmessage = defaultMessage.getString();
        keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
        org.bukkit.event.entity.PlayerDeathEvent event = CraftEventFactory.callPlayerDeathEvent(this, damagesource, loot, deathmessage, keepInventory);

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        String deathMessage = event.getDeathMessage();

        if (deathMessage != null && deathMessage.length() > 0 && flag) { // TODO: allow plugins to override?
            IChatBaseComponent ichatbasecomponent;
            if (deathMessage.equals(deathmessage)) {
                ichatbasecomponent = this.getCombatTracker().getDeathMessage();
            } else {
                ichatbasecomponent = org.bukkit.craftbukkit.util.CraftChatMessage.fromStringOrNull(deathMessage);
            }

            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), ichatbasecomponent), PacketSendListener.exceptionallySend(() -> {
                int i = 256;
                String s = ichatbasecomponent.getString(256);
                IChatBaseComponent ichatbasecomponent1 = IChatBaseComponent.translatable("death.attack.message_too_long", IChatBaseComponent.literal(s).withStyle(EnumChatFormat.YELLOW));
                IChatBaseComponent ichatbasecomponent2 = IChatBaseComponent.translatable("death.attack.even_more_magic", this.getDisplayName()).withStyle((chatmodifier) -> {
                    return chatmodifier.withHoverEvent(new ChatHoverable.e(ichatbasecomponent1));
                });

                return new ClientboundPlayerCombatKillPacket(this.getId(), ichatbasecomponent2);
            }));
            ScoreboardTeamBase scoreboardteambase = this.getTeam();

            if (scoreboardteambase != null && scoreboardteambase.getDeathMessageVisibility() != ScoreboardTeamBase.EnumNameTagVisibility.ALWAYS) {
                if (scoreboardteambase.getDeathMessageVisibility() == ScoreboardTeamBase.EnumNameTagVisibility.HIDE_FOR_OTHER_TEAMS) {
                    this.server.getPlayerList().broadcastSystemToTeam(this, ichatbasecomponent);
                } else if (scoreboardteambase.getDeathMessageVisibility() == ScoreboardTeamBase.EnumNameTagVisibility.HIDE_FOR_OWN_TEAM) {
                    this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, ichatbasecomponent);
                }
            } else {
                this.server.getPlayerList().broadcastSystemMessage(ichatbasecomponent, false);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if (this.serverLevel().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }
        // SPIGOT-5478 must be called manually now
        this.dropExperience(this.serverLevel(), damagesource.getEntity());
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!event.getKeepInventory()) {
            this.getInventory().clearContent();
        }

        this.setCamera(this); // Remove spectated target
        // CraftBukkit end

        // CraftBukkit - Get our scores instead
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(IScoreboardCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        EntityLiving entityliving = this.getKillCredit();

        if (entityliving != null) {
            this.awardStat(StatisticList.ENTITY_KILLED_BY.get(entityliving.getType()));
            entityliving.awardKillScore(this, damagesource);
            this.createWitherRose(entityliving);
        }

        this.level().broadcastEntityEvent(this, (byte) 3);
        this.awardStat(StatisticList.DEATHS);
        this.resetStat(StatisticList.CUSTOM.get(StatisticList.TIME_SINCE_DEATH));
        this.resetStat(StatisticList.CUSTOM.get(StatisticList.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
        this.setClientLoaded(false);
    }

    private void tellNeutralMobsThatIDied() {
        AxisAlignedBB axisalignedbb = (new AxisAlignedBB(this.blockPosition())).inflate(32.0D, 10.0D, 32.0D);

        this.level().getEntitiesOfClass(EntityInsentient.class, axisalignedbb, IEntitySelector.NO_SPECTATORS).stream().filter((entityinsentient) -> {
            return entityinsentient instanceof IEntityAngerable;
        }).forEach((entityinsentient) -> {
            ((IEntityAngerable) entityinsentient).playerDied(this.serverLevel(), this);
        });
    }

    @Override
    public void awardKillScore(Entity entity, DamageSource damagesource) {
        if (entity != this) {
            super.awardKillScore(entity, damagesource);
            // CraftBukkit - Get our scores instead
            this.level().getCraftServer().getScoreboardManager().forAllObjectives(IScoreboardCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (entity instanceof EntityHuman) {
                this.awardStat(StatisticList.PLAYER_KILLS);
                // CraftBukkit - Get our scores instead
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(IScoreboardCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
            } else {
                this.awardStat(StatisticList.MOB_KILLS);
            }

            this.handleTeamKill(this, entity, IScoreboardCriteria.TEAM_KILL);
            this.handleTeamKill(entity, this, IScoreboardCriteria.KILLED_BY_TEAM);
            CriterionTriggers.PLAYER_KILLED_ENTITY.trigger(this, entity, damagesource);
        }
    }

    private void handleTeamKill(ScoreHolder scoreholder, ScoreHolder scoreholder1, IScoreboardCriteria[] aiscoreboardcriteria) {
        ScoreboardTeam scoreboardteam = this.getScoreboard().getPlayersTeam(scoreholder1.getScoreboardName());

        if (scoreboardteam != null) {
            int i = scoreboardteam.getColor().getId();

            if (i >= 0 && i < aiscoreboardcriteria.length) {
                // CraftBukkit - Get our scores instead
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(aiscoreboardcriteria[i], scoreholder, ScoreAccess::increment);
            }
        }

    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else {
            Entity entity = damagesource.getEntity();

            if (entity instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entity;

                if (!this.canHarmPlayer(entityhuman)) {
                    return false;
                }
            }

            if (entity instanceof EntityArrow) {
                EntityArrow entityarrow = (EntityArrow) entity;
                Entity entity1 = entityarrow.getOwner();

                if (entity1 instanceof EntityHuman) {
                    EntityHuman entityhuman1 = (EntityHuman) entity1;

                    if (!this.canHarmPlayer(entityhuman1)) {
                        return false;
                    }
                }
            }

            return super.hurtServer(worldserver, damagesource, f);
        }
    }

    @Override
    public boolean canHarmPlayer(EntityHuman entityhuman) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(entityhuman);
    }

    private boolean isPvpAllowed() {
        // CraftBukkit - this.server.isPvpAllowed() -> this.world.pvpMode
        return this.level().pvpMode;
    }

    // CraftBukkit start
    public TeleportTransition findRespawnPositionAndUseSpawnBlock(boolean flag, TeleportTransition.a teleporttransition_a, PlayerRespawnEvent.RespawnReason reason) {
        TeleportTransition teleportTransition;
        boolean isBedSpawn = false;
        boolean isAnchorSpawn = false;
        // CraftBukkit end
        EntityPlayer.RespawnConfig entityplayer_respawnconfig = this.getRespawnConfig();
        WorldServer worldserver = this.server.getLevel(EntityPlayer.RespawnConfig.getDimensionOrDefault(entityplayer_respawnconfig));

        if (worldserver != null && entityplayer_respawnconfig != null) {
            Optional<EntityPlayer.RespawnPosAngle> optional = findRespawnAndUseSpawnBlock(worldserver, entityplayer_respawnconfig, flag);

            if (optional.isPresent()) {
                EntityPlayer.RespawnPosAngle entityplayer_respawnposangle = (EntityPlayer.RespawnPosAngle) optional.get();

                // CraftBukkit start
                isBedSpawn = entityplayer_respawnposangle.isBedSpawn();
                isAnchorSpawn = entityplayer_respawnposangle.isAnchorSpawn();
                teleportTransition = new TeleportTransition(worldserver, entityplayer_respawnposangle.position(), Vec3D.ZERO, entityplayer_respawnposangle.yaw(), 0.0F, teleporttransition_a);
                // CraftBukkit end
            } else {
                teleportTransition = TeleportTransition.missingRespawnBlock(this.server.overworld(), this, teleporttransition_a); // CraftBukkit
            }
        } else {
            teleportTransition = new TeleportTransition(this.server.overworld(), this, teleporttransition_a); // CraftBukkit
        }
        // CraftBukkit start
        if (reason == null) {
            return teleportTransition;
        }

        Player respawnPlayer = this.getBukkitEntity();
        Location location = CraftLocation.toBukkit(teleportTransition.position(), teleportTransition.newLevel().getWorld(), teleportTransition.yRot(), teleportTransition.xRot());

        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn, isAnchorSpawn, reason);
        this.level().getCraftServer().getPluginManager().callEvent(respawnEvent);
        // Spigot Start
        if (this.connection.isDisconnected()) {
            return null;
        }
        // Spigot End

        location = respawnEvent.getRespawnLocation();

        return new TeleportTransition(((CraftWorld) location.getWorld()).getHandle(), CraftLocation.toVec3D(location), teleportTransition.deltaMovement(), location.getYaw(), location.getPitch(), teleportTransition.missingRespawnBlock(), teleportTransition.asPassenger(), teleportTransition.relatives(), teleportTransition.postTeleportTransition(), teleportTransition.cause());
        // CraftBukkit end
    }

    public static Optional<EntityPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(WorldServer worldserver, EntityPlayer.RespawnConfig entityplayer_respawnconfig, boolean flag) {
        BlockPosition blockposition = entityplayer_respawnconfig.pos;
        float f = entityplayer_respawnconfig.angle;
        boolean flag1 = entityplayer_respawnconfig.forced;
        IBlockData iblockdata = worldserver.getBlockState(blockposition);
        Block block = iblockdata.getBlock();

        if (block instanceof BlockRespawnAnchor && (flag1 || (Integer) iblockdata.getValue(BlockRespawnAnchor.CHARGE) > 0) && BlockRespawnAnchor.canSetSpawn(worldserver)) {
            Optional<Vec3D> optional = BlockRespawnAnchor.findStandUpPosition(EntityTypes.PLAYER, worldserver, blockposition);

            if (!flag1 && flag && optional.isPresent()) {
                worldserver.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockRespawnAnchor.CHARGE, (Integer) iblockdata.getValue(BlockRespawnAnchor.CHARGE) - 1), 3);
            }

            return optional.map((vec3d) -> {
                return EntityPlayer.RespawnPosAngle.of(vec3d, blockposition, false, true); // CraftBukkit
            });
        } else if (block instanceof BlockBed && BlockBed.canSetSpawn(worldserver)) {
            return BlockBed.findStandUpPosition(EntityTypes.PLAYER, worldserver, blockposition, (EnumDirection) iblockdata.getValue(BlockBed.FACING), f).map((vec3d) -> {
                return EntityPlayer.RespawnPosAngle.of(vec3d, blockposition, true, false); // CraftBukkit
            });
        } else if (!flag1) {
            return Optional.empty();
        } else {
            boolean flag2 = block.isPossibleToRespawnInThis(iblockdata);
            IBlockData iblockdata1 = worldserver.getBlockState(blockposition.above());
            boolean flag3 = iblockdata1.getBlock().isPossibleToRespawnInThis(iblockdata1);

            return flag2 && flag3 ? Optional.of(new EntityPlayer.RespawnPosAngle(new Vec3D((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.1D, (double) blockposition.getZ() + 0.5D), f, false, false)) : Optional.empty(); // CraftBukkit
        }
    }

    public void showEndCredits() {
        this.unRide();
        this.serverLevel().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.WIN_GAME, 0.0F));
            this.seenCredits = true;
        }

    }

    @Nullable
    @Override
    public EntityPlayer teleport(TeleportTransition teleporttransition) {
        if (this.isSleeping()) return null; // CraftBukkit - SPIGOT-3154
        if (this.isRemoved()) {
            return null;
        } else {
            if (teleporttransition.missingRespawnBlock()) {
                this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            WorldServer worldserver = teleporttransition.newLevel();
            WorldServer worldserver1 = this.serverLevel();
            // CraftBukkit start
            ResourceKey<WorldDimension> resourcekey = worldserver1.getTypeKey();

            Location enter = this.getBukkitEntity().getLocation();
            PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this), PositionMoveRotation.of(teleporttransition), teleporttransition.relatives());
            Location exit = (worldserver == null) ? null : CraftLocation.toBukkit(absolutePosition.position(), worldserver.getWorld(), absolutePosition.yRot(), absolutePosition.xRot());
            PlayerTeleportEvent tpEvent = new PlayerTeleportEvent(this.getBukkitEntity(), enter, exit, teleporttransition.cause());
            Bukkit.getServer().getPluginManager().callEvent(tpEvent);
            Location newExit = tpEvent.getTo();
            if (tpEvent.isCancelled() || newExit == null) {
                return null;
            }
            if (!newExit.equals(exit)) {
                worldserver = ((CraftWorld) newExit.getWorld()).getHandle();
                teleporttransition = new TeleportTransition(worldserver, CraftLocation.toVec3D(newExit), Vec3D.ZERO, newExit.getYaw(), newExit.getPitch(), teleporttransition.missingRespawnBlock(), teleporttransition.asPassenger(), Set.of(), teleporttransition.postTeleportTransition(), teleporttransition.cause());
            }
            // CraftBukkit end

            if (!teleporttransition.asPassenger()) {
                this.removeVehicle();
            }

            // CraftBukkit start
            if (worldserver != null && worldserver.dimension() == worldserver1.dimension()) {
                this.connection.internalTeleport(PositionMoveRotation.of(teleporttransition), teleporttransition.relatives());
                // CraftBukkit end
                this.connection.resetPosition();
                teleporttransition.postTeleportTransition().onTransition(this);
                return this;
            } else {
                // CraftBukkit start
                /*
                this.isChangingDimension = true;
                WorldData worlddata = worldserver.getLevelData();

                this.connection.send(new PacketPlayOutRespawn(this.createCommonSpawnInfo(worldserver), (byte) 3));
                this.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();

                playerlist.sendPlayerPermissionLevel(this);
                worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                */
                // CraftBukkit end
                GameProfilerFiller gameprofilerfiller = Profiler.get();

                gameprofilerfiller.push("moving");
                if (worldserver != null && resourcekey == WorldDimension.OVERWORLD && worldserver.getTypeKey() == WorldDimension.NETHER) { // CraftBukkit - empty to fall through to null to event
                    this.enteredNetherPosition = this.position();
                }

                gameprofilerfiller.pop();
                gameprofilerfiller.push("placing");
                // CraftBukkit start
                this.isChangingDimension = true; // CraftBukkit - Set teleport invulnerability only if player changing worlds
                WorldData worlddata = worldserver.getLevelData();

                this.connection.send(new PacketPlayOutRespawn(this.createCommonSpawnInfo(worldserver), (byte) 3));
                this.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();

                playerlist.sendPlayerPermissionLevel(this);
                worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                // CraftBukkit end
                this.setServerLevel(worldserver);
                this.connection.internalTeleport(PositionMoveRotation.of(teleporttransition), teleporttransition.relatives()); // CraftBukkit - use internal teleport without event
                this.connection.resetPosition();
                worldserver.addDuringTeleport(this);
                gameprofilerfiller.pop();
                this.triggerDimensionChangeTriggers(worldserver1);
                this.stopUsingItem();
                this.connection.send(new PacketPlayOutAbilities(this.getAbilities()));
                playerlist.sendLevelInfo(this, worldserver);
                playerlist.sendAllPlayerInfo(this);
                playerlist.sendActivePlayerEffects(this);
                teleporttransition.postTeleportTransition().onTransition(this);
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;

                // CraftBukkit start
                PlayerChangedWorldEvent changeEvent = new PlayerChangedWorldEvent(this.getBukkitEntity(), worldserver1.getWorld());
                this.level().getCraftServer().getPluginManager().callEvent(changeEvent);
                // CraftBukkit end
                return this;
            }
        }
    }

    // CraftBukkit start
    @Override
    public CraftPortalEvent callPortalEvent(Entity entity, Location exit, TeleportCause cause, int searchRadius, int creationRadius) {
        Location enter = this.getBukkitEntity().getLocation();
        PlayerPortalEvent event = new PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, searchRadius, true, creationRadius);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return null;
        }
        return new CraftPortalEvent(event);
    }
    // CraftBukkit end

    @Override
    public void forceSetRotation(float f, float f1) {
        this.connection.send(new ClientboundPlayerRotationPacket(f, f1));
    }

    public void triggerDimensionChangeTriggers(WorldServer worldserver) {
        ResourceKey<World> resourcekey = worldserver.dimension();
        ResourceKey<World> resourcekey1 = this.level().dimension();
        // CraftBukkit start
        ResourceKey<World> maindimensionkey = CraftDimensionUtil.getMainDimensionKey(worldserver);
        ResourceKey<World> maindimensionkey1 = CraftDimensionUtil.getMainDimensionKey(this.level());

        CriterionTriggers.CHANGED_DIMENSION.trigger(this, maindimensionkey, maindimensionkey1);
        if (maindimensionkey != resourcekey || maindimensionkey1 != resourcekey1) {
            CriterionTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        }

        if (maindimensionkey == World.NETHER && maindimensionkey1 == World.OVERWORLD && this.enteredNetherPosition != null) {
            // CraftBukkit end
            CriterionTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (maindimensionkey1 != World.NETHER) { // CraftBukkit
            this.enteredNetherPosition = null;
        }

    }

    @Override
    public boolean broadcastToPlayer(EntityPlayer entityplayer) {
        return entityplayer.isSpectator() ? this.getCamera() == this : (this.isSpectator() ? false : super.broadcastToPlayer(entityplayer));
    }

    @Override
    public void take(Entity entity, int i) {
        super.take(entity, i);
        this.containerMenu.broadcastChanges();
    }

    // CraftBukkit start - moved bed result checks from below into separate method
    private Either<EntityHuman.EnumBedResult, Unit> getBedResult(BlockPosition blockposition, EnumDirection enumdirection) {
        if (!this.isSleeping() && this.isAlive()) {
            if (!this.level().dimensionType().natural() || !this.level().dimensionType().bedWorks()) {
                return Either.left(EntityHuman.EnumBedResult.NOT_POSSIBLE_HERE);
            } else if (!this.bedInRange(blockposition, enumdirection)) {
                return Either.left(EntityHuman.EnumBedResult.TOO_FAR_AWAY);
            } else if (this.bedBlocked(blockposition, enumdirection)) {
                return Either.left(EntityHuman.EnumBedResult.OBSTRUCTED);
            } else {
                this.setRespawnPosition(new EntityPlayer.RespawnConfig(this.level().dimension(), blockposition, this.getYRot(), false), true, PlayerSpawnChangeEvent.Cause.BED); // CraftBukkit
                if (this.level().isBrightOutside()) {
                    return Either.left(EntityHuman.EnumBedResult.NOT_POSSIBLE_NOW);
                } else {
                    if (!this.isCreative()) {
                        double d0 = 8.0D;
                        double d1 = 5.0D;
                        Vec3D vec3d = Vec3D.atBottomCenterOf(blockposition);
                        List<EntityMonster> list = this.level().<EntityMonster>getEntitiesOfClass(EntityMonster.class, new AxisAlignedBB(vec3d.x() - 8.0D, vec3d.y() - 5.0D, vec3d.z() - 8.0D, vec3d.x() + 8.0D, vec3d.y() + 5.0D, vec3d.z() + 8.0D), (entitymonster) -> {
                            return entitymonster.isPreventingPlayerRest(this.serverLevel(), this);
                        });

                        if (!list.isEmpty()) {
                            return Either.left(EntityHuman.EnumBedResult.NOT_SAFE);
                        }
                    }

                    return Either.right(Unit.INSTANCE);
                }
            }
        } else {
            return Either.left(EntityHuman.EnumBedResult.OTHER_PROBLEM);
        }
    }

    @Override
    public Either<EntityHuman.EnumBedResult, Unit> startSleepInBed(BlockPosition blockposition, boolean force) {
        EnumDirection enumdirection = (EnumDirection) this.level().getBlockState(blockposition).getValue(BlockFacingHorizontal.FACING);
        Either<EntityHuman.EnumBedResult, Unit> bedResult = this.getBedResult(blockposition, enumdirection);

        if (bedResult.left().orElse(null) == EntityHuman.EnumBedResult.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, blockposition, bedResult);
        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        {
            {
                {
                    Either<EntityHuman.EnumBedResult, Unit> either = super.startSleepInBed(blockposition, force).ifRight((unit) -> {
                        this.awardStat(StatisticList.SLEEP_IN_BED);
                        CriterionTriggers.SLEPT_IN_BED.trigger(this);
                    });

                    if (!this.serverLevel().canSleepThroughNights()) {
                        this.displayClientMessage(IChatBaseComponent.translatable("sleep.not_possible"), true);
                    }

                    ((WorldServer) this.level()).updateSleepingPlayerList();
                    return either;
                }
            }
        }
        // CraftBukkit end
    }

    @Override
    public void startSleeping(BlockPosition blockposition) {
        this.resetStat(StatisticList.CUSTOM.get(StatisticList.TIME_SINCE_REST));
        super.startSleeping(blockposition);
    }

    private boolean bedInRange(BlockPosition blockposition, EnumDirection enumdirection) {
        return this.isReachableBedBlock(blockposition) || this.isReachableBedBlock(blockposition.relative(enumdirection.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPosition blockposition) {
        Vec3D vec3d = Vec3D.atBottomCenterOf(blockposition);

        return Math.abs(this.getX() - vec3d.x()) <= 3.0D && Math.abs(this.getY() - vec3d.y()) <= 2.0D && Math.abs(this.getZ() - vec3d.z()) <= 3.0D;
    }

    private boolean bedBlocked(BlockPosition blockposition, EnumDirection enumdirection) {
        BlockPosition blockposition1 = blockposition.above();

        return !this.freeAt(blockposition1) || !this.freeAt(blockposition1.relative(enumdirection.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean flag, boolean flag1) {
        if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
        // CraftBukkit start - fire PlayerBedLeaveEvent
        CraftPlayer player = this.getBukkitEntity();
        BlockPosition bedPosition = this.getSleepingPos().orElse(null);

        org.bukkit.block.Block bed;
        if (bedPosition != null) {
            bed = this.level().getWorld().getBlockAt(bedPosition.getX(), bedPosition.getY(), bedPosition.getZ());
        } else {
            bed = this.level().getWorld().getBlockAt(player.getLocation());
        }

        PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(player, bed, true);
        this.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new PacketPlayOutAnimation(this, 2));
        }

        super.stopSleepInBed(flag, flag1);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), TeleportCause.EXIT_BED); // CraftBukkit
        }

    }

    @Override
    public boolean isInvulnerableTo(WorldServer worldserver, DamageSource damagesource) {
        return super.isInvulnerableTo(worldserver, damagesource) || this.isChangingDimension() && !damagesource.is(DamageTypes.ENDER_PEARL) || !this.hasClientLoaded();
    }

    @Override
    protected void onChangedBlock(WorldServer worldserver, BlockPosition blockposition) {
        if (!this.isSpectator()) {
            super.onChangedBlock(worldserver, blockposition);
        }

    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {
        if (this.spawnExtraParticlesOnFall && flag && this.fallDistance > 0.0D) {
            Vec3D vec3d = blockposition.getCenter().add(0.0D, 0.5D, 0.0D);
            int i = (int) MathHelper.clamp(50.0D * this.fallDistance, 0.0D, 200.0D);

            this.serverLevel().sendParticles(new ParticleParamBlock(Particles.BLOCK, iblockdata), vec3d.x, vec3d.y, vec3d.z, i, (double) 0.3F, (double) 0.3F, (double) 0.3F, (double) 0.15F);
            this.spawnExtraParticlesOnFall = false;
        }

        super.checkFallDamage(d0, flag, iblockdata, blockposition);
    }

    @Override
    public void onExplosionHit(@Nullable Entity entity) {
        super.onExplosionHit(entity);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = entity;
        this.setIgnoreFallDamageFromCurrentImpulse(entity != null && entity.getType() == EntityTypes.WIND_CHARGE);
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }

    }

    @Override
    public void openTextEdit(TileEntitySign tileentitysign, boolean flag) {
        this.connection.send(new PacketPlayOutBlockChange(this.level(), tileentitysign.getBlockPos()));
        this.connection.send(new PacketPlayOutOpenSignEditor(tileentitysign.getBlockPos(), flag));
    }

    public int nextContainerCounter() { // CraftBukkit - void -> int
        this.containerCounter = this.containerCounter % 100 + 1;
        return containerCounter; // CraftBukkit
    }

    @Override
    public OptionalInt openMenu(@Nullable ITileInventory itileinventory) {
        if (itileinventory == null) {
            return OptionalInt.empty();
        } else {
            // CraftBukkit start - SPIGOT-6552: Handle inventory closing in CraftEventFactory#callInventoryOpenEvent(...)
            /*
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }
            */
            // CraftBukkit end

            this.nextContainerCounter();
            Container container = itileinventory.createMenu(this.containerCounter, this.getInventory(), this);

            // CraftBukkit start - Inventory open hook
            if (container != null) {
                container.setTitle(itileinventory.getDisplayName());

                boolean cancelled = false;
                container = CraftEventFactory.callInventoryOpenEvent(this, container, cancelled);
                if (container == null && !cancelled) { // Let pre-cancelled events fall through
                    // SPIGOT-5263 - close chest if cancelled
                    if (itileinventory instanceof IInventory) {
                        ((IInventory) itileinventory).stopOpen(this);
                    } else if (itileinventory instanceof BlockChest.DoubleInventory) {
                        // SPIGOT-5355 - double chests too :(
                        ((BlockChest.DoubleInventory) itileinventory).inventorylargechest.stopOpen(this);
                    }
                    return OptionalInt.empty();
                }
            }
            // CraftBukkit end
            if (container == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(IChatBaseComponent.translatable("container.spectatorCantOpen").withStyle(EnumChatFormat.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // CraftBukkit start
                this.containerMenu = container;
                this.connection.send(new PacketPlayOutOpenWindow(container.containerId, container.getType(), container.getTitle()));
                // CraftBukkit end
                this.initMenu(container);
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int i, MerchantRecipeList merchantrecipelist, int j, int k, boolean flag, boolean flag1) {
        this.connection.send(new PacketPlayOutOpenWindowMerchant(i, merchantrecipelist, j, k, flag, flag1));
    }

    @Override
    public void openHorseInventory(EntityHorseAbstract entityhorseabstract, IInventory iinventory) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounter();
        Container container = new ContainerHorse(this.containerCounter, this.getInventory(), iinventory, entityhorseabstract, entityhorseabstract.getInventoryColumns());
        container.setTitle(entityhorseabstract.getDisplayName());
        container = CraftEventFactory.callInventoryOpenEvent(this, container);

        if (container == null) {
            iinventory.stopOpen(this);
            return;
        }
        // CraftBukkit end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        // this.nextContainerCounter(); // CraftBukkit - moved up
        int i = entityhorseabstract.getInventoryColumns();

        this.connection.send(new PacketPlayOutOpenWindowHorse(this.containerCounter, i, entityhorseabstract.getId()));
        this.containerMenu = container; // CraftBukkit
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openItemGui(ItemStack itemstack, EnumHand enumhand) {
        if (itemstack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            if (WrittenBookContent.resolveForItem(itemstack, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new PacketPlayOutOpenBook(enumhand));
        }

    }

    @Override
    public void openCommandBlock(TileEntityCommand tileentitycommand) {
        this.connection.send(PacketPlayOutTileEntityData.create(tileentitycommand, TileEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        CraftEventFactory.handleInventoryCloseEvent(this); // CraftBukkit
        this.connection.send(new PacketPlayOutCloseWindow(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    public void rideTick() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.rideTick();
        this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    public void checkMovementStatistics(double d0, double d1, double d2) {
        if (!this.isPassenger() && !didNotMove(d0, d1, d2)) {
            if (this.isSwimming()) {
                int i = Math.round((float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);

                if (i > 0) {
                    this.awardStat(StatisticList.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.SWIM); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isEyeInFluid(TagsFluid.WATER)) {
                int j = Math.round((float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);

                if (j > 0) {
                    this.awardStat(StatisticList.WALK_UNDER_WATER_ONE_CM, j);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) j * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK_UNDERWATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isInWater()) {
                int k = Math.round((float) Math.sqrt(d0 * d0 + d2 * d2) * 100.0F);

                if (k > 0) {
                    this.awardStat(StatisticList.WALK_ON_WATER_ONE_CM, k);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) k * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK_ON_WATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.onClimbable()) {
                if (d1 > 0.0D) {
                    this.awardStat(StatisticList.CLIMB_ONE_CM, (int) Math.round(d1 * 100.0D));
                }
            } else if (this.onGround()) {
                int l = Math.round((float) Math.sqrt(d0 * d0 + d2 * d2) * 100.0F);

                if (l > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(StatisticList.SPRINT_ONE_CM, l);
                        this.causeFoodExhaustion(this.level().spigotConfig.sprintMultiplier * (float) l * 0.01F, EntityExhaustionEvent.ExhaustionReason.SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else if (this.isCrouching()) {
                        this.awardStat(StatisticList.CROUCH_ONE_CM, l);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) l * 0.01F, EntityExhaustionEvent.ExhaustionReason.CROUCH); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else {
                        this.awardStat(StatisticList.WALK_ONE_CM, l);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) l * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK); // CraftBukkit - EntityExhaustionEvent // Spigot
                    }
                }
            } else if (this.isFallFlying()) {
                int i1 = Math.round((float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);

                this.awardStat(StatisticList.AVIATE_ONE_CM, i1);
            } else {
                int j1 = Math.round((float) Math.sqrt(d0 * d0 + d2 * d2) * 100.0F);

                if (j1 > 25) {
                    this.awardStat(StatisticList.FLY_ONE_CM, j1);
                }
            }

        }
    }

    private void checkRidingStatistics(double d0, double d1, double d2) {
        if (this.isPassenger() && !didNotMove(d0, d1, d2)) {
            int i = Math.round((float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);
            Entity entity = this.getVehicle();

            if (entity instanceof EntityMinecartAbstract) {
                this.awardStat(StatisticList.MINECART_ONE_CM, i);
            } else if (entity instanceof AbstractBoat) {
                this.awardStat(StatisticList.BOAT_ONE_CM, i);
            } else if (entity instanceof EntityPig) {
                this.awardStat(StatisticList.PIG_ONE_CM, i);
            } else if (entity instanceof EntityHorseAbstract) {
                this.awardStat(StatisticList.HORSE_ONE_CM, i);
            } else if (entity instanceof EntityStrider) {
                this.awardStat(StatisticList.STRIDER_ONE_CM, i);
            }

        }
    }

    private static boolean didNotMove(double d0, double d1, double d2) {
        return d0 == 0.0D && d1 == 0.0D && d2 == 0.0D;
    }

    @Override
    public void awardStat(Statistic<?> statistic, int i) {
        this.stats.increment(this, statistic, i);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(statistic, this, (scoreaccess) -> {
            scoreaccess.add(i);
        });
    }

    @Override
    public void resetStat(Statistic<?> statistic) {
        this.stats.setValue(this, statistic, 0);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(statistic, this, ScoreAccess::reset); // CraftBukkit - Get our scores instead
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> collection) {
        return this.recipeBook.addRecipes(collection, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipeholder, List<ItemStack> list) {
        CriterionTriggers.RECIPE_CRAFTED.trigger(this, recipeholder.id(), list);
    }

    @Override
    public void awardRecipesByKey(List<ResourceKey<IRecipe<?>>> list) {
        List<RecipeHolder<?>> list1 = (List) list.stream().flatMap((resourcekey) -> {
            return this.server.getRecipeManager().byKey(resourcekey).stream();
        }).collect(Collectors.toList());

        this.awardRecipes(list1);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> collection) {
        return this.recipeBook.removeRecipes(collection, this);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        this.awardStat(StatisticList.JUMP);
        if (this.isSprinting()) {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpSprintExhaustion, EntityExhaustionEvent.ExhaustionReason.JUMP_SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        } else {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpWalkExhaustion, EntityExhaustionEvent.ExhaustionReason.JUMP); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        }

    }

    @Override
    public void giveExperiencePoints(int i) {
        super.giveExperiencePoints(i);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }

    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
        this.lastSentExp = -1; // CraftBukkit - Added to reset
    }

    @Override
    public void displayClientMessage(IChatBaseComponent ichatbasecomponent, boolean flag) {
        this.sendSystemMessage(ichatbasecomponent, flag);
    }

    @Override
    protected void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new PacketPlayOutEntityStatus(this, (byte) 9));
            super.completeUsingItem();
        }

    }

    @Override
    public void lookAt(ArgumentAnchor.Anchor argumentanchor_anchor, Vec3D vec3d) {
        super.lookAt(argumentanchor_anchor, vec3d);
        this.connection.send(new PacketPlayOutLookAt(argumentanchor_anchor, vec3d.x, vec3d.y, vec3d.z));
    }

    public void lookAt(ArgumentAnchor.Anchor argumentanchor_anchor, Entity entity, ArgumentAnchor.Anchor argumentanchor_anchor1) {
        Vec3D vec3d = argumentanchor_anchor1.apply(entity);

        super.lookAt(argumentanchor_anchor, vec3d);
        this.connection.send(new PacketPlayOutLookAt(argumentanchor_anchor, entity, argumentanchor_anchor1));
    }

    public void restoreFrom(EntityPlayer entityplayer, boolean flag) {
        this.wardenSpawnTracker = entityplayer.wardenSpawnTracker;
        this.chatSession = entityplayer.chatSession;
        this.gameMode.setGameModeForPlayer(entityplayer.gameMode.getGameModeForPlayer(), entityplayer.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        if (flag) {
            this.getAttributes().assignBaseValues(entityplayer.getAttributes());
            // this.getAttributes().assignPermanentModifiers(entityplayer.getAttributes()); // CraftBukkit
            this.setHealth(entityplayer.getHealth());
            this.foodData = entityplayer.foodData;

            for (MobEffect mobeffect : entityplayer.getActiveEffects()) {
                // this.addEffect(new MobEffect(mobeffect)); // CraftBukkit
            }

            this.getInventory().replaceWith(entityplayer.getInventory());
            this.experienceLevel = entityplayer.experienceLevel;
            this.totalExperience = entityplayer.totalExperience;
            this.experienceProgress = entityplayer.experienceProgress;
            this.setScore(entityplayer.getScore());
            this.portalProcess = entityplayer.portalProcess;
        } else {
            this.getAttributes().assignBaseValues(entityplayer.getAttributes());
            // this.setHealth(this.getMaxHealth()); // CraftBukkit
            if (this.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || entityplayer.isSpectator()) {
                this.getInventory().replaceWith(entityplayer.getInventory());
                this.experienceLevel = entityplayer.experienceLevel;
                this.totalExperience = entityplayer.totalExperience;
                this.experienceProgress = entityplayer.experienceProgress;
                this.setScore(entityplayer.getScore());
            }
        }

        this.enchantmentSeed = entityplayer.enchantmentSeed;
        this.enderChestInventory = entityplayer.enderChestInventory;
        this.getEntityData().set(EntityPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (Byte) entityplayer.getEntityData().get(EntityPlayer.DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        // this.recipeBook.copyOverData(entityplayer.recipeBook); // CraftBukkit
        this.seenCredits = entityplayer.seenCredits;
        this.enteredNetherPosition = entityplayer.enteredNetherPosition;
        this.chunkTrackingView = entityplayer.chunkTrackingView;
        this.setShoulderEntityLeft(entityplayer.getShoulderEntityLeft());
        this.setShoulderEntityRight(entityplayer.getShoulderEntityRight());
        this.setLastDeathLocation(entityplayer.getLastDeathLocation());
    }

    @Override
    protected void onEffectAdded(MobEffect mobeffect, @Nullable Entity entity) {
        super.onEffectAdded(mobeffect, entity);
        this.connection.send(new PacketPlayOutEntityEffect(this.getId(), mobeffect, true));
        if (mobeffect.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriterionTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectUpdated(MobEffect mobeffect, boolean flag, @Nullable Entity entity) {
        super.onEffectUpdated(mobeffect, flag, entity);
        this.connection.send(new PacketPlayOutEntityEffect(this.getId(), mobeffect, false));
        CriterionTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffect> collection) {
        super.onEffectsRemoved(collection);

        for (MobEffect mobeffect : collection) {
            this.connection.send(new PacketPlayOutRemoveEntityEffect(this.getId(), mobeffect.getEffect()));
            if (mobeffect.is(MobEffects.LEVITATION)) {
                this.levitationStartPos = null;
            }
        }

        CriterionTriggers.EFFECTS_CHANGED.trigger(this, (Entity) null);
    }

    @Override
    public void teleportTo(double d0, double d1, double d2) {
        this.connection.teleport(new PositionMoveRotation(new Vec3D(d0, d1, d2), Vec3D.ZERO, 0.0F, 0.0F), Relative.union(Relative.DELTA, Relative.ROTATION));
    }

    @Override
    public void teleportRelative(double d0, double d1, double d2) {
        this.connection.teleport(new PositionMoveRotation(new Vec3D(d0, d1, d2), Vec3D.ZERO, 0.0F, 0.0F), Relative.ALL);
    }

    @Override
    public boolean teleportTo(WorldServer worldserver, double d0, double d1, double d2, Set<Relative> set, float f, float f1, boolean flag, TeleportCause cause) { // CraftBukkit
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (flag) {
            this.setCamera(this);
        }

        boolean flag1 = super.teleportTo(worldserver, d0, d1, d2, set, f, f1, flag, cause); // CraftBukkit

        if (flag1) {
            this.setYHeadRot(set.contains(Relative.Y_ROT) ? this.getYHeadRot() + f : f);
        }

        return flag1;
    }

    @Override
    public void snapTo(double d0, double d1, double d2) {
        super.snapTo(d0, d1, d2);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity entity) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new PacketPlayOutAnimation(entity, 4));
    }

    @Override
    public void magicCrit(Entity entity) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new PacketPlayOutAnimation(entity, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new PacketPlayOutAbilities(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    public WorldServer serverLevel() {
        return (WorldServer) this.level();
    }

    public boolean setGameMode(EnumGamemode enumgamemode) {
        boolean flag = this.isSpectator();

        if (!this.gameMode.changeGameModeForPlayer(enumgamemode)) {
            return false;
        } else {
            this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.CHANGE_GAME_MODE, (float) enumgamemode.getId()));
            if (enumgamemode == EnumGamemode.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
                EnchantmentManager.stopLocationBasedEffects(this);
            } else {
                this.setCamera(this);
                if (flag) {
                    EnchantmentManager.runLocationChangedEffects(this.serverLevel(), this);
                }
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return true;
        }
    }

    @Nonnull
    @Override
    public EnumGamemode gameMode() {
        return this.gameMode.getGameModeForPlayer();
    }

    public ICommandListener commandSource() {
        return this.commandSource;
    }

    public CommandListenerWrapper createCommandSourceStack() {
        return new CommandListenerWrapper(this.commandSource(), this.position(), this.getRotationVector(), this.serverLevel(), this.getPermissionLevel(), this.getName().getString(), this.getDisplayName(), this.server, this);
    }

    public void sendSystemMessage(IChatBaseComponent ichatbasecomponent) {
        this.sendSystemMessage(ichatbasecomponent, false);
    }

    public void sendSystemMessage(IChatBaseComponent ichatbasecomponent, boolean flag) {
        if (this.acceptsSystemMessages(flag)) {
            this.connection.send(new ClientboundSystemChatPacket(ichatbasecomponent, flag), PacketSendListener.exceptionallySend(() -> {
                if (this.acceptsSystemMessages(false)) {
                    int i = 256;
                    String s = ichatbasecomponent.getString(256);
                    IChatBaseComponent ichatbasecomponent1 = IChatBaseComponent.literal(s).withStyle(EnumChatFormat.YELLOW);

                    return new ClientboundSystemChatPacket(IChatBaseComponent.translatable("multiplayer.message_not_delivered", ichatbasecomponent1).withStyle(EnumChatFormat.RED), false);
                } else {
                    return null;
                }
            }));
        }
    }

    public void sendChatMessage(OutgoingChatMessage outgoingchatmessage, boolean flag, ChatMessageType.a chatmessagetype_a) {
        if (this.acceptsChatMessages()) {
            outgoingchatmessage.sendToPlayer(this, flag, chatmessagetype_a);
        }

    }

    public String getIpAddress() {
        SocketAddress socketaddress = this.connection.getRemoteAddress();

        if (socketaddress instanceof InetSocketAddress inetsocketaddress) {
            return InetAddresses.toAddrString(inetsocketaddress.getAddress());
        } else {
            return "<unknown>";
        }
    }

    public void updateOptions(ClientInformation clientinformation) {
        // CraftBukkit start
        if (getMainArm() != clientinformation.mainHand()) {
            PlayerChangedMainHandEvent event = new PlayerChangedMainHandEvent(getBukkitEntity(), getMainArm() == EnumMainHand.LEFT ? MainHand.LEFT : MainHand.RIGHT);
            this.server.server.getPluginManager().callEvent(event);
        }
        if (!this.language.equals(clientinformation.language())) {
            PlayerLocaleChangeEvent event = new PlayerLocaleChangeEvent(getBukkitEntity(), clientinformation.language());
            this.server.server.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        this.language = clientinformation.language();
        this.requestedViewDistance = clientinformation.viewDistance();
        this.chatVisibility = clientinformation.chatVisibility();
        this.canChatColor = clientinformation.chatColors();
        this.textFilteringEnabled = clientinformation.textFilteringEnabled();
        this.allowsListing = clientinformation.allowsListing();
        this.particleStatus = clientinformation.particleStatus();
        this.getEntityData().set(EntityPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) clientinformation.modelCustomisation());
        this.getEntityData().set(EntityPlayer.DATA_PLAYER_MAIN_HAND, (byte) clientinformation.mainHand().getId());
    }

    public ClientInformation clientInformation() {
        int i = (Byte) this.getEntityData().get(EntityPlayer.DATA_PLAYER_MODE_CUSTOMISATION);
        EnumMainHand enummainhand = (EnumMainHand) EnumMainHand.BY_ID.apply((Byte) this.getEntityData().get(EntityPlayer.DATA_PLAYER_MAIN_HAND));

        return new ClientInformation(this.language, this.requestedViewDistance, this.chatVisibility, this.canChatColor, i, enummainhand, this.textFilteringEnabled, this.allowsListing, this.particleStatus);
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public EnumChatVisibility getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean flag) {
        return this.chatVisibility == EnumChatVisibility.HIDDEN ? flag : true;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == EnumChatVisibility.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerPing serverping) {
        this.connection.send(new ClientboundServerDataPacket(serverping.description(), serverping.favicon().map(ServerPing.a::iconBytes)));
    }

    @Override
    public int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = SystemUtils.getMillis();
    }

    public ServerStatisticManager getStats() {
        return this.stats;
    }

    public RecipeBookServer getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }

    }

    public Entity getCamera() {
        return (Entity) (this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity entity) {
        Entity entity1 = this.getCamera();

        this.camera = (Entity) (entity == null ? this : entity);
        if (entity1 != this.camera) {
            World world = this.camera.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                // CraftBukkit start
                boolean result = this.teleportTo(worldserver, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot(), false, TeleportCause.SPECTATE);
                if (!result) {
                    this.camera = entity1;
                    return;
                }
                // CraftBukkit end
            }

            if (entity != null) {
                this.serverLevel().getChunkSource().move(this);
            }

            this.connection.send(new PacketPlayOutCamera(this.camera));
            this.connection.resetPosition();
        }

    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }

    }

    @Override
    public void attack(Entity entity) {
        if (this.isSpectator()) {
            this.setCamera(entity);
        } else {
            super.attack(entity);
        }

    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public IChatBaseComponent getTabListDisplayName() {
        return listName; // CraftBukkit
    }

    public int getTabListOrder() {
        return listOrder; // CraftBukkit
    }

    @Override
    public void swing(EnumHand enumhand) {
        super.swing(enumhand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public AdvancementDataPlayer getAdvancements() {
        return this.advancements;
    }

    @Nullable
    public EntityPlayer.RespawnConfig getRespawnConfig() {
        return this.respawnConfig;
    }

    public void copyRespawnPosition(EntityPlayer entityplayer) {
        this.setRespawnPosition(entityplayer.respawnConfig, false);
    }

    public void setRespawnPosition(@Nullable EntityPlayer.RespawnConfig entityplayer_respawnconfig, boolean flag) {
        // CraftBukkit start
        this.setRespawnPosition(entityplayer_respawnconfig, flag, PlayerSpawnChangeEvent.Cause.UNKNOWN);
    }

    public void setRespawnPosition(@Nullable EntityPlayer.RespawnConfig entityplayer_respawnconfig, boolean flag, PlayerSpawnChangeEvent.Cause cause) {
        Location newSpawn = null;
        boolean forced = false;
        if (entityplayer_respawnconfig != null) {
            WorldServer newWorld = this.server.getLevel(entityplayer_respawnconfig.dimension());
            newSpawn = CraftLocation.toBukkit(entityplayer_respawnconfig.pos(), newWorld.getWorld(), entityplayer_respawnconfig.angle(), 0);
            forced = entityplayer_respawnconfig.forced();
        }

        PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(this.getBukkitEntity(), newSpawn, forced, cause);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        newSpawn = event.getNewSpawn();

        if (newSpawn != null) {
            entityplayer_respawnconfig = new EntityPlayer.RespawnConfig(((CraftWorld) newSpawn.getWorld()).getHandle().dimension(), BlockPosition.containing(newSpawn.getX(), newSpawn.getY(), newSpawn.getZ()), newSpawn.getYaw(), event.isForced());
        } else {
            entityplayer_respawnconfig = null;
        }
        // CraftBukkit end
        if (flag && entityplayer_respawnconfig != null && !entityplayer_respawnconfig.isSamePosition(this.respawnConfig)) {
            this.sendSystemMessage(EntityPlayer.SPAWN_SET_MESSAGE);
        }

        this.respawnConfig = entityplayer_respawnconfig;
    }

    public SectionPosition getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPosition sectionposition) {
        this.lastSectionPos = sectionposition;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunktrackingview) {
        this.chunkTrackingView = chunktrackingview;
    }

    @Override
    public void playNotifySound(SoundEffect soundeffect, SoundCategory soundcategory, float f, float f1) {
        this.connection.send(new PacketPlayOutNamedSoundEffect(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundeffect), soundcategory, this.getX(), this.getY(), this.getZ(), f, f1, this.random.nextLong()));
    }

    @Override
    // CraftBukkit start - SPIGOT-2942: Add boolean to call event
    public EntityItem drop(ItemStack itemstack, boolean flag, boolean flag1, boolean callEvent) {
        EntityItem entityitem = super.drop(itemstack, flag, flag1, callEvent);
        // CraftBukkit end

        if (flag1) {
            ItemStack itemstack1 = entityitem != null ? entityitem.getItem() : ItemStack.EMPTY;

            if (!itemstack1.isEmpty()) {
                this.awardStat(StatisticList.ITEM_DROPPED.get(itemstack1.getItem()), itemstack.getCount());
                this.awardStat(StatisticList.DROP);
            }
        }

        return entityitem;
    }

    public ITextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(WorldServer worldserver) {
        this.setLevel(worldserver);
        this.gameMode.setLevel(worldserver);
    }

    @Nullable
    private static EnumGamemode readPlayerMode(@Nullable NBTTagCompound nbttagcompound, String s) {
        return nbttagcompound != null ? (EnumGamemode) nbttagcompound.read(s, EnumGamemode.LEGACY_ID_CODEC).orElse(null) : null; // CraftBukkit - decompile error
    }

    private EnumGamemode calculateGameModeForNewPlayer(@Nullable EnumGamemode enumgamemode) {
        EnumGamemode enumgamemode1 = this.server.getForcedGameType();

        return enumgamemode1 != null ? enumgamemode1 : (enumgamemode != null ? enumgamemode : this.server.getDefaultGameType());
    }

    public void loadGameTypes(@Nullable NBTTagCompound nbttagcompound) {
        this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer(readPlayerMode(nbttagcompound, "playerGameType")), readPlayerMode(nbttagcompound, "previousPlayerGameType"));
    }

    private void storeGameTypes(NBTTagCompound nbttagcompound) {
        nbttagcompound.store("playerGameType", EnumGamemode.LEGACY_ID_CODEC, this.gameMode.getGameModeForPlayer());
        EnumGamemode enumgamemode = this.gameMode.getPreviousGameModeForPlayer();

        nbttagcompound.storeNullable("previousPlayerGameType", EnumGamemode.LEGACY_ID_CODEC, enumgamemode);
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(EntityPlayer entityplayer) {
        return entityplayer == this ? false : this.textFilteringEnabled || entityplayer.textFilteringEnabled;
    }

    @Override
    public boolean mayInteract(WorldServer worldserver, BlockPosition blockposition) {
        return super.mayInteract(worldserver, blockposition) && worldserver.mayInteract(this, blockposition);
    }

    @Override
    protected void updateUsingItem(ItemStack itemstack) {
        CriterionTriggers.USING_ITEM.trigger(this, itemstack);
        super.updateUsingItem(itemstack);
    }

    public boolean drop(boolean flag) {
        PlayerInventory playerinventory = this.getInventory();
        ItemStack itemstack = playerinventory.removeFromSelected(flag);

        this.containerMenu.findSlot(playerinventory, playerinventory.getSelectedSlot()).ifPresent((i) -> {
            this.containerMenu.setRemoteSlot(i, playerinventory.getSelectedItem());
        });
        return this.drop(itemstack, false, true) != null;
    }

    @Override
    public void handleExtraItemsCreatedOnUse(ItemStack itemstack) {
        if (!this.getInventory().add(itemstack)) {
            this.drop(itemstack, false);
        }

    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean flag) {
        this.spawnExtraParticlesOnFall = flag;
    }

    @Override
    public void onItemPickup(EntityItem entityitem) {
        super.onItemPickup(entityitem);
        Entity entity = entityitem.getOwner();

        if (entity != null) {
            CriterionTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, entityitem.getItem(), entity);
        }

    }

    public void setChatSession(RemoteChatSession remotechatsession) {
        this.chatSession = remotechatsession;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double d0, double d1) {
        this.hurtDir = (float) (MathHelper.atan2(d1, d0) * (double) (180F / (float) Math.PI) - (double) this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity entity, boolean flag) {
        if (super.startRiding(entity, flag)) {
            entity.positionRider(this);
            this.connection.teleport(new PositionMoveRotation(this.position(), Vec3D.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            if (entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                this.server.getPlayerList().sendActiveEffects(entityliving, this.connection);
            }

            this.connection.send(new PacketPlayOutMount(entity));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removeVehicle() {
        Entity entity = this.getVehicle();

        super.removeVehicle();
        if (entity instanceof EntityLiving entityliving) {
            for (MobEffect mobeffect : entityliving.getActiveEffects()) {
                this.connection.send(new PacketPlayOutRemoveEntityEffect(entity.getId(), mobeffect.getEffect()));
            }
        }

        if (entity != null) {
            this.connection.send(new PacketPlayOutMount(entity));
        }

    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(WorldServer worldserver) {
        return new CommonPlayerSpawnInfo(worldserver.dimensionTypeRegistration(), worldserver.dimension(), BiomeManager.obfuscateSeed(worldserver.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), worldserver.isDebug(), worldserver.isFlat(), this.getLastDeathLocation(), this.getPortalCooldown(), worldserver.getSeaLevel());
    }

    public void setRaidOmenPosition(BlockPosition blockposition) {
        this.raidOmenPosition = blockposition;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    @Nullable
    public BlockPosition getRaidOmenPosition() {
        return this.raidOmenPosition;
    }

    @Override
    public Vec3D getKnownMovement() {
        Entity entity = this.getVehicle();

        return entity != null && entity.getControllingPassenger() != this ? entity.getKnownMovement() : this.lastKnownClientMovement;
    }

    public void setKnownMovement(Vec3D vec3d) {
        this.lastKnownClientMovement = vec3d;
    }

    @Override
    protected float getEnchantedDamage(Entity entity, float f, DamageSource damagesource) {
        return EnchantmentManager.modifyDamage(this.serverLevel(), this.getWeaponItem(), entity, damagesource, f);
    }

    @Override
    public void onEquippedItemBroken(Item item, EnumItemSlot enumitemslot) {
        super.onEquippedItemBroken(item, enumitemslot);
        this.awardStat(StatisticList.ITEM_BROKEN.get(item));
    }

    public Input getLastClientInput() {
        return this.lastClientInput;
    }

    public void setLastClientInput(Input input) {
        this.lastClientInput = input;
    }

    public Vec3D getLastClientMoveIntent() {
        float f = this.lastClientInput.left() == this.lastClientInput.right() ? 0.0F : (this.lastClientInput.left() ? 1.0F : -1.0F);
        float f1 = this.lastClientInput.forward() == this.lastClientInput.backward() ? 0.0F : (this.lastClientInput.forward() ? 1.0F : -1.0F);

        return getInputVector(new Vec3D((double) f, 0.0D, (double) f1), 1.0F, this.getYRot());
    }

    public void registerEnderPearl(EntityEnderPearl entityenderpearl) {
        this.enderPearls.add(entityenderpearl);
    }

    public void deregisterEnderPearl(EntityEnderPearl entityenderpearl) {
        this.enderPearls.remove(entityenderpearl);
    }

    public Set<EntityEnderPearl> getEnderPearls() {
        return this.enderPearls;
    }

    public long registerAndUpdateEnderPearlTicket(EntityEnderPearl entityenderpearl) {
        World world = entityenderpearl.level();

        if (world instanceof WorldServer worldserver) {
            ChunkCoordIntPair chunkcoordintpair = entityenderpearl.chunkPosition();

            this.registerEnderPearl(entityenderpearl);
            worldserver.resetEmptyTime();
            return placeEnderPearlTicket(worldserver, chunkcoordintpair) - 1L;
        } else {
            return 0L;
        }
    }

    public static long placeEnderPearlTicket(WorldServer worldserver, ChunkCoordIntPair chunkcoordintpair) {
        worldserver.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunkcoordintpair, 2);
        return TicketType.ENDER_PEARL.timeout();
    }

    // CraftBukkit start
    public static record RespawnPosAngle(Vec3D position, float yaw, boolean isBedSpawn, boolean isAnchorSpawn) {

        public static EntityPlayer.RespawnPosAngle of(Vec3D vec3d, BlockPosition blockposition, boolean isBedSpawn, boolean isAnchorSpawn) {
            return new EntityPlayer.RespawnPosAngle(vec3d, calculateLookAtYaw(vec3d, blockposition), isBedSpawn, isAnchorSpawn);
            // CraftBukkit end
        }

        private static float calculateLookAtYaw(Vec3D vec3d, BlockPosition blockposition) {
            Vec3D vec3d1 = Vec3D.atBottomCenterOf(blockposition).subtract(vec3d).normalize();

            return (float) MathHelper.wrapDegrees(MathHelper.atan2(vec3d1.z, vec3d1.x) * (double) (180F / (float) Math.PI) - 90.0D);
        }
    }

    // CraftBukkit start - Add per-player time and weather.
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.level().getDayTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.level().getDayTime() - (this.level().getDayTime() % 24000) + this.timeOffset;
        }
    }

    public WeatherType weather = null;

    public WeatherType getPlayerWeather() {
        return this.weather;
    }

    public void setPlayerWeather(WeatherType type, boolean plugin) {
        if (!plugin && this.weather != null) {
            return;
        }

        if (plugin) {
            this.weather = type;
        }

        if (type == WeatherType.DOWNFALL) {
            this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.STOP_RAINING, 0));
        } else {
            this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0));
        }
    }

    private float pluginRainPosition;
    private float pluginRainPositionPrevious;

    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weather == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (pluginRainPositionPrevious != pluginRainPosition) {
                this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, pluginRainPosition));
            }
        }

        if (oldThunder != newThunder) {
            if (weather == WeatherType.DOWNFALL || weather == null) {
                this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, 0));
            }
        }
    }

    public void tickWeather() {
        if (this.weather == null) return;

        pluginRainPositionPrevious = pluginRainPosition;
        if (weather == WeatherType.DOWNFALL) {
            pluginRainPosition += 0.01;
        } else {
            pluginRainPosition -= 0.01;
        }

        pluginRainPosition = MathHelper.clamp(pluginRainPosition, 0.0F, 1.0F);
    }

    public void resetPlayerWeather() {
        this.weather = null;
        this.setPlayerWeather(this.level().getLevelData().isRaining() ? WeatherType.DOWNFALL : WeatherType.CLEAR, false);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    // SPIGOT-1903, MC-98153
    public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
        this.snapTo(x, y, z, yaw, pitch);
        this.connection.resetPosition();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || !getBukkitEntity().isOnline();
    }

    @Override
    public Scoreboard getScoreboard() {
        return getBukkitEntity().getScoreboard().getHandle();
    }

    public void reset() {
        float exp = 0;

        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }

        this.setHealth(this.getMaxHealth());
        this.stopUsingItem(); // CraftBukkit - SPIGOT-6682: Clear active item on reset
        this.setRemainingFireTicks(0);
        this.fallDistance = 0;
        this.foodData = new FoodMetaData();
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0;
        this.deathTime = 0;
        this.setArrowCount(0, true); // CraftBukkit - ArrowBodyCountChangeEvent
        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new CombatTracker(this);
        this.lastSentExp = -1;
        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
        this.setDeltaMovement(0, 0, 0); // CraftBukkit - SPIGOT-6948: Reset velocity on death
        this.skipDropExperience = false; // CraftBukkit - SPIGOT-7462: Reset experience drop skip, so that further deaths drop xp
    }

    @Override
    public CraftPlayer getBukkitEntity() {
        return (CraftPlayer) super.getBukkitEntity();
    }
    // CraftBukkit end

    public static record RespawnConfig(ResourceKey<World> dimension, BlockPosition pos, float angle, boolean forced) {

        public static final Codec<EntityPlayer.RespawnConfig> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(World.RESOURCE_KEY_CODEC.optionalFieldOf("dimension", World.OVERWORLD).forGetter(EntityPlayer.RespawnConfig::dimension), BlockPosition.CODEC.fieldOf("pos").forGetter(EntityPlayer.RespawnConfig::pos), Codec.FLOAT.optionalFieldOf("angle", 0.0F).forGetter(EntityPlayer.RespawnConfig::angle), Codec.BOOL.optionalFieldOf("forced", false).forGetter(EntityPlayer.RespawnConfig::forced)).apply(instance, EntityPlayer.RespawnConfig::new);
        });

        static ResourceKey<World> getDimensionOrDefault(@Nullable EntityPlayer.RespawnConfig entityplayer_respawnconfig) {
            return entityplayer_respawnconfig != null ? entityplayer_respawnconfig.dimension() : World.OVERWORLD;
        }

        public boolean isSamePosition(@Nullable EntityPlayer.RespawnConfig entityplayer_respawnconfig) {
            return entityplayer_respawnconfig != null && this.dimension == entityplayer_respawnconfig.dimension && this.pos.equals(entityplayer_respawnconfig.pos);
        }
    }
}
