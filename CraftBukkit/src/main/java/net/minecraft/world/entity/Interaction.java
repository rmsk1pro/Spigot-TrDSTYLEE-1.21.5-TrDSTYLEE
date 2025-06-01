package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.level.material.EnumPistonReaction;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import net.minecraft.world.damagesource.DamageSource;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityDamageEvent;
// CraftBukkit end

public class Interaction extends Entity implements Attackable, Targeting {

    private static final DataWatcherObject<Float> DATA_WIDTH_ID = DataWatcher.<Float>defineId(Interaction.class, DataWatcherRegistry.FLOAT);
    private static final DataWatcherObject<Float> DATA_HEIGHT_ID = DataWatcher.<Float>defineId(Interaction.class, DataWatcherRegistry.FLOAT);
    private static final DataWatcherObject<Boolean> DATA_RESPONSE_ID = DataWatcher.<Boolean>defineId(Interaction.class, DataWatcherRegistry.BOOLEAN);
    private static final String TAG_WIDTH = "width";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_ATTACK = "attack";
    private static final String TAG_INTERACTION = "interaction";
    private static final String TAG_RESPONSE = "response";
    private static final float DEFAULT_WIDTH = 1.0F;
    private static final float DEFAULT_HEIGHT = 1.0F;
    private static final boolean DEFAULT_RESPONSE = false;
    @Nullable
    public Interaction.PlayerAction attack;
    @Nullable
    public Interaction.PlayerAction interaction;

    public Interaction(EntityTypes<?> entitytypes, World world) {
        super(entitytypes, world);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(Interaction.DATA_WIDTH_ID, 1.0F);
        datawatcher_a.define(Interaction.DATA_HEIGHT_ID, 1.0F);
        datawatcher_a.define(Interaction.DATA_RESPONSE_ID, false);
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        this.setWidth(nbttagcompound.getFloatOr("width", 1.0F));
        this.setHeight(nbttagcompound.getFloatOr("height", 1.0F));
        this.attack = (Interaction.PlayerAction) nbttagcompound.read("attack", Interaction.PlayerAction.CODEC).orElse(null); // CraftBukkit - decompile error
        this.interaction = (Interaction.PlayerAction) nbttagcompound.read("interaction", Interaction.PlayerAction.CODEC).orElse(null); // CraftBukkit - decompile error
        this.setResponse(nbttagcompound.getBooleanOr("response", false));
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        nbttagcompound.putFloat("width", this.getWidth());
        nbttagcompound.putFloat("height", this.getHeight());
        nbttagcompound.storeNullable("attack", Interaction.PlayerAction.CODEC, this.attack);
        nbttagcompound.storeNullable("interaction", Interaction.PlayerAction.CODEC, this.interaction);
        nbttagcompound.putBoolean("response", this.getResponse());
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (Interaction.DATA_HEIGHT_ID.equals(datawatcherobject) || Interaction.DATA_WIDTH_ID.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public EnumPistonReaction getPistonPushReaction() {
        return EnumPistonReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        if (entity instanceof EntityHuman entityhuman) {
            // CraftBukkit start
            DamageSource source = entityhuman.damageSources().playerAttack(entityhuman);
            EntityDamageEvent event = CraftEventFactory.callNonLivingEntityDamageEvent(this, source, 1.0F, false);
            if (event.isCancelled()) {
                return true;
            }
            // CraftBukkit end
            this.attack = new Interaction.PlayerAction(entityhuman.getUUID(), this.level().getGameTime());
            if (entityhuman instanceof EntityPlayer entityplayer) {
                CriterionTriggers.PLAYER_HURT_ENTITY.trigger(entityplayer, this, source, (float) event.getFinalDamage(), 1.0F, false); // CraftBukkit
            }

            return !this.getResponse();
        } else {
            return false;
        }
    }

    @Override
    public final boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        return false;
    }

    @Override
    public EnumInteractionResult interact(EntityHuman entityhuman, EnumHand enumhand) {
        if (this.level().isClientSide) {
            return this.getResponse() ? EnumInteractionResult.SUCCESS : EnumInteractionResult.CONSUME;
        } else {
            this.interaction = new Interaction.PlayerAction(entityhuman.getUUID(), this.level().getGameTime());
            return EnumInteractionResult.CONSUME;
        }
    }

    @Override
    public void tick() {}

    @Nullable
    @Override
    public EntityLiving getLastAttacker() {
        return this.attack != null ? this.level().getPlayerByUUID(this.attack.player()) : null;
    }

    @Nullable
    @Override
    public EntityLiving getTarget() {
        return this.interaction != null ? this.level().getPlayerByUUID(this.interaction.player()) : null;
    }

    public void setWidth(float f) {
        this.entityData.set(Interaction.DATA_WIDTH_ID, f);
    }

    public float getWidth() {
        return (Float) this.entityData.get(Interaction.DATA_WIDTH_ID);
    }

    public void setHeight(float f) {
        this.entityData.set(Interaction.DATA_HEIGHT_ID, f);
    }

    public float getHeight() {
        return (Float) this.entityData.get(Interaction.DATA_HEIGHT_ID);
    }

    public void setResponse(boolean flag) {
        this.entityData.set(Interaction.DATA_RESPONSE_ID, flag);
    }

    public boolean getResponse() {
        return (Boolean) this.entityData.get(Interaction.DATA_RESPONSE_ID);
    }

    private EntitySize getDimensions() {
        return EntitySize.scalable(this.getWidth(), this.getHeight());
    }

    @Override
    public EntitySize getDimensions(EntityPose entitypose) {
        return this.getDimensions();
    }

    @Override
    protected AxisAlignedBB makeBoundingBox(Vec3D vec3d) {
        return this.getDimensions().makeBoundingBox(vec3d);
    }

    public static record PlayerAction(UUID player, long timestamp) {

        public static final Codec<Interaction.PlayerAction> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(UUIDUtil.CODEC.fieldOf("player").forGetter(Interaction.PlayerAction::player), Codec.LONG.fieldOf("timestamp").forGetter(Interaction.PlayerAction::timestamp)).apply(instance, Interaction.PlayerAction::new);
        });
    }
}
