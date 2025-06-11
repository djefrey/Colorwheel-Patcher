package dev.djefrey.forge;

import dev.djefrey.ClrwlPatcher;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

@Mod(ClrwlPatcher.MOD_ID)
public final class ClrwlPatcherForge
{
    public ClrwlPatcherForge()
    {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event)
    {
        Path shaderPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(ClrwlPatcher.MOD_ID);

        ClrwlPatcher.init(shaderPath, configPath);
    }
}
