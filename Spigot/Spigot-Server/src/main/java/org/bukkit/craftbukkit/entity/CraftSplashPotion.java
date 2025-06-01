package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.projectile.EntityPotion;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.SplashPotion;

public class CraftSplashPotion extends CraftThrownPotion implements SplashPotion {

    public CraftSplashPotion(CraftServer server, EntityPotion entity) {
        super(server, entity);
    }
}
