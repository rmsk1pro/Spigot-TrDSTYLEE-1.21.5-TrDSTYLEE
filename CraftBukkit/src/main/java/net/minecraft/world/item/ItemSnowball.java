package net.minecraft.world.item;

import net.minecraft.core.EnumDirection;
import net.minecraft.core.IPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntitySnowball;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.level.World;

public class ItemSnowball extends Item implements ProjectileItem {

    public static float PROJECTILE_SHOOT_POWER = 1.5F;

    public ItemSnowball(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public EnumInteractionResult use(World world, EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        // CraftBukkit start - moved down
        // world.playSound((Entity) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
        if (world instanceof WorldServer worldserver) {
            if (IProjectile.spawnProjectileFromRotation(EntitySnowball::new, worldserver, itemstack, entityhuman, 0.0F, ItemSnowball.PROJECTILE_SHOOT_POWER, 1.0F).isAlive()) {
                itemstack.consume(1, entityhuman);

                world.playSound((Entity) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            } else if (entityhuman instanceof net.minecraft.server.level.EntityPlayer) {
                ((net.minecraft.server.level.EntityPlayer) entityhuman).getBukkitEntity().updateInventory();
            }
            // CraftBukkit end
        }

        entityhuman.awardStat(StatisticList.ITEM_USED.get(this));
        // itemstack.consume(1, entityhuman); // CraftBukkit - moved up
        return EnumInteractionResult.SUCCESS;
    }

    @Override
    public IProjectile asProjectile(World world, IPosition iposition, ItemStack itemstack, EnumDirection enumdirection) {
        return new EntitySnowball(world, iposition.x(), iposition.y(), iposition.z(), itemstack);
    }
}
