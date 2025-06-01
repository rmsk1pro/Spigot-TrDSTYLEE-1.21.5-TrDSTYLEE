package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.WorldGenFeatureEmptyConfiguration;

// CraftBukkit start
import java.util.List;
import org.bukkit.block.BlockState;
import org.bukkit.event.world.PortalCreateEvent;
// CraftBukkit end

public class EndPlatformFeature extends WorldGenerator<WorldGenFeatureEmptyConfiguration> {

    public EndPlatformFeature(Codec<WorldGenFeatureEmptyConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<WorldGenFeatureEmptyConfiguration> featureplacecontext) {
        createEndPlatform(featureplacecontext.level(), featureplacecontext.origin(), false);
        return true;
    }

    public static void createEndPlatform(WorldAccess worldaccess, BlockPosition blockposition, boolean flag) {
        createEndPlatform(worldaccess, blockposition, flag, null);
        // CraftBukkit start
    }

    public static void createEndPlatform(WorldAccess worldaccess, BlockPosition blockposition, boolean flag, Entity entity) {
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(worldaccess);
        // CraftBukkit end
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();

        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                for (int k = -1; k < 3; ++k) {
                    BlockPosition blockposition1 = blockposition_mutableblockposition.set(blockposition).move(j, k, i);
                    Block block = k == -1 ? Blocks.OBSIDIAN : Blocks.AIR;

                    // CraftBukkit start
                    if (!blockList.getBlockState(blockposition1).is(block)) {
                        if (flag) {
                            blockList.destroyBlock(blockposition1, true, (Entity) null);
                        }

                        blockList.setBlock(blockposition1, block.defaultBlockState(), 3);
                        // CraftBukkit end
                    }
                }
            }
        }
        // CraftBukkit start
        // SPIGOT-7746: Entity will only be null during world generation, which is async, so just generate without event
        if (entity != null) {
            org.bukkit.World bworld = worldaccess.getLevel().getWorld();
            PortalCreateEvent portalEvent = new PortalCreateEvent((List<BlockState>) (List) blockList.getList(), bworld, entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM);

            worldaccess.getLevel().getCraftServer().getPluginManager().callEvent(portalEvent);
            if (portalEvent.isCancelled()) {
                return;
            }
        }

        // SPIGOT-7856: End platform not dropping items after replacing blocks
        if (flag) {
            blockList.getList().forEach((state) -> worldaccess.destroyBlock(state.getPosition(), true, null));
        }
        blockList.updateList();
        // CraftBukkit end

    }
}
