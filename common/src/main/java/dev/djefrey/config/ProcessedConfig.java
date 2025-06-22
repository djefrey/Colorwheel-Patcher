package dev.djefrey.config;

import dev.djefrey.ClrwlPatcher;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record ProcessedConfig(List<PatchConfig> patches)
{
    public static ProcessedConfig processConfigs(ClrwlConfig jarConfig, ClrwlConfig userConfig)
    {
        List<PatchConfig> patches = new ArrayList<>();

        addPatchesFromConfig(userConfig, true,  patches);
        addPatchesFromConfig(jarConfig, false,  patches);

        return new ProcessedConfig(patches);
    }

    private static void addPatchesFromConfig(ClrwlConfig config, boolean userConfig, List<PatchConfig> patches)
    {
        for (var patch : config.patches())
        {
            if (patches.stream().anyMatch(p -> p.shaderName().equals(patch.shaderName())))
            {
                continue;
            }

            patches.add(PatchConfig.fromFileEntry(patch, userConfig));
        }
    }

    public record PatchConfig(String shaderName,
                              List<String> versions,
                              List<String> aliases,
                              boolean userPatch)
    {
        public static PatchConfig fromFileEntry(ClrwlConfig.PatchConfig entry, boolean userConfig)
        {
            return new PatchConfig(entry.shaderName(), entry.versions(), entry.aliases(), userConfig);
        }

        public InputStream getPatchZip(Path configPath) throws FileNotFoundException
        {
            InputStream res;

            if (userPatch)
            {
                res = new FileInputStream(configPath.resolve(ClrwlPatcher.USER_PATCHES_SUBPATH + shaderName + ".zip").toFile());
            }
            else
            {
                res = getClass().getResourceAsStream(ClrwlPatcher.JAR_PATCHES_SUBPATH + shaderName + ".zip");
            }

            if (res == null)
            {
                throw new FileNotFoundException();
            }

            return res;
        }

        public boolean doesMatchWith(String str)
        {
            return str.contains(shaderName) || aliases.stream().anyMatch(str::contains);
        }
    }
}
