package org.bukkit.craftbukkit;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FluidType;
import org.bukkit.Fluid;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.registry.CraftOldEnumRegistryItem;
import org.jetbrains.annotations.NotNull;

public class CraftFluid extends CraftOldEnumRegistryItem<Fluid, FluidType> implements Fluid {

    private static int count = 0;

    public static Fluid minecraftToBukkit(FluidType minecraft) {
        return CraftRegistry.minecraftToBukkit(minecraft, Registries.FLUID, Registry.FLUID);
    }

    public static FluidType bukkitToMinecraft(Fluid bukkit) {
        return CraftRegistry.bukkitToMinecraft(bukkit);
    }

    public CraftFluid(NamespacedKey key, Holder<FluidType> handle) {
        super(key, handle, count++);
    }

    @NotNull
    @Override
    public NamespacedKey getKey() {
        return getKeyOrThrow();
    }
}
