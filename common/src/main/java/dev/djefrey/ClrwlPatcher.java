package dev.djefrey;

import dev.djefrey.config.ClrwlConfig;
import dev.djefrey.config.ProcessedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class ClrwlPatcher
{
    public static final String MOD_ID = "colorwheel_patcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String VERSION = "0.2.1";
    public static final String BRAND_VERSION = "Colorwheel_" + VERSION;

    public static final String JAR_PATCHES_SUBPATH = "/patches/";
    public static final String USER_PATCHES_SUBPATH = "/patches/";

    public static final String CONFIG_FILENAME = "/config.json";

    public static void init(Path shadesrFolder, Path configFolder)
    {
        ProcessedConfig config = readPatchConfigs(configFolder);

        LOGGER.info("Available patches: {}", config.patches().size());

        for (var patch : config.patches())
        {
            LOGGER.info(" - {} [{}] ({})", patch.shaderName(), String.join(", ", patch.versions()), patch.userPatch() ? "user" : "jar");
        }

        List<String> shaders = listShaderpacks(shadesrFolder);
        shaders = filterPatchedShaderpacks(shaders);

        LOGGER.info("Non patched shaders: {}", shaders.size());

        for (var shader : shaders)
        {
            LOGGER.info(" - {}", shader);
        }

        patchShaderpacks(config.patches(), shaders, shadesrFolder, configFolder);
    }

    private static ProcessedConfig readPatchConfigs(Path configFolder)
    {
        ClrwlConfig jarConfig;
        ClrwlConfig userConfig;

        try (InputStream in = ClrwlPatcher.class.getResourceAsStream(CONFIG_FILENAME))
        {
            if (in != null)
            {
                jarConfig =  ClrwlConfig.loadJson(new InputStreamReader(in));
            }
            else
            {
                LOGGER.error("Could not read config.json in .jar");
                jarConfig = ClrwlConfig.empty();
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Could not read config.json in .jar");
            jarConfig = ClrwlConfig.empty();
        }

        Path configPath = configFolder.resolve(CONFIG_FILENAME);

        if (Files.exists(configPath))
        {
            try (InputStream in = Files.newInputStream(configPath))
            {
                userConfig =  ClrwlConfig.loadJson(new InputStreamReader(in));
            }
            catch (IOException e)
            {
                LOGGER.error("Could not read config.json in config/");
                userConfig = ClrwlConfig.empty();
            }
        }
        else
        {
            LOGGER.info("No user config.json found");
            userConfig = ClrwlConfig.empty();
        }

        return ProcessedConfig.processConfigs(jarConfig, userConfig);
    }

    private static List<String> listShaderpacks(Path shadersFolder)
    {
        List<String> shaders;

        if (!Files.exists(shadersFolder))
        {
            LOGGER.info("No shaderpacks folder");
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(shadersFolder))
        {
            shaders = stream
                    .filter(p -> p.getFileName().toString().endsWith(".zip") || Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .toList();
        }
        catch (IOException e)
        {
            LOGGER.error("Could not list installed shaderpacks");
            return Collections.emptyList();
        }

        return shaders;
    }

    private static List<String> filterPatchedShaderpacks(List<String> shaders)
    {
        var clrwlShaders  = shaders.stream().filter(s -> s.contains(BRAND_VERSION)).toList();

        return shaders.stream().filter(shader ->
        {
            var isZip = shader.toLowerCase().endsWith(".zip");
            Stream<String> potentialMatches;
            String shaderName;

            if (isZip)
            {
                shaderName = shader.substring(0, shader.length() - 4);
                potentialMatches = clrwlShaders.stream().filter(s -> s.toLowerCase().endsWith(".zip"));
            }
            else
            {
                shaderName = shader;
                potentialMatches = clrwlShaders.stream().filter(s -> !s.toLowerCase().endsWith(".zip"));
            }

            return potentialMatches.noneMatch((clrwl) -> clrwl.startsWith(shaderName));
        }).toList();
    }

    private static void patchShaderpacks(List<ProcessedConfig.PatchConfig> patches, List<String> shaders, Path shaderpacksFolder, Path configFolder)
    {
        for (var shader : shaders)
        {
            var maybePatch = patches.stream().filter(p -> shader.contains(p.shaderName())).findFirst();

            if (maybePatch.isEmpty())
            {
                LOGGER.info("Could not find patch for {}", shader);
                continue;
            }

            var patch = maybePatch.get();
            var isZip = shader.toLowerCase().endsWith(".zip");

            LOGGER.info("Found {}patch '{}' for {}", patch.userPatch() ? "user " : "", patch.shaderName(), shader);

            if (patch.versions().stream().noneMatch(shader::contains))
            {
                LOGGER.info("'{}' version is unknown or unsupported", shader);
                continue;
            }

            try
            {
                Path tmpFolder = createTmpDirectory();

                var shaderpack = shaderpacksFolder.resolve(shader);
                var patchedName = getPatchShaderpackName(shader, isZip);

                if (isZip)
                {
                    try (FileInputStream in = new FileInputStream(shaderpack.toFile()))
                    {
                        ZipUtils.extract(in, tmpFolder.toFile());
                    }
                }
                else
                {
                    FileUtils.copyAndMerge(shaderpack, tmpFolder);
                }

                try
                {
                    var zipIn = patch.getPatchZip(configFolder);
                    var shadersFolder = FileUtils.findFolderInChildren(tmpFolder.toFile(), "shaders");

                    if (shadersFolder.isEmpty())
                    {
                        LOGGER.error("Could not find shaders/ in {}", shader);
                        continue;
                    }

                    var parent = shadersFolder.get().getParentFile();

                    ZipUtils.extract(zipIn, parent);
                }
                catch (FileNotFoundException e)
                {
                    LOGGER.error("Could not find {}patch {}", patch.userPatch() ? "user " : "", patch.shaderName());
                    FileUtils.deleteRecursive(tmpFolder.toFile());
                    continue;
                }

                if (isZip)
                {
                    var patchedPath = shaderpacksFolder.resolve(patchedName + ".zip");

                    ZipUtils.compress(tmpFolder.toFile(), patchedPath.toFile());

                    try
                    {
                        FileUtils.deleteRecursive(tmpFolder.toFile());
                    }
                    catch (IOException e)
                    {
                        LOGGER.warn("Could not delete tmp folder");
                    }
                }
                else
                {
                    var patchedPath = shaderpacksFolder.resolve(patchedName);

                    FileUtils.moveRecursive(tmpFolder, patchedPath);
                }
            }
            catch (IOException e)
            {
                LOGGER.error("Could not patch shaderpack", e);
            }
        }
    }

    private static Path createTmpDirectory() throws IOException
    {
        return Files.createTempDirectory("clrwl-patcher");
    }

    private static String getPatchShaderpackName(String shader, boolean isZip)
    {
        if (isZip)
        {
            return shader.substring(0, shader.length() - 4) + " + " + BRAND_VERSION;
        }
        else
        {
            return shader + " + " + BRAND_VERSION;
        }
    }
}
