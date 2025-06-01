package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.world.entity.vehicle.EntityMinecartTNT;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.minecart.ExplosiveMinecart;

public final class CraftMinecartTNT extends CraftMinecart implements ExplosiveMinecart {
    CraftMinecartTNT(CraftServer server, EntityMinecartTNT entity) {
        super(server, entity);
    }

    @Override
    public float getYield() {
        return getHandle().explosionPowerBase;
    }

    @Override
    public boolean isIncendiary() {
        return getHandle().isIncendiary;
    }

    @Override
    public void setIsIncendiary(boolean isIncendiary) {
        getHandle().isIncendiary = isIncendiary;
    }

    @Override
    public void setYield(float yield) {
        getHandle().explosionPowerBase = yield;
    }

    @Override
    public float getExplosionSpeedFactor() {
        return getHandle().explosionSpeedFactor;
    }

    @Override
    public void setExplosionSpeedFactor(float factor) {
        getHandle().explosionSpeedFactor = factor;
    }

    @Override
    public void setFuseTicks(int ticks) {
        getHandle().fuse = ticks;
    }

    @Override
    public int getFuseTicks() {
        return getHandle().getFuse();
    }

    @Override
    public void ignite() {
        getHandle().primeFuse(null);
    }

    @Override
    public boolean isIgnited() {
        return getHandle().isPrimed();
    }

    @Override
    public void explode() {
        getHandle().explode(getHandle().getDeltaMovement().horizontalDistanceSqr());
    }

    @Override
    public void explode(double power) {
        Preconditions.checkArgument(0 <= power && power <= 5, "Power must be in range [0, 5] (got %s)", power);

        getHandle().explode(power);
    }

    @Override
    public EntityMinecartTNT getHandle() {
        return (EntityMinecartTNT) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftMinecartTNT";
    }
}
