package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.animal.ChickenVariant;
import net.minecraft.world.entity.animal.EntityChicken;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.registry.CraftRegistryItem;
import org.bukkit.entity.Chicken;

public class CraftChicken extends CraftAnimals implements Chicken {

    public CraftChicken(CraftServer server, EntityChicken entity) {
        super(server, entity);
    }

    @Override
    public EntityChicken getHandle() {
        return (EntityChicken) entity;
    }

    @Override
    public String toString() {
        return "CraftChicken";
    }

    @Override
    public Chicken.Variant getVariant() {
        return CraftChicken.CraftVariant.minecraftHolderToBukkit(getHandle().getVariant());
    }

    @Override
    public void setVariant(Chicken.Variant variant) {
        Preconditions.checkArgument(variant != null, "variant");

        getHandle().setVariant(CraftChicken.CraftVariant.bukkitToMinecraftHolder(variant));
    }

    public static class CraftVariant extends CraftRegistryItem<ChickenVariant> implements Chicken.Variant {

        public static Chicken.Variant minecraftToBukkit(ChickenVariant minecraft) {
            return CraftRegistry.minecraftToBukkit(minecraft, Registries.CHICKEN_VARIANT, Registry.CHICKEN_VARIANT);
        }

        public static Chicken.Variant minecraftHolderToBukkit(Holder<ChickenVariant> minecraft) {
            return minecraftToBukkit(minecraft.value());
        }

        public static ChickenVariant bukkitToMinecraft(Chicken.Variant bukkit) {
            return CraftRegistry.bukkitToMinecraft(bukkit);
        }

        public static Holder<ChickenVariant> bukkitToMinecraftHolder(Chicken.Variant bukkit) {
            return CraftRegistry.bukkitToMinecraftHolder(bukkit, Registries.CHICKEN_VARIANT);
        }

        public CraftVariant(NamespacedKey key, Holder<ChickenVariant> handle) {
            super(key, handle);
        }

        @Override
        public NamespacedKey getKey() {
            return getKeyOrThrow();
        }
    }
}
