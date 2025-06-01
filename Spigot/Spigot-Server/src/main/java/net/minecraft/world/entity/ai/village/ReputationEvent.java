package net.minecraft.world.entity.ai.village;

public interface ReputationEvent {

    java.util.Map<String, ReputationEvent> BY_ID = com.google.common.collect.Maps.newHashMap(); // CraftBukkit - map with all values
    ReputationEvent ZOMBIE_VILLAGER_CURED = register("zombie_villager_cured");
    ReputationEvent GOLEM_KILLED = register("golem_killed");
    ReputationEvent VILLAGER_HURT = register("villager_hurt");
    ReputationEvent VILLAGER_KILLED = register("villager_killed");
    ReputationEvent TRADE = register("trade");
    // CraftBukkit start - additional events added in the API
    ReputationEvent GOSSIP = register("bukkit_gossip");
    ReputationEvent DECAY = register("bukkit_decay");
    ReputationEvent UNSPECIFIED = register("bukkit_unspecified");
    // CraftBukkit end

    static ReputationEvent register(final String s) {
        return new ReputationEvent() {
            // CraftBukkit start - add new value to map
            {
                BY_ID.put(s, this);
            }
            // CraftBukkit end

            public String toString() {
                return s;
            }
        };
    }
}
