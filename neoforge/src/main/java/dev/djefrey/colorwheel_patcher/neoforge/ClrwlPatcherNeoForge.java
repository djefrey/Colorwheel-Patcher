package dev.djefrey.colorwheel_patcher.neoforge;

import dev.djefrey.colorwheel_patcher.ClrwlPatcher;
import dev.djefrey.colorwheel_patcher.Version;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.regex.Pattern;

@Mod(ClrwlPatcher.MOD_ID)
public final class ClrwlPatcherNeoForge
{
    private final Pattern VERSION_REGEX = Pattern.compile("(\\d+).(\\d+).(\\d+).*");

    public ClrwlPatcherNeoForge(IEventBus modBus)
    {
        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event)
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
