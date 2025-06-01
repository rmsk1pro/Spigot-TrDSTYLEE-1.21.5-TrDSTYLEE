package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.projectile.EntityPotion;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.LingeringPotion;

public class CraftLingeringPotion extends CraftThrownPotion implements LingeringPotion {

    public CraftLingeringPotion(CraftServer server, EntityPotion entity) {
        super(server, entity);
    }
}
