package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.IMaterial;
import net.minecraft.world.level.World;

public class EntitySkeleton extends EntitySkeletonAbstract {

    private static final int TOTAL_CONVERSION_TIME = 300;
    public static final DataWatcherObject<Boolean> DATA_STRAY_CONVERSION_ID = DataWatcher.<Boolean>defineId(EntitySkeleton.class, DataWatcherRegistry.BOOLEAN);
    public static final String CONVERSION_TAG = "StrayConversionTime";
    private static final int NOT_CONVERTING = -1;
    private int inPowderSnowTime;
    public int conversionTime;

    public EntitySkeleton(EntityTypes<? extends EntitySkeleton> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntitySkeleton.DATA_STRAY_CONVERSION_ID, false);
    }

    public boolean isFreezeConverting() {
        return (Boolean) this.getEntityData().get(EntitySkeleton.DATA_STRAY_CONVERSION_ID);
    }

    public void setFreezeConverting(boolean flag) {
        this.entityData.set(EntitySkeleton.DATA_STRAY_CONVERSION_ID, flag);
    }

    @Override
    public boolean isShaking() {
        return this.isFreezeConverting();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isInPowderSnow) {
                if (this.isFreezeConverting()) {
                    --this.conversionTime;
                    if (this.conversionTime < 0) {
                        this.doFreezeConversion();
                    }
                } else {
                    ++this.inPowderSnowTime;
                    if (this.inPowderSnowTime >= 140) {
                        this.startFreezeConversion(300);
                    }
                }
            } else {
                this.inPowderSnowTime = -1;
                this.setFreezeConverting(false);
            }
        }

        super.tick();
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("StrayConversionTime", this.isFreezeConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        int i = nbttagcompound.getIntOr("StrayConversionTime", -1);

        if (i != -1) {
            this.startFreezeConversion(i);
        } else {
            this.setFreezeConverting(false);
        }

    }

    @VisibleForTesting
    public void startFreezeConversion(int i) {
        this.conversionTime = i;
        this.setFreezeConverting(true);
    }

    protected void doFreezeConversion() {
        net.minecraft.world.entity.monster.EntitySkeletonStray converted = this.convertTo(EntityTypes.STRAY, ConversionParams.single(this, true, true), (entityskeletonstray) -> { // CraftBukkit
            if (!this.isSilent()) {
                this.level().levelEvent((Entity) null, 1048, this.blockPosition(), 0);
            }

        // CraftBukkit start
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.FROZEN, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.FROZEN);
        if (converted == null) {
            ((org.bukkit.entity.Skeleton) getBukkitEntity()).setConversionTime(-1); // CraftBukkit - SPIGOT-7997: End conversion to stop event spam
        }
        // CraftBukkit end
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.SKELETON_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.SKELETON_DEATH;
    }

    @Override
    SoundEffect getStepSound() {
        return SoundEffects.SKELETON_STEP;
    }

    @Override
    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropCustomDeathLoot(worldserver, damagesource, flag);
        Entity entity = damagesource.getEntity();

        if (entity instanceof EntityCreeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                entitycreeper.increaseDroppedSkulls();
                this.spawnAtLocation(worldserver, (IMaterial) Items.SKELETON_SKULL);
            }
        }

    }
}
