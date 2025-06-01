package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.stream.Stream;
import net.minecraft.core.IRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

// CraftBukkit start
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
// CraftBukkit end

public record LootDataType<T>(ResourceKey<IRegistry<T>> registryKey, Codec<T> codec, LootDataType.a<T> validator) {

    public static final LootDataType<LootItemCondition> PREDICATE = new LootDataType<LootItemCondition>(Registries.PREDICATE, LootItemCondition.DIRECT_CODEC, createSimpleValidator());
    public static final LootDataType<LootItemFunction> MODIFIER = new LootDataType<LootItemFunction>(Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC, createSimpleValidator());
    public static final LootDataType<LootTable> TABLE = new LootDataType<LootTable>(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC, createLootTableValidator());

    public void runValidation(LootCollector lootcollector, ResourceKey<T> resourcekey, T t0) {
        this.validator.run(lootcollector, resourcekey, t0);
    }

    public static Stream<LootDataType<?>> values() {
        return Stream.of(LootDataType.PREDICATE, LootDataType.MODIFIER, LootDataType.TABLE);
    }

    private static <T extends LootItemUser> LootDataType.a<T> createSimpleValidator() {
        return (lootcollector, resourcekey, lootitemuser) -> {
            lootitemuser.validate(lootcollector.enterElement("{" + String.valueOf(resourcekey.registry()) + "/" + String.valueOf(resourcekey.location()) + "}", resourcekey));
        };
    }

    private static LootDataType.a<LootTable> createLootTableValidator() {
        return (lootcollector, resourcekey, loottable) -> {
            loottable.validate(lootcollector.setContextKeySet(loottable.getParamSet()).enterElement("{" + String.valueOf(resourcekey.registry()) + "/" + String.valueOf(resourcekey.location()) + "}", resourcekey));
            loottable.craftLootTable = new CraftLootTable(CraftNamespacedKey.fromMinecraft(resourcekey.location()), loottable); // CraftBukkit
        };
    }

    @FunctionalInterface
    public interface a<T> {

        void run(LootCollector lootcollector, ResourceKey<T> resourcekey, T t0);
    }
}
