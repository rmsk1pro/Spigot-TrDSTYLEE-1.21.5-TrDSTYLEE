package org.bukkit.craftbukkit.block;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.level.MobSpawnerAbstract;
import net.minecraft.world.level.MobSpawnerData;
import net.minecraft.world.level.block.entity.TileEntityMobSpawner;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.entity.CraftEntitySnapshot;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;

public class CraftCreatureSpawner extends CraftBlockEntityState<TileEntityMobSpawner> implements CreatureSpawner {

    public CraftCreatureSpawner(World world, TileEntityMobSpawner tileEntity) {
        super(world, tileEntity);
    }

    protected CraftCreatureSpawner(CraftCreatureSpawner state, Location location) {
        super(state, location);
    }

    @Override
    public EntityType getSpawnedType() {
        MobSpawnerData spawnData = this.getSnapshot().getSpawner().nextSpawnData;
        if (spawnData == null) {
            return null;
        }

        Optional<EntityTypes<?>> type = EntityTypes.by(spawnData.getEntityToSpawn());
        return type.map(CraftEntityType::minecraftToBukkit).orElse(null);
    }

    @Override
    public void setSpawnedType(EntityType entityType) {
        if (entityType == null) {
            this.getSnapshot().getSpawner().spawnPotentials = WeightedList.of(); // need clear the spawnPotentials to avoid nextSpawnData being replaced later
            this.getSnapshot().getSpawner().nextSpawnData = new MobSpawnerData();
            return;
        }
        Preconditions.checkArgument(entityType != EntityType.UNKNOWN, "Can't spawn EntityType %s from mob spawners!", entityType);

        RandomSource rand = (this.isPlaced()) ? this.getWorldHandle().getRandom() : RandomSource.create();
        this.getSnapshot().setEntityId(CraftEntityType.bukkitToMinecraft(entityType), rand);
    }

    @Override
    public EntitySnapshot getSpawnedEntity() {
        MobSpawnerData spawnData = this.getSnapshot().getSpawner().nextSpawnData;
        if (spawnData == null) {
            return null;
        }

        return CraftEntitySnapshot.create(spawnData.getEntityToSpawn());
    }

    @Override
    public void setSpawnedEntity(EntitySnapshot snapshot) {
        setSpawnedEntity(this.getSnapshot().getSpawner(), snapshot, null, null);
    }

    @Override
    public void setSpawnedEntity(SpawnerEntry spawnerEntry) {
        Preconditions.checkArgument(spawnerEntry != null, "Entry cannot be null");

        setSpawnedEntity(this.getSnapshot().getSpawner(), spawnerEntry.getSnapshot(), spawnerEntry.getSpawnRule(), spawnerEntry.getEquipment());
    }

    public static void setSpawnedEntity(MobSpawnerAbstract spawner, EntitySnapshot snapshot, SpawnRule spawnRule, SpawnerEntry.Equipment equipment) {
        spawner.spawnPotentials = WeightedList.of(); // need clear the spawnPotentials to avoid nextSpawnData being replaced later

        if (snapshot == null) {
            spawner.nextSpawnData = new MobSpawnerData();
            return;
        }
        NBTTagCompound compoundTag = ((CraftEntitySnapshot) snapshot).getData();

        spawner.nextSpawnData = new MobSpawnerData(compoundTag, Optional.ofNullable(toMinecraftRule(spawnRule)), getEquipment(equipment));
    }

    @Override
    public void addPotentialSpawn(EntitySnapshot snapshot, int weight, SpawnRule spawnRule) {
        addPotentialSpawn(this.getSnapshot().getSpawner(), snapshot, weight, spawnRule, null);
    }

    public static void addPotentialSpawn(MobSpawnerAbstract spawner, EntitySnapshot snapshot, int weight, SpawnRule spawnRule, SpawnerEntry.Equipment equipment) {
        Preconditions.checkArgument(snapshot != null, "Snapshot cannot be null");

        NBTTagCompound compoundTag = ((CraftEntitySnapshot) snapshot).getData();

        WeightedList.a<MobSpawnerData> builder = WeightedList.builder(); // PAIL rename Builder
        spawner.spawnPotentials.unwrap().forEach(entry -> builder.add(entry.value(), entry.weight()));
        builder.add(new MobSpawnerData(compoundTag, Optional.ofNullable(toMinecraftRule(spawnRule)), getEquipment(equipment)), weight);
        spawner.spawnPotentials = builder.build();
    }

    @Override
    public void addPotentialSpawn(SpawnerEntry spawnerEntry) {
        Preconditions.checkArgument(spawnerEntry != null, "Entry cannot be null");

        addPotentialSpawn(spawnerEntry.getSnapshot(), spawnerEntry.getSpawnWeight(), spawnerEntry.getSpawnRule());
    }

    @Override
    public void setPotentialSpawns(Collection<SpawnerEntry> entries) {
        setPotentialSpawns(this.getSnapshot().getSpawner(), entries);
    }

    public static void setPotentialSpawns(MobSpawnerAbstract spawner, Collection<SpawnerEntry> entries) {
        Preconditions.checkArgument(entries != null, "Entries cannot be null");

        WeightedList.a<MobSpawnerData> builder = WeightedList.builder();
        for (SpawnerEntry spawnerEntry : entries) {
            NBTTagCompound compoundTag = ((CraftEntitySnapshot) spawnerEntry.getSnapshot()).getData();
            builder.add(new MobSpawnerData(compoundTag, Optional.ofNullable(toMinecraftRule(spawnerEntry.getSpawnRule())), getEquipment(spawnerEntry.getEquipment())), spawnerEntry.getSpawnWeight());
        }
        spawner.spawnPotentials = builder.build();
    }

    @Override
    public List<SpawnerEntry> getPotentialSpawns() {
        return getPotentialSpawns(this.getSnapshot().getSpawner());
    }

    public static List<SpawnerEntry> getPotentialSpawns(MobSpawnerAbstract spawner) {
        List<SpawnerEntry> entries = new ArrayList<>();

        for (Weighted<MobSpawnerData> entry : spawner.spawnPotentials.unwrap()) {
            CraftEntitySnapshot snapshot = CraftEntitySnapshot.create(entry.value().getEntityToSpawn());

            if (snapshot != null) {
                SpawnRule rule = entry.value().customSpawnRules().map(CraftCreatureSpawner::fromMinecraftRule).orElse(null);
                entries.add(new SpawnerEntry(snapshot, entry.weight(), rule, getEquipment(entry.value().equipment())));
            }
        }
        return entries;
    }

    public static MobSpawnerData.a toMinecraftRule(SpawnRule rule) { // PAIL rename CustomSpawnRules
        if (rule == null) {
            return null;
        }
        return new MobSpawnerData.a(new InclusiveRange<>(rule.getMinBlockLight(), rule.getMaxBlockLight()), new InclusiveRange<>(rule.getMinSkyLight(), rule.getMaxSkyLight()));
    }

    public static SpawnRule fromMinecraftRule(MobSpawnerData.a rule) {
        InclusiveRange<Integer> blockLight = rule.blockLightLimit();
        InclusiveRange<Integer> skyLight = rule.skyLightLimit();

        return new SpawnRule(blockLight.maxInclusive(), blockLight.maxInclusive(), skyLight.minInclusive(), skyLight.maxInclusive());
    }

    @Override
    public String getCreatureTypeName() {
        MobSpawnerData spawnData = this.getSnapshot().getSpawner().nextSpawnData;
        if (spawnData == null) {
            return null;
        }

        Optional<EntityTypes<?>> type = EntityTypes.by(spawnData.getEntityToSpawn());
        return type.map(CraftEntityType::minecraftToBukkit).map(CraftEntityType::bukkitToString).orElse(null);
    }

    @Override
    public void setCreatureTypeByName(String creatureType) {
        // Verify input
        EntityType type = CraftEntityType.stringToBukkit(creatureType);
        if (type == null) {
            setSpawnedType(null);
            return;
        }
        setSpawnedType(type);
    }

    @Override
    public int getDelay() {
        return this.getSnapshot().getSpawner().spawnDelay;
    }

    @Override
    public void setDelay(int delay) {
        this.getSnapshot().getSpawner().spawnDelay = delay;
    }

    @Override
    public int getMinSpawnDelay() {
        return this.getSnapshot().getSpawner().minSpawnDelay;
    }

    @Override
    public void setMinSpawnDelay(int spawnDelay) {
        Preconditions.checkArgument(spawnDelay <= getMaxSpawnDelay(), "Minimum Spawn Delay must be less than or equal to Maximum Spawn Delay");
        this.getSnapshot().getSpawner().minSpawnDelay = spawnDelay;
    }

    @Override
    public int getMaxSpawnDelay() {
        return this.getSnapshot().getSpawner().maxSpawnDelay;
    }

    @Override
    public void setMaxSpawnDelay(int spawnDelay) {
        Preconditions.checkArgument(spawnDelay > 0, "Maximum Spawn Delay must be greater than 0.");
        Preconditions.checkArgument(spawnDelay >= getMinSpawnDelay(), "Maximum Spawn Delay must be greater than or equal to Minimum Spawn Delay");
        this.getSnapshot().getSpawner().maxSpawnDelay = spawnDelay;
    }

    @Override
    public int getMaxNearbyEntities() {
        return this.getSnapshot().getSpawner().maxNearbyEntities;
    }

    @Override
    public void setMaxNearbyEntities(int maxNearbyEntities) {
        this.getSnapshot().getSpawner().maxNearbyEntities = maxNearbyEntities;
    }

    @Override
    public int getSpawnCount() {
        return this.getSnapshot().getSpawner().spawnCount;
    }

    @Override
    public void setSpawnCount(int count) {
        this.getSnapshot().getSpawner().spawnCount = count;
    }

    @Override
    public int getRequiredPlayerRange() {
        return this.getSnapshot().getSpawner().requiredPlayerRange;
    }

    @Override
    public void setRequiredPlayerRange(int requiredPlayerRange) {
        this.getSnapshot().getSpawner().requiredPlayerRange = requiredPlayerRange;
    }

    @Override
    public int getSpawnRange() {
        return this.getSnapshot().getSpawner().spawnRange;
    }

    @Override
    public void setSpawnRange(int spawnRange) {
        this.getSnapshot().getSpawner().spawnRange = spawnRange;
    }

    @Override
    public CraftCreatureSpawner copy() {
        return new CraftCreatureSpawner(this, null);
    }

    @Override
    public CraftCreatureSpawner copy(Location location) {
        return new CraftCreatureSpawner(this, location);
    }

    public static Optional<EquipmentTable> getEquipment(SpawnerEntry.Equipment bukkit) {
        if (bukkit == null) {
            return Optional.empty();
        }

        return Optional.of(new EquipmentTable(
                CraftLootTable.bukkitToMinecraft(bukkit.getEquipmentLootTable()),
                bukkit.getDropChances().entrySet().stream().collect(Collectors.toMap((entry) -> CraftEquipmentSlot.getNMS(entry.getKey()), Map.Entry::getValue)))
        );
    }

    public static SpawnerEntry.Equipment getEquipment(Optional<EquipmentTable> optional) {
        return optional.map((nms) -> new SpawnerEntry.Equipment(
                CraftLootTable.minecraftToBukkit(nms.lootTable()),
                new HashMap<>(nms.slotDropChances().entrySet().stream().collect(Collectors.toMap((entry) -> CraftEquipmentSlot.getSlot(entry.getKey()), Map.Entry::getValue)))
        )).orElse(null);
    }
}
