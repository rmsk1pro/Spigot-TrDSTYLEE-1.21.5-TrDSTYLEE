package net.minecraft.world.entity.decoration;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Vector3f;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleParamBlock;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLightning;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.EnumMainHand;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.vehicle.EntityMinecartAbstract;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.EnumPistonReaction;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
// CraftBukkit end

public class EntityArmorStand extends EntityLiving {

    public static final int WOBBLE_TIME = 5;
    private static final boolean ENABLE_ARMS = true;
    public static final Vector3f DEFAULT_HEAD_POSE = new Vector3f(0.0F, 0.0F, 0.0F);
    public static final Vector3f DEFAULT_BODY_POSE = new Vector3f(0.0F, 0.0F, 0.0F);
    public static final Vector3f DEFAULT_LEFT_ARM_POSE = new Vector3f(-10.0F, 0.0F, -10.0F);
    public static final Vector3f DEFAULT_RIGHT_ARM_POSE = new Vector3f(-15.0F, 0.0F, 10.0F);
    public static final Vector3f DEFAULT_LEFT_LEG_POSE = new Vector3f(-1.0F, 0.0F, -1.0F);
    public static final Vector3f DEFAULT_RIGHT_LEG_POSE = new Vector3f(1.0F, 0.0F, 1.0F);
    private static final EntitySize MARKER_DIMENSIONS = EntitySize.fixed(0.0F, 0.0F);
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.ARMOR_STAND.getDimensions().scale(0.5F).withEyeHeight(0.9875F);
    private static final double FEET_OFFSET = 0.1D;
    private static final double CHEST_OFFSET = 0.9D;
    private static final double LEGS_OFFSET = 0.4D;
    private static final double HEAD_OFFSET = 1.6D;
    public static final int DISABLE_TAKING_OFFSET = 8;
    public static final int DISABLE_PUTTING_OFFSET = 16;
    public static final int CLIENT_FLAG_SMALL = 1;
    public static final int CLIENT_FLAG_SHOW_ARMS = 4;
    public static final int CLIENT_FLAG_NO_BASEPLATE = 8;
    public static final int CLIENT_FLAG_MARKER = 16;
    public static final DataWatcherObject<Byte> DATA_CLIENT_FLAGS = DataWatcher.<Byte>defineId(EntityArmorStand.class, DataWatcherRegistry.BYTE);
    public static final DataWatcherObject<Vector3f> DATA_HEAD_POSE = DataWatcher.<Vector3f>defineId(EntityArmorStand.class, DataWatcherRegistry.ROTATIONS);
    public static final DataWatcherObject<Vector3f> DATA_BODY_POSE = DataWatcher.<Vector3f>defineId(EntityArmorStand.class, DataWatcherRegistry.ROTATIONS);
    public static final DataWatcherObject<Vector3f> DATA_LEFT_ARM_POSE = DataWatcher.<Vector3f>defineId(EntityArmorStand.class, DataWatcherRegistry.ROTATIONS);
    public static final DataWatcherObject<Vector3f> DATA_RIGHT_ARM_POSE = DataWatcher.<Vector3f>defineId(EntityArmorStand.class, DataWatcherRegistry.ROTATIONS);
    public static final DataWatcherObject<Vector3f> DATA_LEFT_LEG_POSE = DataWatcher.<Vector3f>defineId(EntityArmorStand.class, DataWatcherRegistry.ROTATIONS);
    public static final DataWatcherObject<Vector3f> DATA_RIGHT_LEG_POSE = DataWatcher.<Vector3f>defineId(EntityArmorStand.class, DataWatcherRegistry.ROTATIONS);
    private static final Predicate<Entity> RIDABLE_MINECARTS = (entity) -> {
        boolean flag;

        if (entity instanceof EntityMinecartAbstract entityminecartabstract) {
            if (entityminecartabstract.isRideable()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    };
    private static final boolean DEFAULT_INVISIBLE = false;
    private static final int DEFAULT_DISABLED_SLOTS = 0;
    private static final boolean DEFAULT_SMALL = false;
    private static final boolean DEFAULT_SHOW_ARMS = false;
    private static final boolean DEFAULT_NO_BASE_PLATE = false;
    private static final boolean DEFAULT_MARKER = false;
    private boolean invisible;
    public long lastHit;
    public int disabledSlots;
    public Vector3f headPose;
    public Vector3f bodyPose;
    public Vector3f leftArmPose;
    public Vector3f rightArmPose;
    public Vector3f leftLegPose;
    public Vector3f rightLegPose;

    public EntityArmorStand(EntityTypes<? extends EntityArmorStand> entitytypes, World world) {
        super(entitytypes, world);
        this.invisible = false;
        this.disabledSlots = 0;
        this.headPose = EntityArmorStand.DEFAULT_HEAD_POSE;
        this.bodyPose = EntityArmorStand.DEFAULT_BODY_POSE;
        this.leftArmPose = EntityArmorStand.DEFAULT_LEFT_ARM_POSE;
        this.rightArmPose = EntityArmorStand.DEFAULT_RIGHT_ARM_POSE;
        this.leftLegPose = EntityArmorStand.DEFAULT_LEFT_LEG_POSE;
        this.rightLegPose = EntityArmorStand.DEFAULT_RIGHT_LEG_POSE;
    }

    public EntityArmorStand(World world, double d0, double d1, double d2) {
        this(EntityTypes.ARMOR_STAND, world);
        this.setPos(d0, d1, d2);
    }

    public static AttributeProvider.Builder createAttributes() {
        return createLivingAttributes().add(GenericAttributes.STEP_HEIGHT, 0.0D);
    }

    // CraftBukkit start - SPIGOT-3607, SPIGOT-3637
    @Override
    public float getBukkitYaw() {
        return this.getYRot();
    }
    // CraftBukkit end

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    private boolean hasPhysics() {
        return !this.isMarker() && !this.isNoGravity();
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.hasPhysics();
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityArmorStand.DATA_CLIENT_FLAGS, (byte) 0);
        datawatcher_a.define(EntityArmorStand.DATA_HEAD_POSE, EntityArmorStand.DEFAULT_HEAD_POSE);
        datawatcher_a.define(EntityArmorStand.DATA_BODY_POSE, EntityArmorStand.DEFAULT_BODY_POSE);
        datawatcher_a.define(EntityArmorStand.DATA_LEFT_ARM_POSE, EntityArmorStand.DEFAULT_LEFT_ARM_POSE);
        datawatcher_a.define(EntityArmorStand.DATA_RIGHT_ARM_POSE, EntityArmorStand.DEFAULT_RIGHT_ARM_POSE);
        datawatcher_a.define(EntityArmorStand.DATA_LEFT_LEG_POSE, EntityArmorStand.DEFAULT_LEFT_LEG_POSE);
        datawatcher_a.define(EntityArmorStand.DATA_RIGHT_LEG_POSE, EntityArmorStand.DEFAULT_RIGHT_LEG_POSE);
    }

    @Override
    public boolean canUseSlot(EnumItemSlot enumitemslot) {
        return enumitemslot != EnumItemSlot.BODY && enumitemslot != EnumItemSlot.SADDLE && !this.isDisabled(enumitemslot);
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("Invisible", this.isInvisible());
        nbttagcompound.putBoolean("Small", this.isSmall());
        nbttagcompound.putBoolean("ShowArms", this.showArms());
        nbttagcompound.putInt("DisabledSlots", this.disabledSlots);
        nbttagcompound.putBoolean("NoBasePlate", !this.showBasePlate());
        if (this.isMarker()) {
            nbttagcompound.putBoolean("Marker", this.isMarker());
        }

        nbttagcompound.put("Pose", this.writePose());
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setInvisible(nbttagcompound.getBooleanOr("Invisible", false));
        this.setSmall(nbttagcompound.getBooleanOr("Small", false));
        this.setShowArms(nbttagcompound.getBooleanOr("ShowArms", false));
        this.disabledSlots = nbttagcompound.getIntOr("DisabledSlots", 0);
        this.setNoBasePlate(nbttagcompound.getBooleanOr("NoBasePlate", false));
        this.setMarker(nbttagcompound.getBooleanOr("Marker", false));
        this.noPhysics = !this.hasPhysics();
        this.readPose(nbttagcompound.getCompoundOrEmpty("Pose"));
    }

    private void readPose(NBTTagCompound nbttagcompound) {
        this.setHeadPose((Vector3f) nbttagcompound.read("Head", Vector3f.CODEC).orElse(EntityArmorStand.DEFAULT_HEAD_POSE));
        this.setBodyPose((Vector3f) nbttagcompound.read("Body", Vector3f.CODEC).orElse(EntityArmorStand.DEFAULT_BODY_POSE));
        this.setLeftArmPose((Vector3f) nbttagcompound.read("LeftArm", Vector3f.CODEC).orElse(EntityArmorStand.DEFAULT_LEFT_ARM_POSE));
        this.setRightArmPose((Vector3f) nbttagcompound.read("RightArm", Vector3f.CODEC).orElse(EntityArmorStand.DEFAULT_RIGHT_ARM_POSE));
        this.setLeftLegPose((Vector3f) nbttagcompound.read("LeftLeg", Vector3f.CODEC).orElse(EntityArmorStand.DEFAULT_LEFT_LEG_POSE));
        this.setRightLegPose((Vector3f) nbttagcompound.read("RightLeg", Vector3f.CODEC).orElse(EntityArmorStand.DEFAULT_RIGHT_LEG_POSE));
    }

    private NBTTagCompound writePose() {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        if (!EntityArmorStand.DEFAULT_HEAD_POSE.equals(this.headPose)) {
            nbttagcompound.store("Head", Vector3f.CODEC, this.headPose);
        }

        if (!EntityArmorStand.DEFAULT_BODY_POSE.equals(this.bodyPose)) {
            nbttagcompound.store("Body", Vector3f.CODEC, this.bodyPose);
        }

        if (!EntityArmorStand.DEFAULT_LEFT_ARM_POSE.equals(this.leftArmPose)) {
            nbttagcompound.store("LeftArm", Vector3f.CODEC, this.leftArmPose);
        }

        if (!EntityArmorStand.DEFAULT_RIGHT_ARM_POSE.equals(this.rightArmPose)) {
            nbttagcompound.store("RightArm", Vector3f.CODEC, this.rightArmPose);
        }

        if (!EntityArmorStand.DEFAULT_LEFT_LEG_POSE.equals(this.leftLegPose)) {
            nbttagcompound.store("LeftLeg", Vector3f.CODEC, this.leftLegPose);
        }

        if (!EntityArmorStand.DEFAULT_RIGHT_LEG_POSE.equals(this.rightLegPose)) {
            nbttagcompound.store("RightLeg", Vector3f.CODEC, this.rightLegPose);
        }

        return nbttagcompound;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox(), EntityArmorStand.RIDABLE_MINECARTS)) {
            if (this.distanceToSqr(entity) <= 0.2D) {
                entity.push((Entity) this);
            }
        }

    }

    @Override
    public EnumInteractionResult interactAt(EntityHuman entityhuman, Vec3D vec3d, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (!this.isMarker() && !itemstack.is(Items.NAME_TAG)) {
            if (entityhuman.isSpectator()) {
                return EnumInteractionResult.SUCCESS;
            } else if (entityhuman.level().isClientSide) {
                return EnumInteractionResult.SUCCESS_SERVER;
            } else {
                EnumItemSlot enumitemslot = this.getEquipmentSlotForItem(itemstack);

                if (itemstack.isEmpty()) {
                    EnumItemSlot enumitemslot1 = this.getClickedSlot(vec3d);
                    EnumItemSlot enumitemslot2 = this.isDisabled(enumitemslot1) ? enumitemslot : enumitemslot1;

                    if (this.hasItemInSlot(enumitemslot2) && this.swapItem(entityhuman, enumitemslot2, itemstack, enumhand)) {
                        return EnumInteractionResult.SUCCESS_SERVER;
                    }
                } else {
                    if (this.isDisabled(enumitemslot)) {
                        return EnumInteractionResult.FAIL;
                    }

                    if (enumitemslot.getType() == EnumItemSlot.Function.HAND && !this.showArms()) {
                        return EnumInteractionResult.FAIL;
                    }

                    if (this.swapItem(entityhuman, enumitemslot, itemstack, enumhand)) {
                        return EnumInteractionResult.SUCCESS_SERVER;
                    }
                }

                return EnumInteractionResult.PASS;
            }
        } else {
            return EnumInteractionResult.PASS;
        }
    }

    private EnumItemSlot getClickedSlot(Vec3D vec3d) {
        EnumItemSlot enumitemslot = EnumItemSlot.MAINHAND;
        boolean flag = this.isSmall();
        double d0 = vec3d.y / (double) (this.getScale() * this.getAgeScale());
        EnumItemSlot enumitemslot1 = EnumItemSlot.FEET;

        if (d0 >= 0.1D && d0 < 0.1D + (flag ? 0.8D : 0.45D) && this.hasItemInSlot(enumitemslot1)) {
            enumitemslot = EnumItemSlot.FEET;
        } else if (d0 >= 0.9D + (flag ? 0.3D : 0.0D) && d0 < 0.9D + (flag ? 1.0D : 0.7D) && this.hasItemInSlot(EnumItemSlot.CHEST)) {
            enumitemslot = EnumItemSlot.CHEST;
        } else if (d0 >= 0.4D && d0 < 0.4D + (flag ? 1.0D : 0.8D) && this.hasItemInSlot(EnumItemSlot.LEGS)) {
            enumitemslot = EnumItemSlot.LEGS;
        } else if (d0 >= 1.6D && this.hasItemInSlot(EnumItemSlot.HEAD)) {
            enumitemslot = EnumItemSlot.HEAD;
        } else if (!this.hasItemInSlot(EnumItemSlot.MAINHAND) && this.hasItemInSlot(EnumItemSlot.OFFHAND)) {
            enumitemslot = EnumItemSlot.OFFHAND;
        }

        return enumitemslot;
    }

    private boolean isDisabled(EnumItemSlot enumitemslot) {
        return (this.disabledSlots & 1 << enumitemslot.getFilterBit(0)) != 0 || enumitemslot.getType() == EnumItemSlot.Function.HAND && !this.showArms();
    }

    private boolean swapItem(EntityHuman entityhuman, EnumItemSlot enumitemslot, ItemStack itemstack, EnumHand enumhand) {
        ItemStack itemstack1 = this.getItemBySlot(enumitemslot);

        if (!itemstack1.isEmpty() && (this.disabledSlots & 1 << enumitemslot.getFilterBit(8)) != 0) {
            return false;
        } else if (itemstack1.isEmpty() && (this.disabledSlots & 1 << enumitemslot.getFilterBit(16)) != 0) {
            return false;
            // CraftBukkit start
        } else {
            org.bukkit.inventory.ItemStack armorStandItem = CraftItemStack.asCraftMirror(itemstack1);
            org.bukkit.inventory.ItemStack playerHeldItem = CraftItemStack.asCraftMirror(itemstack);

            Player player = (Player) entityhuman.getBukkitEntity();
            ArmorStand self = (ArmorStand) this.getBukkitEntity();

            EquipmentSlot slot = CraftEquipmentSlot.getSlot(enumitemslot);
            EquipmentSlot hand = CraftEquipmentSlot.getHand(enumhand);
            PlayerArmorStandManipulateEvent armorStandManipulateEvent = new PlayerArmorStandManipulateEvent(player, self, playerHeldItem, armorStandItem, slot, hand);
            this.level().getCraftServer().getPluginManager().callEvent(armorStandManipulateEvent);

            if (armorStandManipulateEvent.isCancelled()) {
                return true;
            }

        if (entityhuman.hasInfiniteMaterials() && itemstack1.isEmpty() && !itemstack.isEmpty()) {
            // CraftBukkit end
            this.setItemSlot(enumitemslot, itemstack.copyWithCount(1));
            return true;
        } else if (!itemstack.isEmpty() && itemstack.getCount() > 1) {
            if (!itemstack1.isEmpty()) {
                return false;
            } else {
                this.setItemSlot(enumitemslot, itemstack.split(1));
                return true;
            }
        } else {
            this.setItemSlot(enumitemslot, itemstack);
            entityhuman.setItemInHand(enumhand, itemstack1);
            return true;
        }
        } // CraftBukkit
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isRemoved()) {
            return false;
        } else if (!worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && damagesource.getEntity() instanceof EntityInsentient) {
            return false;
        } else if (damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f)) {
                return false;
            }
            this.kill(worldserver, damagesource); // CraftBukkit
            // CraftBukkit end
            return false;
        } else if (!this.isInvulnerableTo(worldserver, damagesource) && (true || !this.invisible) && !this.isMarker()) { // CraftBukkit
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f, true, this.invisible)) {
                return false;
            }
            // CraftBukkit end
            if (damagesource.is(DamageTypeTags.IS_EXPLOSION)) {
                this.brokenByAnything(worldserver, damagesource);
                this.kill(worldserver, damagesource); // CraftBukkit
                return false;
            } else if (damagesource.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
                if (this.isOnFire()) {
                    this.causeDamage(worldserver, damagesource, 0.15F);
                } else {
                    this.igniteForSeconds(5.0F);
                }

                return false;
            } else if (damagesource.is(DamageTypeTags.BURNS_ARMOR_STANDS) && this.getHealth() > 0.5F) {
                this.causeDamage(worldserver, damagesource, 4.0F);
                return false;
            } else {
                boolean flag = damagesource.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
                boolean flag1 = damagesource.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);

                if (!flag && !flag1) {
                    return false;
                } else {
                    Entity entity = damagesource.getEntity();

                    if (entity instanceof EntityHuman) {
                        EntityHuman entityhuman = (EntityHuman) entity;

                        if (!entityhuman.getAbilities().mayBuild) {
                            return false;
                        }
                    }

                    if (damagesource.isCreativePlayer()) {
                        this.playBrokenSound();
                        this.showBreakingParticles();
                        this.kill(worldserver, damagesource); // CraftBukkit
                        return true;
                    } else {
                        long i = worldserver.getGameTime();

                        if (i - this.lastHit > 5L && !flag1) {
                            worldserver.broadcastEntityEvent(this, (byte) 32);
                            this.gameEvent(GameEvent.ENTITY_DAMAGE, damagesource.getEntity());
                            this.lastHit = i;
                        } else {
                            this.brokenByPlayer(worldserver, damagesource);
                            this.showBreakingParticles();
                            this.discard(EntityRemoveEvent.Cause.DEATH); // CraftBukkit - SPIGOT-4890: remain as this.discard() since above damagesource method will call death event
                        }

                        return true;
                    }
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 32) {
            if (this.level().isClientSide) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.ARMOR_STAND_HIT, this.getSoundSource(), 0.3F, 1.0F, false);
                this.lastHit = this.level().getGameTime();
            }
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1) || d1 == 0.0D) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return d0 < d1 * d1;
    }

    private void showBreakingParticles() {
        if (this.level() instanceof WorldServer) {
            ((WorldServer) this.level()).sendParticles(new ParticleParamBlock(Particles.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), this.getX(), this.getY(0.6666666666666666D), this.getZ(), 10, (double) (this.getBbWidth() / 4.0F), (double) (this.getBbHeight() / 4.0F), (double) (this.getBbWidth() / 4.0F), 0.05D);
        }

    }

    private void causeDamage(WorldServer worldserver, DamageSource damagesource, float f) {
        float f1 = this.getHealth();

        f1 -= f;
        if (f1 <= 0.5F) {
            this.brokenByAnything(worldserver, damagesource);
            this.kill(worldserver, damagesource); // CraftBukkit
        } else {
            this.setHealth(f1);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damagesource.getEntity());
        }

    }

    private void brokenByPlayer(WorldServer worldserver, DamageSource damagesource) {
        ItemStack itemstack = new ItemStack(Items.ARMOR_STAND);

        itemstack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack)); // CraftBukkit - add to drops
        this.brokenByAnything(worldserver, damagesource);
    }

    private void brokenByAnything(WorldServer worldserver, DamageSource damagesource) {
        this.playBrokenSound();
        // this.dropAllDeathLoot(worldserver, damagesource); // CraftBukkit - moved down

        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            ItemStack itemstack = this.equipment.set(enumitemslot, ItemStack.EMPTY);

            if (!itemstack.isEmpty()) {
                drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack)); // CraftBukkit - add to drops
            }
        }
        this.dropAllDeathLoot(worldserver, damagesource); // CraftBukkit - moved from above

    }

    private void playBrokenSound() {
        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    protected void tickHeadTurn(float f) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.getYRot();
    }

    @Override
    public void travel(Vec3D vec3d) {
        if (this.hasPhysics()) {
            super.travel(vec3d);
        }
    }

    @Override
    public void setYBodyRot(float f) {
        this.yBodyRotO = this.yRotO = f;
        this.yHeadRotO = this.yHeadRot = f;
    }

    @Override
    public void setYHeadRot(float f) {
        this.yBodyRotO = this.yRotO = f;
        this.yHeadRotO = this.yHeadRot = f;
    }

    @Override
    public void tick() {
        super.tick();
        Vector3f vector3f = (Vector3f) this.entityData.get(EntityArmorStand.DATA_HEAD_POSE);

        if (!this.headPose.equals(vector3f)) {
            this.setHeadPose(vector3f);
        }

        Vector3f vector3f1 = (Vector3f) this.entityData.get(EntityArmorStand.DATA_BODY_POSE);

        if (!this.bodyPose.equals(vector3f1)) {
            this.setBodyPose(vector3f1);
        }

        Vector3f vector3f2 = (Vector3f) this.entityData.get(EntityArmorStand.DATA_LEFT_ARM_POSE);

        if (!this.leftArmPose.equals(vector3f2)) {
            this.setLeftArmPose(vector3f2);
        }

        Vector3f vector3f3 = (Vector3f) this.entityData.get(EntityArmorStand.DATA_RIGHT_ARM_POSE);

        if (!this.rightArmPose.equals(vector3f3)) {
            this.setRightArmPose(vector3f3);
        }

        Vector3f vector3f4 = (Vector3f) this.entityData.get(EntityArmorStand.DATA_LEFT_LEG_POSE);

        if (!this.leftLegPose.equals(vector3f4)) {
            this.setLeftLegPose(vector3f4);
        }

        Vector3f vector3f5 = (Vector3f) this.entityData.get(EntityArmorStand.DATA_RIGHT_LEG_POSE);

        if (!this.rightLegPose.equals(vector3f5)) {
            this.setRightLegPose(vector3f5);
        }

    }

    @Override
    protected void updateInvisibilityStatus() {
        this.setInvisible(this.invisible);
    }

    @Override
    public void setInvisible(boolean flag) {
        this.invisible = flag;
        super.setInvisible(flag);
    }

    @Override
    public boolean isBaby() {
        return this.isSmall();
    }

    // CraftBukkit start
    @Override
    public boolean shouldDropExperience() {
        return true; // MC-157395, SPIGOT-5193 even baby (small) armor stands should drop
    }
    // CraftBukkit end

    @Override
    public void kill(WorldServer worldserver) {
        // CraftBukkit start - pass DamageSource for kill
        this.kill(worldserver, null);
    }

    public void kill(WorldServer worldserver, DamageSource damageSource) {
        org.bukkit.craftbukkit.event.CraftEventFactory.callEntityDeathEvent(this, (damageSource == null ? this.damageSources().genericKill() : damageSource), drops); // CraftBukkit - call event
        this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        // CraftBukkit end
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.shouldAffectBlocklikeEntities() ? this.isInvisible() : true;
    }

    @Override
    public EnumPistonReaction getPistonPushReaction() {
        return this.isMarker() ? EnumPistonReaction.IGNORE : super.getPistonPushReaction();
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return this.isMarker();
    }

    public void setSmall(boolean flag) {
        this.entityData.set(EntityArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS), 1, flag));
    }

    public boolean isSmall() {
        return ((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS) & 1) != 0;
    }

    public void setShowArms(boolean flag) {
        this.entityData.set(EntityArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS), 4, flag));
    }

    public boolean showArms() {
        return ((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS) & 4) != 0;
    }

    public void setNoBasePlate(boolean flag) {
        this.entityData.set(EntityArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS), 8, flag));
    }

    public boolean showBasePlate() {
        return ((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS) & 8) == 0;
    }

    public void setMarker(boolean flag) {
        this.entityData.set(EntityArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS), 16, flag));
    }

    public boolean isMarker() {
        return ((Byte) this.entityData.get(EntityArmorStand.DATA_CLIENT_FLAGS) & 16) != 0;
    }

    private byte setBit(byte b0, int i, boolean flag) {
        if (flag) {
            b0 = (byte) (b0 | i);
        } else {
            b0 = (byte) (b0 & ~i);
        }

        return b0;
    }

    public void setHeadPose(Vector3f vector3f) {
        this.headPose = vector3f;
        this.entityData.set(EntityArmorStand.DATA_HEAD_POSE, vector3f);
    }

    public void setBodyPose(Vector3f vector3f) {
        this.bodyPose = vector3f;
        this.entityData.set(EntityArmorStand.DATA_BODY_POSE, vector3f);
    }

    public void setLeftArmPose(Vector3f vector3f) {
        this.leftArmPose = vector3f;
        this.entityData.set(EntityArmorStand.DATA_LEFT_ARM_POSE, vector3f);
    }

    public void setRightArmPose(Vector3f vector3f) {
        this.rightArmPose = vector3f;
        this.entityData.set(EntityArmorStand.DATA_RIGHT_ARM_POSE, vector3f);
    }

    public void setLeftLegPose(Vector3f vector3f) {
        this.leftLegPose = vector3f;
        this.entityData.set(EntityArmorStand.DATA_LEFT_LEG_POSE, vector3f);
    }

    public void setRightLegPose(Vector3f vector3f) {
        this.rightLegPose = vector3f;
        this.entityData.set(EntityArmorStand.DATA_RIGHT_LEG_POSE, vector3f);
    }

    public Vector3f getHeadPose() {
        return this.headPose;
    }

    public Vector3f getBodyPose() {
        return this.bodyPose;
    }

    public Vector3f getLeftArmPose() {
        return this.leftArmPose;
    }

    public Vector3f getRightArmPose() {
        return this.rightArmPose;
    }

    public Vector3f getLeftLegPose() {
        return this.leftLegPose;
    }

    public Vector3f getRightLegPose() {
        return this.rightLegPose;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isMarker();
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        boolean flag;

        if (entity instanceof EntityHuman entityhuman) {
            if (!this.level().mayInteract(entityhuman, this.blockPosition())) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    @Override
    public EnumMainHand getMainArm() {
        return EnumMainHand.RIGHT;
    }

    @Override
    public EntityLiving.a getFallSounds() {
        return new EntityLiving.a(SoundEffects.ARMOR_STAND_FALL, SoundEffects.ARMOR_STAND_FALL);
    }

    @Nullable
    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.ARMOR_STAND_HIT;
    }

    @Nullable
    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.ARMOR_STAND_BREAK;
    }

    @Override
    public void thunderHit(WorldServer worldserver, EntityLightning entitylightning) {}

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityArmorStand.DATA_CLIENT_FLAGS.equals(datawatcherobject)) {
            this.refreshDimensions();
            this.blocksBuilding = !this.isMarker();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.getDimensionsMarker(this.isMarker());
    }

    private EntitySize getDimensionsMarker(boolean flag) {
        return flag ? EntityArmorStand.MARKER_DIMENSIONS : (this.isBaby() ? EntityArmorStand.BABY_DIMENSIONS : this.getType().getDimensions());
    }

    @Override
    public Vec3D getLightProbePosition(float f) {
        if (this.isMarker()) {
            AxisAlignedBB axisalignedbb = this.getDimensionsMarker(false).makeBoundingBox(this.position());
            BlockPosition blockposition = this.blockPosition();
            int i = Integer.MIN_VALUE;

            for (BlockPosition blockposition1 : BlockPosition.betweenClosed(BlockPosition.containing(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ), BlockPosition.containing(axisalignedbb.maxX, axisalignedbb.maxY, axisalignedbb.maxZ))) {
                int j = Math.max(this.level().getBrightness(EnumSkyBlock.BLOCK, blockposition1), this.level().getBrightness(EnumSkyBlock.SKY, blockposition1));

                if (j == 15) {
                    return Vec3D.atCenterOf(blockposition1);
                }

                if (j > i) {
                    i = j;
                    blockposition = blockposition1.immutable();
                }
            }

            return Vec3D.atCenterOf(blockposition);
        } else {
            return super.getLightProbePosition(f);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.ARMOR_STAND);
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return !this.isInvisible() && !this.isMarker();
    }
}
