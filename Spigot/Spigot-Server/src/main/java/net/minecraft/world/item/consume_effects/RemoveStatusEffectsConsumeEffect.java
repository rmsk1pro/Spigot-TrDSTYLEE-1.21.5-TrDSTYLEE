package net.minecraft.world.item.consume_effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectList;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;

// CraftBukkit start
import org.bukkit.event.entity.EntityPotionEffectEvent;
// CraftBukkit end

public record RemoveStatusEffectsConsumeEffect(HolderSet<MobEffectList> effects) implements ConsumeEffect {

    public static final MapCodec<RemoveStatusEffectsConsumeEffect> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(RegistryCodecs.homogeneousList(Registries.MOB_EFFECT).fieldOf("effects").forGetter(RemoveStatusEffectsConsumeEffect::effects)).apply(instance, RemoveStatusEffectsConsumeEffect::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveStatusEffectsConsumeEffect> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.holderSet(Registries.MOB_EFFECT), RemoveStatusEffectsConsumeEffect::effects, RemoveStatusEffectsConsumeEffect::new);

    public RemoveStatusEffectsConsumeEffect(Holder<MobEffectList> holder) {
        this(HolderSet.direct(holder));
    }

    @Override
    public ConsumeEffect.a<RemoveStatusEffectsConsumeEffect> getType() {
        return ConsumeEffect.a.REMOVE_EFFECTS;
    }

    @Override
    public boolean apply(World world, ItemStack itemstack, EntityLiving entityliving, EntityPotionEffectEvent.Cause cause) { // CraftBukkit
        boolean flag = false;

        for (Holder<MobEffectList> holder : this.effects) {
            if (entityliving.removeEffect(holder, cause)) { // CraftBukkit
                flag = true;
            }
        }

        return flag;
    }
}
