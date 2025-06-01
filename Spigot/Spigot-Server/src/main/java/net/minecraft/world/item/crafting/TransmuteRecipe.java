package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.World;

// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftTransmuteRecipe;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class TransmuteRecipe implements RecipeCrafting {

    final String group;
    final CraftingBookCategory category;
    final RecipeItemStack input;
    final RecipeItemStack material;
    final TransmuteResult result;
    @Nullable
    private PlacementInfo placementInfo;

    public TransmuteRecipe(String s, CraftingBookCategory craftingbookcategory, RecipeItemStack recipeitemstack, RecipeItemStack recipeitemstack1, TransmuteResult transmuteresult) {
        this.group = s;
        this.category = craftingbookcategory;
        this.input = recipeitemstack;
        this.material = recipeitemstack1;
        this.result = transmuteresult;
    }

    public boolean matches(CraftingInput craftinginput, World world) {
        if (craftinginput.ingredientCount() != 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < craftinginput.size(); ++i) {
                ItemStack itemstack = craftinginput.getItem(i);

                if (!itemstack.isEmpty()) {
                    if (!flag && this.input.test(itemstack)) {
                        if (this.result.isResultUnchanged(itemstack)) {
                            return false;
                        }

                        flag = true;
                    } else {
                        if (flag1 || !this.material.test(itemstack)) {
                            return false;
                        }

                        flag1 = true;
                    }
                }
            }

            return flag && flag1;
        }
    }

    public ItemStack assemble(CraftingInput craftinginput, HolderLookup.a holderlookup_a) {
        for (int i = 0; i < craftinginput.size(); ++i) {
            ItemStack itemstack = craftinginput.getItem(i);

            if (!itemstack.isEmpty() && this.input.test(itemstack)) {
                return this.result.apply(itemstack);
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(List.of(this.input.display(), this.material.display()), this.result.display(), new SlotDisplay.d(Items.CRAFTING_TABLE)));
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        return new CraftTransmuteRecipe(id, CraftRecipe.toBukkit(this.result), CraftRecipe.toBukkit(this.input), CraftRecipe.toBukkit(this.material));
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<TransmuteRecipe> getSerializer() {
        return RecipeSerializer.TRANSMUTE;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(List.of(this.input, this.material));
        }

        return this.placementInfo;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    public static class a implements RecipeSerializer<TransmuteRecipe> {

        private static final MapCodec<TransmuteRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.STRING.optionalFieldOf("group", "").forGetter((transmuterecipe) -> {
                return transmuterecipe.group;
            }), CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter((transmuterecipe) -> {
                return transmuterecipe.category;
            }), RecipeItemStack.CODEC.fieldOf("input").forGetter((transmuterecipe) -> {
                return transmuterecipe.input;
            }), RecipeItemStack.CODEC.fieldOf("material").forGetter((transmuterecipe) -> {
                return transmuterecipe.material;
            }), TransmuteResult.CODEC.fieldOf("result").forGetter((transmuterecipe) -> {
                return transmuterecipe.result;
            })).apply(instance, TransmuteRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, (transmuterecipe) -> {
            return transmuterecipe.group;
        }, CraftingBookCategory.STREAM_CODEC, (transmuterecipe) -> {
            return transmuterecipe.category;
        }, RecipeItemStack.CONTENTS_STREAM_CODEC, (transmuterecipe) -> {
            return transmuterecipe.input;
        }, RecipeItemStack.CONTENTS_STREAM_CODEC, (transmuterecipe) -> {
            return transmuterecipe.material;
        }, TransmuteResult.STREAM_CODEC, (transmuterecipe) -> {
            return transmuterecipe.result;
        }, TransmuteRecipe::new);

        public a() {}

        @Override
        public MapCodec<TransmuteRecipe> codec() {
            return TransmuteRecipe.a.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> streamCodec() {
            return TransmuteRecipe.a.STREAM_CODEC;
        }
    }
}
