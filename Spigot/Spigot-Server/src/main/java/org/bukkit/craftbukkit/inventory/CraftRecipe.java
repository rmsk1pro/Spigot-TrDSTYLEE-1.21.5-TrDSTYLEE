package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.IRecipe;
import net.minecraft.world.item.crafting.RecipeItemStack;
import net.minecraft.world.item.crafting.TransmuteResult;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;

public interface CraftRecipe extends Recipe {

    void addToCraftingManager();

    default Optional<RecipeItemStack> toNMSOptional(RecipeChoice bukkit, boolean requireNotEmpty) {
        return (bukkit == null) ? Optional.empty() : Optional.of(toNMS(bukkit, requireNotEmpty));
    }

    default RecipeItemStack toNMS(RecipeChoice bukkit, boolean requireNotEmpty) {
        RecipeItemStack stack;

        if (bukkit == null) {
            stack = RecipeItemStack.of();
        } else if (bukkit instanceof RecipeChoice.MaterialChoice) {
            stack = RecipeItemStack.of(((RecipeChoice.MaterialChoice) bukkit).getChoices().stream().map((mat) -> CraftItemType.bukkitToMinecraft(mat)));
        } else if (bukkit instanceof RecipeChoice.ExactChoice) {
            stack = RecipeItemStack.ofStacks(((RecipeChoice.ExactChoice) bukkit).getChoices().stream().map((mat) -> CraftItemStack.asNMSCopy(mat)).toList());
        } else {
            throw new IllegalArgumentException("Unknown recipe stack instance " + bukkit);
        }

        if (requireNotEmpty) {
            Preconditions.checkArgument(!stack.isEmpty(), "Recipe requires at least one non-air choice");
        }

        return stack;
    }

    default TransmuteResult toNMS(ItemStack stack) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);

        return new TransmuteResult(nms.getItemHolder(), nms.getCount(), nms.getComponentsPatch());
    }

    public static RecipeChoice toBukkit(Optional<RecipeItemStack> list) {
        return list.map(CraftRecipe::toBukkit).orElse(null);
    }

    public static RecipeChoice toBukkit(RecipeItemStack list) {
        if (list.isEmpty()) {
            return null;
        }

        if (list.isExact()) {
            List<org.bukkit.inventory.ItemStack> choices = new ArrayList<>(list.itemStacks().size());
            for (net.minecraft.world.item.ItemStack i : list.itemStacks()) {
                choices.add(CraftItemStack.asBukkitCopy(i));
            }

            return new RecipeChoice.ExactChoice(choices);
        } else {
            List<org.bukkit.Material> choices = list.items().map((i) -> CraftItemType.minecraftToBukkit(i.value())).toList();

            return new RecipeChoice.MaterialChoice(choices);
        }
    }

    public static ItemStack toBukkit(TransmuteResult transmute) {
        net.minecraft.world.item.ItemStack nms = new net.minecraft.world.item.ItemStack(transmute.item(), transmute.count(), transmute.components());

        return CraftItemStack.asBukkitCopy(nms);
    }

    public static net.minecraft.world.item.crafting.CraftingBookCategory getCategory(CraftingBookCategory bukkit) {
        return net.minecraft.world.item.crafting.CraftingBookCategory.valueOf(bukkit.name());
    }

    public static CraftingBookCategory getCategory(net.minecraft.world.item.crafting.CraftingBookCategory nms) {
        return CraftingBookCategory.valueOf(nms.name());
    }

    public static net.minecraft.world.item.crafting.CookingBookCategory getCategory(CookingBookCategory bukkit) {
        return net.minecraft.world.item.crafting.CookingBookCategory.valueOf(bukkit.name());
    }

    public static CookingBookCategory getCategory(net.minecraft.world.item.crafting.CookingBookCategory nms) {
        return CookingBookCategory.valueOf(nms.name());
    }

    public static ResourceKey<IRecipe<?>> toMinecraft(NamespacedKey key) {
        return ResourceKey.create(Registries.RECIPE, CraftNamespacedKey.toMinecraft(key));
    }
}
