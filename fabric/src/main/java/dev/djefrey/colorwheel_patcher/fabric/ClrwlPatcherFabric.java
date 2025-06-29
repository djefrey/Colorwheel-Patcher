package dev.djefrey.colorwheel_patcher.fabric;

import dev.djefrey.colorwheel_patcher.ClrwlPatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class ClrwlPatcherFabric implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        ClientLifecycleEvents.CLIENT_STARTED.register(c ->
        {
            Path shaderPath = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve(ClrwlPatcher.MOD_ID);

            ClrwlPatcher.init(shaderPath, configPath);
        });
    }
}
