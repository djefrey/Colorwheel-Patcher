package dev.djefrey.colorwheel_patcher.fabric;

import dev.djefrey.colorwheel_patcher.ClrwlPatcher;
import dev.djefrey.colorwheel_patcher.Version;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class ClrwlPatcherFabric implements ModInitializer
{
    private final Pattern VERSION_REGEX = Pattern.compile("(\\d+).(\\d+).(\\d+).*");

    @Override
    public void onInitialize()
    {
        ClientLifecycleEvents.CLIENT_STARTED.register(c ->
        {
            var versionStr = FabricLoader.getInstance().getModContainer(ClrwlPatcher.MOD_ID).get().getMetadata().getVersion().getFriendlyString();
            var matcher = VERSION_REGEX.matcher(versionStr);

            if (!matcher.matches())
            {
                throw new IllegalStateException("Could not parse Colorwheel mod version");
            }

            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int incremental = Integer.parseInt(matcher.group(3));

            Path shaderPath = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve(ClrwlPatcher.MOD_ID);

            ClrwlPatcher.init(new Version(major, minor, incremental), shaderPath, configPath);
        });
    }
}
