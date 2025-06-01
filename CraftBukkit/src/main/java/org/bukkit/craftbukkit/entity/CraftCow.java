package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.animal.CowVariant;
import net.minecraft.world.entity.animal.EntityCow;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.registry.CraftRegistryItem;
import org.bukkit.entity.Cow;

public class CraftCow extends CraftAbstractCow implements Cow {

    public CraftCow(CraftServer server, EntityCow entity) {
        super(server, entity);
    }

    @Override
    public EntityCow getHandle() {
        return (EntityCow) entity;
    }

    @Override
    public String toString() {
        return "CraftCow";
    }

    @Override
    public Cow.Variant getVariant() {
        return CraftVariant.minecraftHolderToBukkit(getHandle().getVariant());
    }

    @Override
    public void setVariant(Cow.Variant variant) {
        Preconditions.checkArgument(variant != null, "variant");

        getHandle().setVariant(CraftVariant.bukkitToMinecraftHolder(variant));
    }

    public static class CraftVariant extends CraftRegistryItem<CowVariant> implements Cow.Variant {

        public static Cow.Variant minecraftToBukkit(CowVariant minecraft) {
            return CraftRegistry.minecraftToBukkit(minecraft, Registries.COW_VARIANT, Registry.COW_VARIANT);
        }

        public static Cow.Variant minecraftHolderToBukkit(Holder<CowVariant> minecraft) {
            return minecraftToBukkit(minecraft.value());
        }

        public static CowVariant bukkitToMinecraft(Cow.Variant bukkit) {
            return CraftRegistry.bukkitToMinecraft(bukkit);
        }

        public static Holder<CowVariant> bukkitToMinecraftHolder(Cow.Variant bukkit) {
            return CraftRegistry.bukkitToMinecraftHolder(bukkit, Registries.COW_VARIANT);
        }

        public CraftVariant(NamespacedKey key, Holder<CowVariant> handle) {
            super(key, handle);
        }

        @Override
        public NamespacedKey getKey() {
            return getKeyOrThrow();
        }
    }
}
