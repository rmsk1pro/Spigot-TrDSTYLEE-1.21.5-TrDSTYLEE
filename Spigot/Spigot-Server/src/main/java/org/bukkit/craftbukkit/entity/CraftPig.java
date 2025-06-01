package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.animal.EntityPig;
import net.minecraft.world.entity.animal.PigVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.registry.CraftRegistryItem;
import org.bukkit.entity.Pig;

public class CraftPig extends CraftAnimals implements Pig {

    public CraftPig(CraftServer server, EntityPig entity) {
        super(server, entity);
    }

    @Override
    public boolean hasSaddle() {
        return getHandle().isSaddled();
    }

    @Override
    public void setSaddle(boolean saddled) {
        getHandle().setItemSlot(EnumItemSlot.SADDLE, (saddled) ? new ItemStack(Items.SADDLE) : ItemStack.EMPTY);
    }

    @Override
    public int getBoostTicks() {
        return getHandle().steering.boosting ? getHandle().steering.boostTimeTotal() : 0;
    }

    @Override
    public void setBoostTicks(int ticks) {
        Preconditions.checkArgument(ticks >= 0, "ticks must be >= 0");

        getHandle().steering.setBoostTicks(ticks);
    }

    @Override
    public int getCurrentBoostTicks() {
        return getHandle().steering.boosting ? getHandle().steering.boostTime : 0;
    }

    @Override
    public void setCurrentBoostTicks(int ticks) {
        if (!getHandle().steering.boosting) {
            return;
        }

        int max = getHandle().steering.boostTimeTotal();
        Preconditions.checkArgument(ticks >= 0 && ticks <= max, "boost ticks must not exceed 0 or %d (inclusive)", max);

        this.getHandle().steering.boostTime = ticks;
    }

    @Override
    public Material getSteerMaterial() {
        return Material.CARROT_ON_A_STICK;
    }

    @Override
    public Pig.Variant getVariant() {
        return CraftVariant.minecraftHolderToBukkit(getHandle().getVariant());
    }

    @Override
    public void setVariant(Pig.Variant variant) {
        Preconditions.checkArgument(variant != null, "variant");

        getHandle().setVariant(CraftVariant.bukkitToMinecraftHolder(variant));
    }

    public static class CraftVariant extends CraftRegistryItem<PigVariant> implements Pig.Variant {

        public static Pig.Variant minecraftToBukkit(PigVariant minecraft) {
            return CraftRegistry.minecraftToBukkit(minecraft, Registries.PIG_VARIANT, Registry.PIG_VARIANT);
        }

        public static Pig.Variant minecraftHolderToBukkit(Holder<PigVariant> minecraft) {
            return minecraftToBukkit(minecraft.value());
        }

        public static PigVariant bukkitToMinecraft(Pig.Variant bukkit) {
            return CraftRegistry.bukkitToMinecraft(bukkit);
        }

        public static Holder<PigVariant> bukkitToMinecraftHolder(Pig.Variant bukkit) {
            return CraftRegistry.bukkitToMinecraftHolder(bukkit, Registries.PIG_VARIANT);
        }

        public CraftVariant(NamespacedKey key, Holder<PigVariant> handle) {
            super(key, handle);
        }

        @Override
        public NamespacedKey getKey() {
            return getKeyOrThrow();
        }
    }

    @Override
    public EntityPig getHandle() {
        return (EntityPig) entity;
    }

    @Override
    public String toString() {
        return "CraftPig";
    }
}
