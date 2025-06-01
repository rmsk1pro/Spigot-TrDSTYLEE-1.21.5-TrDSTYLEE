package org.bukkit;

import static org.bukkit.support.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.MinecraftKey;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
public class SoundTest {

    @Test
    public void testGetSound() {
        for (Sound sound : Sound.values()) {
            assertThat(CraftSound.bukkitToMinecraft(sound), is(not(nullValue())), sound.name());
        }
    }

    @Test
    public void testReverse() {
        for (MinecraftKey effect : BuiltInRegistries.SOUND_EVENT.keySet()) {
            assertNotNull(Sound.valueOf(effect.getPath().replace('.', '_').toUpperCase(Locale.ROOT)), effect + "");
        }
    }

    @Test
    public void testCategory() {
        for (SoundCategory category : SoundCategory.values()) {
            assertNotNull(net.minecraft.sounds.SoundCategory.valueOf(category.name()), category + "");
        }
    }

    @Test
    public void testCategoryReverse() {
        for (net.minecraft.sounds.SoundCategory category : net.minecraft.sounds.SoundCategory.values()) {
            assertNotNull(SoundCategory.valueOf(category.name()), category + "");
        }
    }
}
