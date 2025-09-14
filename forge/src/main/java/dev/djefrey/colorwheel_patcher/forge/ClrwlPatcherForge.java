package dev.djefrey.colorwheel_patcher.forge;

import dev.djefrey.colorwheel_patcher.ClrwlPatcher;
import dev.djefrey.colorwheel_patcher.Version;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.regex.Pattern;

@Mod(ClrwlPatcher.MOD_ID)
public final class ClrwlPatcherForge
{
    private final Pattern VERSION_REGEX = Pattern.compile("(\\d+).(\\d+).(\\d+).*");

    public ClrwlPatcherForge()
    {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event)
    {
        var versionStr = ModList.get().getModContainerById(ClrwlPatcher.MOD_ID).get().getModInfo().getVersion().getQualifier();
        var matcher = VERSION_REGEX.matcher(versionStr);

        if (!matcher.matches())
        {
            throw new IllegalStateException("Could not parse Colorwheel mod version");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int incremental = Integer.parseInt(matcher.group(3));

        Path shaderPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(ClrwlPatcher.MOD_ID);

        ClrwlPatcher.init(new Version(major, minor, incremental), shaderPath, configPath);
    }
}
