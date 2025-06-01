package net.minecraft.world.level;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicLike;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutEntityStatus;
import net.minecraft.network.protocol.game.PacketPlayOutGameStateChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.slf4j.Logger;

public class GameRules {

    public static final int DEFAULT_RANDOM_TICK_SPEED = 3;
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<GameRules.GameRuleKey<?>, GameRules.GameRuleDefinition<?>> GAME_RULE_TYPES = Maps.newTreeMap(Comparator.comparing((gamerules_gamerulekey) -> {
        return gamerules_gamerulekey.id;
    }));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DOFIRETICK = register("doFireTick", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_ALLOWFIRETICKAWAYFROMPLAYERS = register("allowFireTicksAwayFromPlayer", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_MOBGRIEFING = register("mobGriefing", GameRules.GameRuleCategory.MOBS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_KEEPINVENTORY = register("keepInventory", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DOMOBSPAWNING = register("doMobSpawning", GameRules.GameRuleCategory.SPAWNING, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DOMOBLOOT = register("doMobLoot", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_PROJECTILESCANBREAKBLOCKS = register("projectilesCanBreakBlocks", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DOBLOCKDROPS = register("doTileDrops", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DOENTITYDROPS = register("doEntityDrops", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_COMMANDBLOCKOUTPUT = register("commandBlockOutput", GameRules.GameRuleCategory.CHAT, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_NATURAL_REGENERATION = register("naturalRegeneration", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DAYLIGHT = register("doDaylightCycle", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_LOGADMINCOMMANDS = register("logAdminCommands", GameRules.GameRuleCategory.CHAT, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_SHOWDEATHMESSAGES = register("showDeathMessages", GameRules.GameRuleCategory.CHAT, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_RANDOMTICKING = register("randomTickSpeed", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleInt.create(3));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_SENDCOMMANDFEEDBACK = register("sendCommandFeedback", GameRules.GameRuleCategory.CHAT, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_REDUCEDDEBUGINFO = register("reducedDebugInfo", GameRules.GameRuleCategory.MISC, GameRules.GameRuleBoolean.create(false, (minecraftserver, gamerules_gameruleboolean) -> {
        byte b0 = (byte) (gamerules_gameruleboolean.get() ? 22 : 23);

        for (EntityPlayer entityplayer : minecraftserver.players()) { // CraftBukkit - per-world
            entityplayer.connection.send(new PacketPlayOutEntityStatus(entityplayer, b0));
        }

    }));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_SPECTATORSGENERATECHUNKS = register("spectatorsGenerateChunks", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_SPAWN_RADIUS = register("spawnRadius", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleInt.create(10));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DISABLE_PLAYER_MOVEMENT_CHECK = register("disablePlayerMovementCheck", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DISABLE_ELYTRA_MOVEMENT_CHECK = register("disableElytraMovementCheck", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_MAX_ENTITY_CRAMMING = register("maxEntityCramming", GameRules.GameRuleCategory.MOBS, GameRules.GameRuleInt.create(24));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_WEATHER_CYCLE = register("doWeatherCycle", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_LIMITED_CRAFTING = register("doLimitedCrafting", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(false, (minecraftserver, gamerules_gameruleboolean) -> {
        for (EntityPlayer entityplayer : minecraftserver.players()) { // CraftBukkit - per-world
            entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.LIMITED_CRAFTING, gamerules_gameruleboolean.get() ? 1.0F : 0.0F));
        }

    }));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_MAX_COMMAND_CHAIN_LENGTH = register("maxCommandChainLength", GameRules.GameRuleCategory.MISC, GameRules.GameRuleInt.create(65536));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_MAX_COMMAND_FORK_COUNT = register("maxCommandForkCount", GameRules.GameRuleCategory.MISC, GameRules.GameRuleInt.create(65536));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_COMMAND_MODIFICATION_BLOCK_LIMIT = register("commandModificationBlockLimit", GameRules.GameRuleCategory.MISC, GameRules.GameRuleInt.create(32768));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_ANNOUNCE_ADVANCEMENTS = register("announceAdvancements", GameRules.GameRuleCategory.CHAT, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DISABLE_RAIDS = register("disableRaids", GameRules.GameRuleCategory.MOBS, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DOINSOMNIA = register("doInsomnia", GameRules.GameRuleCategory.SPAWNING, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DO_IMMEDIATE_RESPAWN = register("doImmediateRespawn", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(false, (minecraftserver, gamerules_gameruleboolean) -> {
        for (EntityPlayer entityplayer : minecraftserver.players()) { // CraftBukkit - per-world
            entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.IMMEDIATE_RESPAWN, gamerules_gameruleboolean.get() ? 1.0F : 0.0F));
        }

    }));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY = register("playersNetherPortalDefaultDelay", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleInt.create(80));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY = register("playersNetherPortalCreativeDelay", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleInt.create(0));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DROWNING_DAMAGE = register("drowningDamage", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_FALL_DAMAGE = register("fallDamage", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_FIRE_DAMAGE = register("fireDamage", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_FREEZE_DAMAGE = register("freezeDamage", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DO_PATROL_SPAWNING = register("doPatrolSpawning", GameRules.GameRuleCategory.SPAWNING, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DO_TRADER_SPAWNING = register("doTraderSpawning", GameRules.GameRuleCategory.SPAWNING, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DO_WARDEN_SPAWNING = register("doWardenSpawning", GameRules.GameRuleCategory.SPAWNING, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_FORGIVE_DEAD_PLAYERS = register("forgiveDeadPlayers", GameRules.GameRuleCategory.MOBS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_UNIVERSAL_ANGER = register("universalAnger", GameRules.GameRuleCategory.MOBS, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_PLAYERS_SLEEPING_PERCENTAGE = register("playersSleepingPercentage", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleInt.create(100));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_BLOCK_EXPLOSION_DROP_DECAY = register("blockExplosionDropDecay", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_MOB_EXPLOSION_DROP_DECAY = register("mobExplosionDropDecay", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_TNT_EXPLOSION_DROP_DECAY = register("tntExplosionDropDecay", GameRules.GameRuleCategory.DROPS, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_SNOW_ACCUMULATION_HEIGHT = register("snowAccumulationHeight", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleInt.create(1));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_WATER_SOURCE_CONVERSION = register("waterSourceConversion", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_LAVA_SOURCE_CONVERSION = register("lavaSourceConversion", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(false));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_GLOBAL_SOUND_EVENTS = register("globalSoundEvents", GameRules.GameRuleCategory.MISC, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_DO_VINES_SPREAD = register("doVinesSpread", GameRules.GameRuleCategory.UPDATES, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_ENDER_PEARLS_VANISH_ON_DEATH = register("enderPearlsVanishOnDeath", GameRules.GameRuleCategory.PLAYER, GameRules.GameRuleBoolean.create(true));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_MINECART_MAX_SPEED = register("minecartMaxSpeed", GameRules.GameRuleCategory.MISC, GameRules.GameRuleInt.create(8, 1, 1000, FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS), (minecraftserver, gamerules_gameruleint) -> {
    }));
    public static final GameRules.GameRuleKey<GameRules.GameRuleInt> RULE_SPAWN_CHUNK_RADIUS = register("spawnChunkRadius", GameRules.GameRuleCategory.MISC, GameRules.GameRuleInt.create(2, 0, 32, FeatureFlagSet.of(), (minecraftserver, gamerules_gameruleint) -> {
        WorldServer worldserver = minecraftserver; // CraftBukkit - per-world

        worldserver.setDefaultSpawnPos(worldserver.getSharedSpawnPos(), worldserver.getSharedSpawnAngle());
    }));
    public static final GameRules.GameRuleKey<GameRules.GameRuleBoolean> RULE_TNT_EXPLODES = register("tntExplodes", GameRules.GameRuleCategory.MISC, GameRules.GameRuleBoolean.create(true));
    private final Map<GameRules.GameRuleKey<?>, GameRules.GameRuleValue<?>> rules;
    private final FeatureFlagSet enabledFeatures;

    public static <T extends GameRules.GameRuleValue<T>> GameRules.GameRuleDefinition<T> getType(GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        return (GameRules.GameRuleDefinition) GameRules.GAME_RULE_TYPES.get(gamerules_gamerulekey);
    }

    public static <T extends GameRules.GameRuleValue<T>> Codec<GameRules.GameRuleKey<T>> keyCodec(Class<T> oclass) {
        return Codec.STRING.comapFlatMap((s) -> {
            return GameRules.GAME_RULE_TYPES.entrySet().stream().filter((entry) -> { // CraftBukkit - decompile error
                return ((GameRules.GameRuleDefinition) entry.getValue()).valueClass == oclass;
            }).map(Entry::getKey).filter((gamerules_gamerulekey) -> {
                return gamerules_gamerulekey.getId().equals(s);
            }).map((gamerules_gamerulekey) -> {
                return (GameRules.GameRuleKey<T>) gamerules_gamerulekey; // CraftBukkit - decompile error
            }).findFirst().map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "Invalid game rule ID for type: " + s;
                });
            });
        }, GameRules.GameRuleKey::getId);
    }

    private static <T extends GameRules.GameRuleValue<T>> GameRules.GameRuleKey<T> register(String s, GameRules.GameRuleCategory gamerules_gamerulecategory, GameRules.GameRuleDefinition<T> gamerules_gameruledefinition) {
        GameRules.GameRuleKey<T> gamerules_gamerulekey = new GameRules.GameRuleKey<T>(s, gamerules_gamerulecategory);
        GameRules.GameRuleDefinition<?> gamerules_gameruledefinition1 = (GameRules.GameRuleDefinition) GameRules.GAME_RULE_TYPES.put(gamerules_gamerulekey, gamerules_gameruledefinition);

        if (gamerules_gameruledefinition1 != null) {
            throw new IllegalStateException("Duplicate game rule registration for " + s);
        } else {
            return gamerules_gamerulekey;
        }
    }

    public GameRules(FeatureFlagSet featureflagset, DynamicLike<?> dynamiclike) {
        this(featureflagset);
        this.loadFromTag(dynamiclike);
    }

    public GameRules(FeatureFlagSet featureflagset) {
        this((Map) availableRules(featureflagset).collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
            return ((GameRules.GameRuleDefinition) entry.getValue()).createRule();
        })), featureflagset);
    }

    private static Stream<Map.Entry<GameRules.GameRuleKey<?>, GameRules.GameRuleDefinition<?>>> availableRules(FeatureFlagSet featureflagset) {
        return GameRules.GAME_RULE_TYPES.entrySet().stream().filter((entry) -> {
            return ((GameRules.GameRuleDefinition) entry.getValue()).requiredFeatures.isSubsetOf(featureflagset);
        });
    }

    private GameRules(Map<GameRules.GameRuleKey<?>, GameRules.GameRuleValue<?>> map, FeatureFlagSet featureflagset) {
        this.rules = map;
        this.enabledFeatures = featureflagset;
    }

    public <T extends GameRules.GameRuleValue<T>> T getRule(GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        T t0 = (T) (this.rules.get(gamerules_gamerulekey));

        if (t0 == null) {
            throw new IllegalArgumentException("Tried to access invalid game rule");
        } else {
            return t0;
        }
    }

    public NBTTagCompound createTag() {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        this.rules.forEach((gamerules_gamerulekey, gamerules_gamerulevalue) -> {
            nbttagcompound.putString(gamerules_gamerulekey.id, gamerules_gamerulevalue.serialize());
        });
        return nbttagcompound;
    }

    private void loadFromTag(DynamicLike<?> dynamiclike) {
        this.rules.forEach((gamerules_gamerulekey, gamerules_gamerulevalue) -> {
            DataResult<String> dataresult = dynamiclike.get(gamerules_gamerulekey.id).asString(); // CraftBukkit - decompile error

            Objects.requireNonNull(gamerules_gamerulevalue);
            dataresult.ifSuccess(gamerules_gamerulevalue::deserialize);
        });
    }

    public GameRules copy(FeatureFlagSet featureflagset) {
        return new GameRules((Map) availableRules(featureflagset).collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
            return this.rules.containsKey(entry.getKey()) ? (GameRules.GameRuleValue) this.rules.get(entry.getKey()) : ((GameRules.GameRuleDefinition) entry.getValue()).createRule();
        })), featureflagset);
    }

    public void visitGameRuleTypes(GameRules.GameRuleVisitor gamerules_gamerulevisitor) {
        GameRules.GAME_RULE_TYPES.forEach((gamerules_gamerulekey, gamerules_gameruledefinition) -> {
            this.callVisitorCap(gamerules_gamerulevisitor, gamerules_gamerulekey, gamerules_gameruledefinition);
        });
    }

    private <T extends GameRules.GameRuleValue<T>> void callVisitorCap(GameRules.GameRuleVisitor gamerules_gamerulevisitor, GameRules.GameRuleKey<?> gamerules_gamerulekey, GameRules.GameRuleDefinition<?> gamerules_gameruledefinition) {
        if (gamerules_gameruledefinition.requiredFeatures.isSubsetOf(this.enabledFeatures)) {
            gamerules_gamerulevisitor.visit((GameRules.GameRuleKey<T>) gamerules_gamerulekey, (GameRules.GameRuleDefinition<T>) gamerules_gameruledefinition); // CraftBukkit - decompile error
            ((GameRules.GameRuleDefinition<T>) gamerules_gameruledefinition).callVisitor(gamerules_gamerulevisitor, (GameRules.GameRuleKey<T>) gamerules_gamerulekey); // CraftBukkit - decompile error
        }

    }

    public void assignFrom(GameRules gamerules, @Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
        gamerules.rules.keySet().forEach((gamerules_gamerulekey) -> {
            this.assignCap(gamerules_gamerulekey, gamerules, minecraftserver);
        });
    }

    private <T extends GameRules.GameRuleValue<T>> void assignCap(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules gamerules, @Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
        T t0 = gamerules.getRule(gamerules_gamerulekey);

        this.getRule(gamerules_gamerulekey).setFrom(t0, minecraftserver);
    }

    public boolean getBoolean(GameRules.GameRuleKey<GameRules.GameRuleBoolean> gamerules_gamerulekey) {
        return ((GameRules.GameRuleBoolean) this.getRule(gamerules_gamerulekey)).get();
    }

    public int getInt(GameRules.GameRuleKey<GameRules.GameRuleInt> gamerules_gamerulekey) {
        return ((GameRules.GameRuleInt) this.getRule(gamerules_gamerulekey)).get();
    }

    public static enum GameRuleCategory {

        PLAYER("gamerule.category.player"), MOBS("gamerule.category.mobs"), SPAWNING("gamerule.category.spawning"), DROPS("gamerule.category.drops"), UPDATES("gamerule.category.updates"), CHAT("gamerule.category.chat"), MISC("gamerule.category.misc");

        private final String descriptionId;

        private GameRuleCategory(final String s) {
            this.descriptionId = s;
        }

        public String getDescriptionId() {
            return this.descriptionId;
        }
    }

    public interface GameRuleVisitor {

        default <T extends GameRules.GameRuleValue<T>> void visit(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.GameRuleDefinition<T> gamerules_gameruledefinition) {}

        default void visitBoolean(GameRules.GameRuleKey<GameRules.GameRuleBoolean> gamerules_gamerulekey, GameRules.GameRuleDefinition<GameRules.GameRuleBoolean> gamerules_gameruledefinition) {}

        default void visitInteger(GameRules.GameRuleKey<GameRules.GameRuleInt> gamerules_gamerulekey, GameRules.GameRuleDefinition<GameRules.GameRuleInt> gamerules_gameruledefinition) {}
    }

    public static final class GameRuleKey<T extends GameRules.GameRuleValue<T>> {

        final String id;
        private final GameRules.GameRuleCategory category;

        public GameRuleKey(String s, GameRules.GameRuleCategory gamerules_gamerulecategory) {
            this.id = s;
            this.category = gamerules_gamerulecategory;
        }

        public String toString() {
            return this.id;
        }

        public boolean equals(Object object) {
            return this == object ? true : object instanceof GameRules.GameRuleKey && ((GameRules.GameRuleKey) object).id.equals(this.id);
        }

        public int hashCode() {
            return this.id.hashCode();
        }

        public String getId() {
            return this.id;
        }

        public String getDescriptionId() {
            return "gamerule." + this.id;
        }

        public GameRules.GameRuleCategory getCategory() {
            return this.category;
        }
    }

    public static class GameRuleDefinition<T extends GameRules.GameRuleValue<T>> {

        final Supplier<ArgumentType<?>> argument;
        private final Function<GameRules.GameRuleDefinition<T>, T> constructor;
        final BiConsumer<WorldServer, T> callback; // CraftBukkit - per-world
        private final GameRules.h<T> visitorCaller;
        final Class<T> valueClass;
        final FeatureFlagSet requiredFeatures;

        GameRuleDefinition(Supplier<ArgumentType<?>> supplier, Function<GameRules.GameRuleDefinition<T>, T> function, BiConsumer<WorldServer, T> biconsumer, GameRules.h<T> gamerules_h, Class<T> oclass, FeatureFlagSet featureflagset) { // CraftBukkit - per-world
            this.argument = supplier;
            this.constructor = function;
            this.callback = biconsumer;
            this.visitorCaller = gamerules_h;
            this.valueClass = oclass;
            this.requiredFeatures = featureflagset;
        }

        public RequiredArgumentBuilder<CommandListenerWrapper, ?> createArgument(String s) {
            return CommandDispatcher.argument(s, (ArgumentType) this.argument.get());
        }

        public T createRule() {
            return (T) (this.constructor.apply(this));
        }

        public void callVisitor(GameRules.GameRuleVisitor gamerules_gamerulevisitor, GameRules.GameRuleKey<T> gamerules_gamerulekey) {
            this.visitorCaller.call(gamerules_gamerulevisitor, gamerules_gamerulekey, this);
        }

        public FeatureFlagSet requiredFeatures() {
            return this.requiredFeatures;
        }
    }

    public abstract static class GameRuleValue<T extends GameRules.GameRuleValue<T>> {

        protected final GameRules.GameRuleDefinition<T> type;

        public GameRuleValue(GameRules.GameRuleDefinition<T> gamerules_gameruledefinition) {
            this.type = gamerules_gameruledefinition;
        }

        protected abstract void updateFromArgument(CommandContext<CommandListenerWrapper> commandcontext, String s);

        public void setFromArgument(CommandContext<CommandListenerWrapper> commandcontext, String s) {
            this.updateFromArgument(commandcontext, s);
            this.onChanged(((CommandListenerWrapper) commandcontext.getSource()).getLevel()); // CraftBukkit - per-world
        }

        public void onChanged(@Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
            if (minecraftserver != null) {
                this.type.callback.accept(minecraftserver, this.getSelf());
            }

        }

        public abstract void deserialize(String s); // PAIL - private->public

        public abstract String serialize();

        public String toString() {
            return this.serialize();
        }

        public abstract int getCommandResult();

        protected abstract T getSelf();

        protected abstract T copy();

        public abstract void setFrom(T t0, @Nullable WorldServer minecraftserver); // CraftBukkit - per-world
    }

    public static class GameRuleInt extends GameRules.GameRuleValue<GameRules.GameRuleInt> {

        private int value;

        private static GameRules.GameRuleDefinition<GameRules.GameRuleInt> create(int i, BiConsumer<WorldServer, GameRules.GameRuleInt> biconsumer) { // CraftBukkit - per-world
            return new GameRules.GameRuleDefinition<GameRules.GameRuleInt>(IntegerArgumentType::integer, (gamerules_gameruledefinition) -> {
                return new GameRules.GameRuleInt(gamerules_gameruledefinition, i);
            }, biconsumer, GameRules.GameRuleVisitor::visitInteger, GameRules.GameRuleInt.class, FeatureFlagSet.of());
        }

        static GameRules.GameRuleDefinition<GameRules.GameRuleInt> create(int i, int j, int k, FeatureFlagSet featureflagset, BiConsumer<WorldServer, GameRules.GameRuleInt> biconsumer) { // CraftBukkit - per-world
            return new GameRules.GameRuleDefinition<GameRules.GameRuleInt>(() -> {
                return IntegerArgumentType.integer(j, k);
            }, (gamerules_gameruledefinition) -> {
                return new GameRules.GameRuleInt(gamerules_gameruledefinition, i);
            }, biconsumer, GameRules.GameRuleVisitor::visitInteger, GameRules.GameRuleInt.class, featureflagset);
        }

        static GameRules.GameRuleDefinition<GameRules.GameRuleInt> create(int i) {
            return create(i, (minecraftserver, gamerules_gameruleint) -> {
            });
        }

        public GameRuleInt(GameRules.GameRuleDefinition<GameRules.GameRuleInt> gamerules_gameruledefinition, int i) {
            super(gamerules_gameruledefinition);
            this.value = i;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandListenerWrapper> commandcontext, String s) {
            this.value = IntegerArgumentType.getInteger(commandcontext, s);
        }

        public int get() {
            return this.value;
        }

        public void set(int i, @Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
            this.value = i;
            this.onChanged(minecraftserver);
        }

        @Override
        public String serialize() {
            return Integer.toString(this.value);
        }

        @Override
        public void deserialize(String s) { // PAIL - protected->public
            this.value = safeParse(s);
        }

        public boolean tryDeserialize(String s) {
            try {
                StringReader stringreader = new StringReader(s);

                this.value = (Integer) ((ArgumentType) this.type.argument.get()).parse(stringreader);
                return !stringreader.canRead();
            } catch (CommandSyntaxException commandsyntaxexception) {
                return false;
            }
        }

        private static int safeParse(String s) {
            if (!s.isEmpty()) {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException numberformatexception) {
                    GameRules.LOGGER.warn("Failed to parse integer {}", s);
                }
            }

            return 0;
        }

        @Override
        public int getCommandResult() {
            return this.value;
        }

        @Override
        protected GameRules.GameRuleInt getSelf() {
            return this;
        }

        @Override
        protected GameRules.GameRuleInt copy() {
            return new GameRules.GameRuleInt(this.type, this.value);
        }

        public void setFrom(GameRules.GameRuleInt gamerules_gameruleint, @Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
            this.value = gamerules_gameruleint.value;
            this.onChanged(minecraftserver);
        }
    }

    public static class GameRuleBoolean extends GameRules.GameRuleValue<GameRules.GameRuleBoolean> {

        private boolean value;

        static GameRules.GameRuleDefinition<GameRules.GameRuleBoolean> create(boolean flag, BiConsumer<WorldServer, GameRules.GameRuleBoolean> biconsumer) { // CraftBukkit - per-world
            return new GameRules.GameRuleDefinition<GameRules.GameRuleBoolean>(BoolArgumentType::bool, (gamerules_gameruledefinition) -> {
                return new GameRules.GameRuleBoolean(gamerules_gameruledefinition, flag);
            }, biconsumer, GameRules.GameRuleVisitor::visitBoolean, GameRules.GameRuleBoolean.class, FeatureFlagSet.of());
        }

        static GameRules.GameRuleDefinition<GameRules.GameRuleBoolean> create(boolean flag) {
            return create(flag, (minecraftserver, gamerules_gameruleboolean) -> {
            });
        }

        public GameRuleBoolean(GameRules.GameRuleDefinition<GameRules.GameRuleBoolean> gamerules_gameruledefinition, boolean flag) {
            super(gamerules_gameruledefinition);
            this.value = flag;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandListenerWrapper> commandcontext, String s) {
            this.value = BoolArgumentType.getBool(commandcontext, s);
        }

        public boolean get() {
            return this.value;
        }

        public void set(boolean flag, @Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
            this.value = flag;
            this.onChanged(minecraftserver);
        }

        @Override
        public String serialize() {
            return Boolean.toString(this.value);
        }

        @Override
        public void deserialize(String s) { // PAIL - protected->public
            this.value = Boolean.parseBoolean(s);
        }

        @Override
        public int getCommandResult() {
            return this.value ? 1 : 0;
        }

        @Override
        protected GameRules.GameRuleBoolean getSelf() {
            return this;
        }

        @Override
        protected GameRules.GameRuleBoolean copy() {
            return new GameRules.GameRuleBoolean(this.type, this.value);
        }

        public void setFrom(GameRules.GameRuleBoolean gamerules_gameruleboolean, @Nullable WorldServer minecraftserver) { // CraftBukkit - per-world
            this.value = gamerules_gameruleboolean.value;
            this.onChanged(minecraftserver);
        }
    }

    private interface h<T extends GameRules.GameRuleValue<T>> {

        void call(GameRules.GameRuleVisitor gamerules_gamerulevisitor, GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.GameRuleDefinition<T> gamerules_gameruledefinition);
    }
}
