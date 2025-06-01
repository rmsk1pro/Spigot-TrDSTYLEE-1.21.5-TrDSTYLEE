package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemUtils;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.RegionLimitedWorldAccess;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EnumCreatureType;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.GeneratorAccessSeed;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.World;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSettingsGeneration;
import net.minecraft.world.level.biome.BiomeSettingsMobs;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.WorldChunkManager;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SeededRandom;
import net.minecraft.world.level.levelgen.WorldGenStage;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureBoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class ChunkGenerator {

    public static final Codec<ChunkGenerator> CODEC = BuiltInRegistries.CHUNK_GENERATOR.byNameCodec().dispatchStable(ChunkGenerator::codec, Function.identity());
    protected final WorldChunkManager biomeSource;
    private final Supplier<List<FeatureSorter.b>> featuresPerStep;
    public final Function<Holder<BiomeBase>, BiomeSettingsGeneration> generationSettingsGetter;

    public ChunkGenerator(WorldChunkManager worldchunkmanager) {
        this(worldchunkmanager, (holder) -> {
            return ((BiomeBase) holder.value()).getGenerationSettings();
        });
    }

    public ChunkGenerator(WorldChunkManager worldchunkmanager, Function<Holder<BiomeBase>, BiomeSettingsGeneration> function) {
        this.biomeSource = worldchunkmanager;
        this.generationSettingsGetter = function;
        this.featuresPerStep = Suppliers.memoize(() -> {
            return FeatureSorter.buildFeaturesPerStep(List.copyOf(worldchunkmanager.possibleBiomes()), (holder) -> {
                return ((BiomeSettingsGeneration) function.apply(holder)).features();
            }, true);
        });
    }

    public void validate() {
        this.featuresPerStep.get();
    }

    protected abstract MapCodec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderlookup, RandomState randomstate, long i) {
        return ChunkGeneratorStructureState.createForNormal(randomstate, i, this.biomeSource, holderlookup);
    }

    public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<IChunkAccess> createBiomes(RandomState randomstate, Blender blender, StructureManager structuremanager, IChunkAccess ichunkaccess) {
        return CompletableFuture.supplyAsync(() -> {
            ichunkaccess.fillBiomesFromNoise(this.biomeSource, randomstate.sampler());
            return ichunkaccess;
        }, SystemUtils.backgroundExecutor().forName("init_biomes"));
    }

    public abstract void applyCarvers(RegionLimitedWorldAccess regionlimitedworldaccess, long i, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, IChunkAccess ichunkaccess);

    @Nullable
    public Pair<BlockPosition, Holder<Structure>> findNearestMapStructure(WorldServer worldserver, HolderSet<Structure> holderset, BlockPosition blockposition, int i, boolean flag) {
        ChunkGeneratorStructureState chunkgeneratorstructurestate = worldserver.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> map = new Object2ObjectArrayMap();

        for (Holder<Structure> holder : holderset) {
            for (StructurePlacement structureplacement : chunkgeneratorstructurestate.getPlacementsForStructure(holder)) {
                ((Set) map.computeIfAbsent(structureplacement, (structureplacement1) -> {
                    return new ObjectArraySet();
                })).add(holder);
            }
        }

        if (map.isEmpty()) {
            return null;
        } else {
            Pair<BlockPosition, Holder<Structure>> pair = null;
            double d0 = Double.MAX_VALUE;
            StructureManager structuremanager = worldserver.structureManager();
            List<Map.Entry<StructurePlacement, Set<Holder<Structure>>>> list = new ArrayList(map.size());

            for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> map_entry : map.entrySet()) {
                StructurePlacement structureplacement1 = (StructurePlacement) map_entry.getKey();

                if (structureplacement1 instanceof ConcentricRingsStructurePlacement) {
                    ConcentricRingsStructurePlacement concentricringsstructureplacement = (ConcentricRingsStructurePlacement) structureplacement1;
                    Pair<BlockPosition, Holder<Structure>> pair1 = this.getNearestGeneratedStructure((Set) map_entry.getValue(), worldserver, structuremanager, blockposition, flag, concentricringsstructureplacement);

                    if (pair1 != null) {
                        BlockPosition blockposition1 = (BlockPosition) pair1.getFirst();
                        double d1 = blockposition.distSqr(blockposition1);

                        if (d1 < d0) {
                            d0 = d1;
                            pair = pair1;
                        }
                    }
                } else if (structureplacement1 instanceof RandomSpreadStructurePlacement) {
                    list.add(map_entry);
                }
            }

            if (!list.isEmpty()) {
                int j = SectionPosition.blockToSectionCoord(blockposition.getX());
                int k = SectionPosition.blockToSectionCoord(blockposition.getZ());

                for (int l = 0; l <= i; ++l) {
                    boolean flag1 = false;

                    for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> map_entry1 : list) {
                        RandomSpreadStructurePlacement randomspreadstructureplacement = (RandomSpreadStructurePlacement) map_entry1.getKey();
                        Pair<BlockPosition, Holder<Structure>> pair2 = getNearestGeneratedStructure((Set) map_entry1.getValue(), worldserver, structuremanager, j, k, l, flag, chunkgeneratorstructurestate.getLevelSeed(), randomspreadstructureplacement);

                        if (pair2 != null) {
                            flag1 = true;
                            double d2 = blockposition.distSqr((BaseBlockPosition) pair2.getFirst());

                            if (d2 < d0) {
                                d0 = d2;
                                pair = pair2;
                            }
                        }
                    }

                    if (flag1) {
                        return pair;
                    }
                }
            }

            return pair;
        }
    }

    @Nullable
    private Pair<BlockPosition, Holder<Structure>> getNearestGeneratedStructure(Set<Holder<Structure>> set, WorldServer worldserver, StructureManager structuremanager, BlockPosition blockposition, boolean flag, ConcentricRingsStructurePlacement concentricringsstructureplacement) {
        List<ChunkCoordIntPair> list = worldserver.getChunkSource().getGeneratorState().getRingPositionsFor(concentricringsstructureplacement);

        if (list == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        } else {
            Pair<BlockPosition, Holder<Structure>> pair = null;
            double d0 = Double.MAX_VALUE;
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();

            for (ChunkCoordIntPair chunkcoordintpair : list) {
                blockposition_mutableblockposition.set(SectionPosition.sectionToBlockCoord(chunkcoordintpair.x, 8), 32, SectionPosition.sectionToBlockCoord(chunkcoordintpair.z, 8));
                double d1 = blockposition_mutableblockposition.distSqr(blockposition);
                boolean flag1 = pair == null || d1 < d0;

                if (flag1) {
                    Pair<BlockPosition, Holder<Structure>> pair1 = getStructureGeneratingAt(set, worldserver, structuremanager, flag, concentricringsstructureplacement, chunkcoordintpair);

                    if (pair1 != null) {
                        pair = pair1;
                        d0 = d1;
                    }
                }
            }

            return pair;
        }
    }

    @Nullable
    private static Pair<BlockPosition, Holder<Structure>> getNearestGeneratedStructure(Set<Holder<Structure>> set, IWorldReader iworldreader, StructureManager structuremanager, int i, int j, int k, boolean flag, long l, RandomSpreadStructurePlacement randomspreadstructureplacement) {
        int i1 = randomspreadstructureplacement.spacing();

        for (int j1 = -k; j1 <= k; ++j1) {
            boolean flag1 = j1 == -k || j1 == k;

            for (int k1 = -k; k1 <= k; ++k1) {
                boolean flag2 = k1 == -k || k1 == k;

                if (flag1 || flag2) {
                    int l1 = i + i1 * j1;
                    int i2 = j + i1 * k1;
                    ChunkCoordIntPair chunkcoordintpair = randomspreadstructureplacement.getPotentialStructureChunk(l, l1, i2);
                    Pair<BlockPosition, Holder<Structure>> pair = getStructureGeneratingAt(set, iworldreader, structuremanager, flag, randomspreadstructureplacement, chunkcoordintpair);

                    if (pair != null) {
                        return pair;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static Pair<BlockPosition, Holder<Structure>> getStructureGeneratingAt(Set<Holder<Structure>> set, IWorldReader iworldreader, StructureManager structuremanager, boolean flag, StructurePlacement structureplacement, ChunkCoordIntPair chunkcoordintpair) {
        for (Holder<Structure> holder : set) {
            StructureCheckResult structurecheckresult = structuremanager.checkStructurePresence(chunkcoordintpair, holder.value(), structureplacement, flag);

            if (structurecheckresult != StructureCheckResult.START_NOT_PRESENT) {
                if (!flag && structurecheckresult == StructureCheckResult.START_PRESENT) {
                    return Pair.of(structureplacement.getLocatePos(chunkcoordintpair), holder);
                }

                IChunkAccess ichunkaccess = iworldreader.getChunk(chunkcoordintpair.x, chunkcoordintpair.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart structurestart = structuremanager.getStartForStructure(SectionPosition.bottomOf(ichunkaccess), holder.value(), ichunkaccess);

                if (structurestart != null && structurestart.isValid() && (!flag || tryAddReference(structuremanager, structurestart))) {
                    return Pair.of(structureplacement.getLocatePos(structurestart.getChunkPos()), holder);
                }
            }
        }

        return null;
    }

    private static boolean tryAddReference(StructureManager structuremanager, StructureStart structurestart) {
        if (structurestart.canBeReferenced()) {
            structuremanager.addReference(structurestart);
            return true;
        } else {
            return false;
        }
    }

    public void addVanillaDecorations(GeneratorAccessSeed generatoraccessseed, IChunkAccess ichunkaccess, StructureManager structuremanager) { // CraftBukkit
        ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();

        if (!SharedConstants.debugVoidTerrain(chunkcoordintpair)) {
            SectionPosition sectionposition = SectionPosition.of(chunkcoordintpair, generatoraccessseed.getMinSectionY());
            BlockPosition blockposition = sectionposition.origin();
            IRegistry<Structure> iregistry = generatoraccessseed.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> map = (Map) iregistry.stream().collect(Collectors.groupingBy((structure) -> {
                return structure.step().ordinal();
            }));
            List<FeatureSorter.b> list = (List) this.featuresPerStep.get();
            SeededRandom seededrandom = new SeededRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            long i = seededrandom.setDecorationSeed(generatoraccessseed.getSeed(), blockposition.getX(), blockposition.getZ());
            Set<Holder<BiomeBase>> set = new ObjectArraySet();

            ChunkCoordIntPair.rangeClosed(sectionposition.chunk(), 1).forEach((chunkcoordintpair1) -> {
                IChunkAccess ichunkaccess1 = generatoraccessseed.getChunk(chunkcoordintpair1.x, chunkcoordintpair1.z);

                for (ChunkSection chunksection : ichunkaccess1.getSections()) {
                    PalettedContainerRO<Holder<BiomeBase>> palettedcontainerro = chunksection.getBiomes(); // CraftBukkit - decompile error

                    Objects.requireNonNull(set);
                    palettedcontainerro.getAll(set::add);
                }

            });
            set.retainAll(this.biomeSource.possibleBiomes());
            int j = list.size();

            try {
                IRegistry<PlacedFeature> iregistry1 = generatoraccessseed.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
                int k = Math.max(WorldGenStage.Decoration.values().length, j);

                for (int l = 0; l < k; ++l) {
                    int i1 = 0;

                    if (structuremanager.shouldGenerateStructures()) {
                        for (Structure structure : map.getOrDefault(l, Collections.emptyList())) { // CraftBukkit - decompile error
                            seededrandom.setFeatureSeed(i, i1, l);
                            Supplier<String> supplier = () -> {
                                Optional optional = iregistry.getResourceKey(structure).map(Object::toString);

                                Objects.requireNonNull(structure);
                                return (String) optional.orElseGet(structure::toString);
                            };

                            try {
                                generatoraccessseed.setCurrentlyGenerating(supplier);
                                structuremanager.startsForStructure(sectionposition, structure).forEach((structurestart) -> {
                                    structurestart.placeInChunk(generatoraccessseed, structuremanager, this, seededrandom, getWritableArea(ichunkaccess), chunkcoordintpair);
                                });
                            } catch (Exception exception) {
                                CrashReport crashreport = CrashReport.forThrowable(exception, "Feature placement");
                                CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Feature");

                                Objects.requireNonNull(supplier);
                                crashreportsystemdetails.setDetail("Description", supplier::get);
                                throw new ReportedException(crashreport);
                            }

                            ++i1;
                        }
                    }

                    if (l < j) {
                        IntSet intset = new IntArraySet();

                        for (Holder<BiomeBase> holder : set) {
                            List<HolderSet<PlacedFeature>> list1 = ((BiomeSettingsGeneration) this.generationSettingsGetter.apply(holder)).features();

                            if (l < list1.size()) {
                                HolderSet<PlacedFeature> holderset = (HolderSet) list1.get(l);
                                FeatureSorter.b featuresorter_b = (FeatureSorter.b) list.get(l);

                                holderset.stream().map(Holder::value).forEach((placedfeature) -> {
                                    intset.add(featuresorter_b.indexMapping().applyAsInt(placedfeature));
                                });
                            }
                        }

                        int j1 = intset.size();
                        int[] aint = intset.toIntArray();

                        Arrays.sort(aint);
                        FeatureSorter.b featuresorter_b1 = (FeatureSorter.b) list.get(l);

                        for (int k1 = 0; k1 < j1; ++k1) {
                            int l1 = aint[k1];
                            PlacedFeature placedfeature = (PlacedFeature) featuresorter_b1.features().get(l1);
                            Supplier<String> supplier1 = () -> {
                                Optional optional = iregistry1.getResourceKey(placedfeature).map(Object::toString);

                                Objects.requireNonNull(placedfeature);
                                return (String) optional.orElseGet(placedfeature::toString);
                            };

                            seededrandom.setFeatureSeed(i, l1, l);

                            try {
                                generatoraccessseed.setCurrentlyGenerating(supplier1);
                                placedfeature.placeWithBiomeCheck(generatoraccessseed, this, seededrandom, blockposition);
                            } catch (Exception exception1) {
                                CrashReport crashreport1 = CrashReport.forThrowable(exception1, "Feature placement");
                                CrashReportSystemDetails crashreportsystemdetails1 = crashreport1.addCategory("Feature");

                                Objects.requireNonNull(supplier1);
                                crashreportsystemdetails1.setDetail("Description", supplier1::get);
                                throw new ReportedException(crashreport1);
                            }
                        }
                    }
                }

                generatoraccessseed.setCurrentlyGenerating((Supplier) null);
            } catch (Exception exception2) {
                CrashReport crashreport2 = CrashReport.forThrowable(exception2, "Biome decoration");

                crashreport2.addCategory("Generation").setDetail("CenterX", chunkcoordintpair.x).setDetail("CenterZ", chunkcoordintpair.z).setDetail("Decoration Seed", i);
                throw new ReportedException(crashreport2);
            }
        }
    }

   // CraftBukkit start
    public void applyBiomeDecoration(GeneratorAccessSeed generatoraccessseed, IChunkAccess ichunkaccess, StructureManager structuremanager) {
        applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, true);
    }

    public void applyBiomeDecoration(GeneratorAccessSeed generatoraccessseed, IChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla) {
        if (vanilla) {
            addVanillaDecorations(generatoraccessseed, ichunkaccess, structuremanager);
        }

        org.bukkit.World world = generatoraccessseed.getMinecraftWorld().getWorld();
        // only call when a populator is present (prevents unnecessary entity conversion)
        if (!world.getPopulators().isEmpty()) {
            org.bukkit.craftbukkit.generator.CraftLimitedRegion limitedRegion = new org.bukkit.craftbukkit.generator.CraftLimitedRegion(generatoraccessseed, ichunkaccess.getPos());
            int x = ichunkaccess.getPos().x;
            int z = ichunkaccess.getPos().z;
            for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                SeededRandom seededrandom = new SeededRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(generatoraccessseed.getSeed()));
                seededrandom.setDecorationSeed(generatoraccessseed.getSeed(), x, z);
                populator.populate(world, new org.bukkit.craftbukkit.util.RandomSourceWrapper.RandomWrapper(seededrandom), x, z, limitedRegion);
            }
            limitedRegion.saveEntities();
            limitedRegion.breakLink();
        }
    }
    // CraftBukkit end

    private static StructureBoundingBox getWritableArea(IChunkAccess ichunkaccess) {
        ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
        int i = chunkcoordintpair.getMinBlockX();
        int j = chunkcoordintpair.getMinBlockZ();
        LevelHeightAccessor levelheightaccessor = ichunkaccess.getHeightAccessorForGeneration();
        int k = levelheightaccessor.getMinY() + 1;
        int l = levelheightaccessor.getMaxY();

        return new StructureBoundingBox(i, k, j, i + 15, l, j + 15);
    }

    public abstract void buildSurface(RegionLimitedWorldAccess regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, IChunkAccess ichunkaccess);

    public abstract void spawnOriginalMobs(RegionLimitedWorldAccess regionlimitedworldaccess);

    public int getSpawnHeight(LevelHeightAccessor levelheightaccessor) {
        return 64;
    }

    public WorldChunkManager getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedList<BiomeSettingsMobs.c> getMobsAt(Holder<BiomeBase> holder, StructureManager structuremanager, EnumCreatureType enumcreaturetype, BlockPosition blockposition) {
        Map<Structure, LongSet> map = structuremanager.getAllStructuresAt(blockposition);

        for (Map.Entry<Structure, LongSet> map_entry : map.entrySet()) {
            Structure structure = (Structure) map_entry.getKey();
            StructureSpawnOverride structurespawnoverride = (StructureSpawnOverride) structure.spawnOverrides().get(enumcreaturetype);

            if (structurespawnoverride != null) {
                MutableBoolean mutableboolean = new MutableBoolean(false);
                Predicate<StructureStart> predicate = structurespawnoverride.boundingBox() == StructureSpawnOverride.a.PIECE ? (structurestart) -> {
                    return structuremanager.structureHasPieceAt(blockposition, structurestart);
                } : (structurestart) -> {
                    return structurestart.getBoundingBox().isInside(blockposition);
                };

                structuremanager.fillStartsForStructure(structure, (LongSet) map_entry.getValue(), (structurestart) -> {
                    if (mutableboolean.isFalse() && predicate.test(structurestart)) {
                        mutableboolean.setTrue();
                    }

                });
                if (mutableboolean.isTrue()) {
                    return structurespawnoverride.spawns();
                }
            }
        }

        return ((BiomeBase) holder.value()).getMobSettings().getMobs(enumcreaturetype);
    }

    public void createStructures(IRegistryCustom iregistrycustom, ChunkGeneratorStructureState chunkgeneratorstructurestate, StructureManager structuremanager, IChunkAccess ichunkaccess, StructureTemplateManager structuretemplatemanager, ResourceKey<World> resourcekey) {
        ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
        SectionPosition sectionposition = SectionPosition.bottomOf(ichunkaccess);
        RandomState randomstate = chunkgeneratorstructurestate.randomState();

        chunkgeneratorstructurestate.possibleStructureSets().forEach((holder) -> {
            StructurePlacement structureplacement = ((StructureSet) holder.value()).placement();
            List<StructureSet.a> list = ((StructureSet) holder.value()).structures();

            for (StructureSet.a structureset_a : list) {
                StructureStart structurestart = structuremanager.getStartForStructure(sectionposition, (Structure) structureset_a.structure().value(), ichunkaccess);

                if (structurestart != null && structurestart.isValid()) {
                    return;
                }
            }

            if (structureplacement.isStructureChunk(chunkgeneratorstructurestate, chunkcoordintpair.x, chunkcoordintpair.z)) {
                if (list.size() == 1) {
                    this.tryGenerateStructure((StructureSet.a) list.get(0), structuremanager, iregistrycustom, randomstate, structuretemplatemanager, chunkgeneratorstructurestate.getLevelSeed(), ichunkaccess, chunkcoordintpair, sectionposition, resourcekey);
                } else {
                    ArrayList<StructureSet.a> arraylist = new ArrayList(list.size());

                    arraylist.addAll(list);
                    SeededRandom seededrandom = new SeededRandom(new LegacyRandomSource(0L));

                    seededrandom.setLargeFeatureSeed(chunkgeneratorstructurestate.getLevelSeed(), chunkcoordintpair.x, chunkcoordintpair.z);
                    int i = 0;

                    for (StructureSet.a structureset_a1 : arraylist) {
                        i += structureset_a1.weight();
                    }

                    while (!arraylist.isEmpty()) {
                        int j = seededrandom.nextInt(i);
                        int k = 0;

                        for (StructureSet.a structureset_a2 : arraylist) {
                            j -= structureset_a2.weight();
                            if (j < 0) {
                                break;
                            }

                            ++k;
                        }

                        StructureSet.a structureset_a3 = (StructureSet.a) arraylist.get(k);

                        if (this.tryGenerateStructure(structureset_a3, structuremanager, iregistrycustom, randomstate, structuretemplatemanager, chunkgeneratorstructurestate.getLevelSeed(), ichunkaccess, chunkcoordintpair, sectionposition, resourcekey)) {
                            return;
                        }

                        arraylist.remove(k);
                        i -= structureset_a3.weight();
                    }

                }
            }
        });
    }

    private boolean tryGenerateStructure(StructureSet.a structureset_a, StructureManager structuremanager, IRegistryCustom iregistrycustom, RandomState randomstate, StructureTemplateManager structuretemplatemanager, long i, IChunkAccess ichunkaccess, ChunkCoordIntPair chunkcoordintpair, SectionPosition sectionposition, ResourceKey<World> resourcekey) {
        Structure structure = (Structure) structureset_a.structure().value();
        int j = fetchReferences(structuremanager, ichunkaccess, sectionposition, structure);
        HolderSet<BiomeBase> holderset = structure.biomes();

        Objects.requireNonNull(holderset);
        Predicate<Holder<BiomeBase>> predicate = holderset::contains;
        StructureStart structurestart = structure.generate(structureset_a.structure(), resourcekey, iregistrycustom, this, this.biomeSource, randomstate, structuretemplatemanager, i, chunkcoordintpair, j, ichunkaccess, predicate);

        if (structurestart.isValid()) {
            // CraftBukkit start
            StructureBoundingBox box = structurestart.getBoundingBox();
            org.bukkit.event.world.AsyncStructureSpawnEvent event = new org.bukkit.event.world.AsyncStructureSpawnEvent(structuremanager.level.getMinecraftWorld().getWorld(), org.bukkit.craftbukkit.generator.structure.CraftStructure.minecraftToBukkit(structure), new org.bukkit.util.BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), chunkcoordintpair.x, chunkcoordintpair.z);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return true;
            }
            // CraftBukkit end
            structuremanager.setStartForStructure(sectionposition, structure, structurestart, ichunkaccess);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager structuremanager, IChunkAccess ichunkaccess, SectionPosition sectionposition, Structure structure) {
        StructureStart structurestart = structuremanager.getStartForStructure(sectionposition, structure, ichunkaccess);

        return structurestart != null ? structurestart.getReferences() : 0;
    }

    public void createReferences(GeneratorAccessSeed generatoraccessseed, StructureManager structuremanager, IChunkAccess ichunkaccess) {
        int i = 8;
        ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
        int j = chunkcoordintpair.x;
        int k = chunkcoordintpair.z;
        int l = chunkcoordintpair.getMinBlockX();
        int i1 = chunkcoordintpair.getMinBlockZ();
        SectionPosition sectionposition = SectionPosition.bottomOf(ichunkaccess);

        for (int j1 = j - 8; j1 <= j + 8; ++j1) {
            for (int k1 = k - 8; k1 <= k + 8; ++k1) {
                long l1 = ChunkCoordIntPair.asLong(j1, k1);

                for (StructureStart structurestart : generatoraccessseed.getChunk(j1, k1).getAllStarts().values()) {
                    try {
                        if (structurestart.isValid() && structurestart.getBoundingBox().intersects(l, i1, l + 15, i1 + 15)) {
                            structuremanager.addReferenceForStructure(sectionposition, structurestart.getStructure(), l1, ichunkaccess);
                            PacketDebug.sendStructurePacket(generatoraccessseed, structurestart);
                        }
                    } catch (Exception exception) {
                        CrashReport crashreport = CrashReport.forThrowable(exception, "Generating structure reference");
                        CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Structure");
                        Optional<? extends IRegistry<Structure>> optional = generatoraccessseed.registryAccess().lookup(Registries.STRUCTURE);

                        crashreportsystemdetails.setDetail("Id", () -> {
                            return (String) optional.map((iregistry) -> {
                                return iregistry.getKey(structurestart.getStructure()).toString();
                            }).orElse("UNKNOWN");
                        });
                        crashreportsystemdetails.setDetail("Name", () -> {
                            return BuiltInRegistries.STRUCTURE_TYPE.getKey(structurestart.getStructure().type()).toString();
                        });
                        crashreportsystemdetails.setDetail("Class", () -> {
                            return structurestart.getStructure().getClass().getCanonicalName();
                        });
                        throw new ReportedException(crashreport);
                    }
                }
            }
        }

    }

    public abstract CompletableFuture<IChunkAccess> fillFromNoise(Blender blender, RandomState randomstate, StructureManager structuremanager, IChunkAccess ichunkaccess);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int i, int j, HeightMap.Type heightmap_type, LevelHeightAccessor levelheightaccessor, RandomState randomstate);

    public abstract net.minecraft.world.level.BlockColumn getBaseColumn(int i, int j, LevelHeightAccessor levelheightaccessor, RandomState randomstate);

    public int getFirstFreeHeight(int i, int j, HeightMap.Type heightmap_type, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        return this.getBaseHeight(i, j, heightmap_type, levelheightaccessor, randomstate);
    }

    public int getFirstOccupiedHeight(int i, int j, HeightMap.Type heightmap_type, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        return this.getBaseHeight(i, j, heightmap_type, levelheightaccessor, randomstate) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> list, RandomState randomstate, BlockPosition blockposition);

    /** @deprecated */
    @Deprecated
    public BiomeSettingsGeneration getBiomeGenerationSettings(Holder<BiomeBase> holder) {
        return (BiomeSettingsGeneration) this.generationSettingsGetter.apply(holder);
    }
}
