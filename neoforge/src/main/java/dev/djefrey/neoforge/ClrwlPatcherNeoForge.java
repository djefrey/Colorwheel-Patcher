package dev.djefrey.neoforge;

import dev.djefrey.ClrwlPatcher;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

@Mod(ClrwlPatcher.MOD_ID)
public final class ClrwlPatcherNeoForge
{
    public ClrwlPatcherNeoForge(IEventBus modBus)
    {
        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event)
    {
        Path shaderPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(ClrwlPatcher.MOD_ID);

        ClrwlPatcher.init(shaderPath, configPath);
    }
}
