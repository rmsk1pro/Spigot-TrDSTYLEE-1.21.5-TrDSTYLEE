package net.minecraft.world.entity.projectile;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.EntityEndermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionEntity;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import net.minecraft.world.level.dimension.WorldDimension;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class EntityEnderPearl extends EntityProjectileThrowable {

    private long ticketTimer = 0L;

    public EntityEnderPearl(EntityTypes<? extends EntityEnderPearl> entitytypes, World world) {
        super(entitytypes, world);
    }

    public EntityEnderPearl(World world, EntityLiving entityliving, ItemStack itemstack) {
        super(EntityTypes.ENDER_PEARL, entityliving, world, itemstack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void setOwnerThroughUUID(@Nullable UUID uuid) {
        this.deregisterFromCurrentOwner();
        super.setOwnerThroughUUID(uuid);
        this.registerToCurrentOwner();
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        this.deregisterFromCurrentOwner();
        super.setOwner(entity);
        this.registerToCurrentOwner();
    }

    private void deregisterFromCurrentOwner() {
        Entity entity = this.getOwner();

        if (entity instanceof EntityPlayer entityplayer) {
            entityplayer.deregisterEnderPearl(this);
        }

    }

    private void registerToCurrentOwner() {
        Entity entity = this.getOwner();

        if (entity instanceof EntityPlayer entityplayer) {
            entityplayer.registerEnderPearl(this);
        }

    }

    @Nullable
    @Override
    protected Entity findOwner(UUID uuid) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            Entity entity = super.findOwner(uuid);

            if (entity != null) {
                return entity;
            } else {
                for (WorldServer worldserver1 : worldserver.getServer().getAllLevels()) {
                    if (worldserver1 != worldserver) {
                        entity = worldserver1.getEntity(uuid);
                        if (entity != null) {
                            return entity;
                        }
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    protected void onHitEntity(MovingObjectPositionEntity movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        movingobjectpositionentity.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(MovingObjectPosition movingobjectposition) {
        super.onHit(movingobjectposition);

        for (int i = 0; i < 32; ++i) {
            this.level().addParticle(Particles.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(), this.random.nextGaussian(), 0.0D, this.random.nextGaussian());
        }

        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (!this.isRemoved()) {
                Entity entity = this.getOwner();

                if (entity != null && isAllowedToTeleportOwner(entity, worldserver)) {
                    Vec3D vec3d = this.oldPosition();

                    if (entity instanceof EntityPlayer) {
                        EntityPlayer entityplayer = (EntityPlayer) entity;

                        if (entityplayer.connection.isAcceptingMessages()) {
                            // CraftBukkit start
                            EntityPlayer entityplayer1 = entityplayer.teleport(new TeleportTransition(worldserver, vec3d, Vec3D.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING, PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
                            if (entityplayer1 == null) {
                                this.discard(EntityRemoveEvent.Cause.HIT);
                                return;
                            }
                            // CraftBukkit end
                            if (this.random.nextFloat() < 0.05F && worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                                EntityEndermite entityendermite = EntityTypes.ENDERMITE.create(worldserver, EntitySpawnReason.TRIGGERED);

                                if (entityendermite != null) {
                                    entityendermite.snapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                    worldserver.addFreshEntity(entityendermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                                }
                            }

                            if (this.isOnPortalCooldown()) {
                                entity.setPortalCooldown();
                            }

                            // EntityPlayer entityplayer1 = entityplayer.teleport(new TeleportTransition(worldserver, vec3d, Vec3D.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING)); // CraftBukkit - moved up

                            if (entityplayer1 != null) {
                                entityplayer1.resetFallDistance();
                                entityplayer1.resetCurrentImpulseContext();
                                entityplayer1.hurtServer(entityplayer.serverLevel(), this.damageSources().enderPearl().customEntityDamager(this), 5.0F); // CraftBukkit
                            }

                            this.playSound(worldserver, vec3d);
                        }
                    } else {
                        Entity entity1 = entity.teleport(new TeleportTransition(worldserver, vec3d, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));

                        if (entity1 != null) {
                            entity1.resetFallDistance();
                        }

                        this.playSound(worldserver, vec3d);
                    }

                    this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
                    return;
                }

                this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
                return;
            }
        }

    }

    private static boolean isAllowedToTeleportOwner(Entity entity, World world) {
        if (entity.level().dimension() == world.dimension()) {
            if (!(entity instanceof EntityLiving)) {
                return entity.isAlive();
            } else {
                EntityLiving entityliving = (EntityLiving) entity;

                return entityliving.isAlive() && !entityliving.isSleeping();
            }
        } else {
            return entity.canUsePortal(true);
        }
    }

    @Override
    public void tick() {
        int i;
        int j;
        Entity entity;
        label30:
        {
            i = SectionPosition.blockToSectionCoord(this.position().x());
            j = SectionPosition.blockToSectionCoord(this.position().z());
            entity = this.getOwner();
            if (entity instanceof EntityPlayer entityplayer) {
                if (!entity.isAlive() && entityplayer.serverLevel().getGameRules().getBoolean(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH)) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                    break label30;
                }
            }

            super.tick();
        }

        if (this.isAlive()) {
            BlockPosition blockposition = BlockPosition.containing(this.position());

            if ((--this.ticketTimer <= 0L || i != SectionPosition.blockToSectionCoord(blockposition.getX()) || j != SectionPosition.blockToSectionCoord(blockposition.getZ())) && entity instanceof EntityPlayer) {
                EntityPlayer entityplayer1 = (EntityPlayer) entity;

                this.ticketTimer = entityplayer1.registerAndUpdateEnderPearlTicket(this);
            }

        }
    }

    private void playSound(World world, Vec3D vec3d) {
        world.playSound((Entity) null, vec3d.x, vec3d.y, vec3d.z, SoundEffects.PLAYER_TELEPORT, SoundCategory.PLAYERS);
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleporttransition) {
        Entity entity = super.teleport(teleporttransition);

        if (entity != null) {
            entity.placePortalTicket(BlockPosition.containing(entity.position()));
        }

        return entity;
    }

    @Override
    public boolean canTeleport(World world, World world1) {
        if (world.getTypeKey() == WorldDimension.END && world1.getTypeKey() == WorldDimension.OVERWORLD) { // CraftBukkit
            Entity entity = this.getOwner();

            if (entity instanceof EntityPlayer) {
                EntityPlayer entityplayer = (EntityPlayer) entity;

                return super.canTeleport(world, world1) && entityplayer.seenCredits;
            }
        }

        return super.canTeleport(world, world1);
    }

    @Override
    protected void onInsideBlock(IBlockData iblockdata) {
        super.onInsideBlock(iblockdata);
        if (iblockdata.is(Blocks.END_GATEWAY)) {
            Entity entity = this.getOwner();

            if (entity instanceof EntityPlayer) {
                EntityPlayer entityplayer = (EntityPlayer) entity;

                entityplayer.onInsideBlock(iblockdata);
            }
        }

    }

    @Override
    public void onRemoval(Entity.RemovalReason entity_removalreason) {
        if (entity_removalreason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            this.deregisterFromCurrentOwner();
        }

        super.onRemoval(entity_removalreason);
    }

    @Override
    public void onAboveBubbleColumn(boolean flag, BlockPosition blockposition) {
        Entity.handleOnAboveBubbleColumn(this, flag, blockposition);
    }

    @Override
    public void onInsideBubbleColumn(boolean flag) {
        Entity.handleOnInsideBubbleColumn(this, flag);
    }
}
