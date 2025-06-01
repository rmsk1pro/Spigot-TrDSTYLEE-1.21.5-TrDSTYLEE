package org.bukkit.craftbukkit.attribute;

import com.google.common.base.Preconditions;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.IRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.AttributeBase;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.legacy.FieldRename;
import org.bukkit.craftbukkit.registry.CraftOldEnumRegistryItem;
import org.bukkit.craftbukkit.util.ApiVersion;
import org.jetbrains.annotations.NotNull;

public class CraftAttribute extends CraftOldEnumRegistryItem<Attribute, AttributeBase> implements Attribute {

    private static int count = 0;

    public static Attribute minecraftToBukkit(AttributeBase minecraft) {
        return CraftRegistry.minecraftToBukkit(minecraft, Registries.ATTRIBUTE, Registry.ATTRIBUTE);
    }

    public static Attribute minecraftHolderToBukkit(Holder<AttributeBase> minecraft) {
        return minecraftToBukkit(minecraft.value());
    }

    public static Attribute stringToBukkit(String string) {
        Preconditions.checkArgument(string != null);

        // We currently do not have any version-dependent remapping, so we can use current version
        // First convert from when only the names where saved
        string = FieldRename.convertAttributeName(ApiVersion.CURRENT, string);
        string = string.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(string);

        // Now also convert from when keys where saved
        return CraftRegistry.get(Registry.ATTRIBUTE, key, ApiVersion.CURRENT);
    }

    public static AttributeBase bukkitToMinecraft(Attribute bukkit) {
        return CraftRegistry.bukkitToMinecraft(bukkit);
    }

    public static Holder<AttributeBase> bukkitToMinecraftHolder(Attribute bukkit) {
        Preconditions.checkArgument(bukkit != null);

        IRegistry<AttributeBase> registry = CraftRegistry.getMinecraftRegistry(Registries.ATTRIBUTE);

        if (registry.wrapAsHolder(bukkitToMinecraft(bukkit)) instanceof Holder.c<AttributeBase> holder) {
            return holder;
        }

        throw new IllegalArgumentException("No Reference holder found for " + bukkit
                + ", this can happen if a plugin creates its own sound effect with out properly registering it.");
    }

    public static String bukkitToString(Attribute bukkit) {
        Preconditions.checkArgument(bukkit != null);

        return bukkit.getKey().toString();
    }

    public CraftAttribute(NamespacedKey key, Holder<AttributeBase> handle) {
        super(key, handle, count++);
    }

    @NotNull
    @Override
    public NamespacedKey getKey() {
        return getKeyOrThrow();
    }

    @NotNull
    @Override
    public String getTranslationKey() {
        return getHandle().getDescriptionId();
    }
}
