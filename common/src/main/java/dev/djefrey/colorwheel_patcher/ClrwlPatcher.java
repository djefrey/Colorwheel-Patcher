package dev.djefrey.colorwheel_patcher;

import com.google.common.collect.Lists;
import dev.djefrey.colorwheel_patcher.config.ClrwlConfig;
import dev.djefrey.colorwheel_patcher.config.ProcessedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ClrwlPatcher
{
    public static final String MOD_ID = "colorwheel_patcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String BRAND = "Colorwheel";
    public static final Version VERSION = new Version(0, 2, 3);
    public static final String BRAND_VERSION = BRAND + "_" + VERSION;

    public static final String JAR_PATCHES_SUBPATH = "/patches/";
    public static final String USER_PATCHES_SUBPATH = "/patches/";

    public static final String CONFIG_FILENAME = "/config.json";

    public static final Pattern CLRWL_BRAND_VERSION_REGEX = Pattern.compile(" \\+ Colorwheel_([0-9]+\\.[0-9]+\\.[0-9]+)");

    public static final String OUTDATED_PREFIX = "§cOutdated§r ";

    public static void init(Path shadesrFolder, Path configFolder)
    {
        ProcessedConfig config = readPatchConfigs(configFolder);

        LOGGER.info("Available patches: {}", config.patches().size());

        for (var patch : config.patches())
        {
            LOGGER.info(" - {} [{}] ({})", patch.shaderName(), String.join(", ", patch.versions()), patch.userPatch() ? "user" : "jar");
        }

        List<String> shaders = listShaderpacks(shadesrFolder);
        Map<String, List<Version>> baseShaders = matchColorwheelShaders(shaders);

        LOGGER.info("Existing base shaders: {}", baseShaders.size());

        for (var entry : baseShaders.entrySet())
        {
            List<String> versions = entry.getValue().stream().map(Version::toString).toList();

            LOGGER.info(" - {} [{}]", entry.getKey(), String.join(", ", versions));
        }

        patchShaderpacks(config.patches(), baseShaders, shadesrFolder, configFolder);
        flagOutdatedShaderpacks(baseShaders, shadesrFolder);
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

    private static Map<String, List<Version>> matchColorwheelShaders(List<String> shaders)
    {
        List<String> nonClrwlShaders = new ArrayList<>();
        Map<String, List<Version>> matchMap = new HashMap<>();

        for (var shader : shaders)
        {
            var nonClrwl = extractBaseShaderName(shader);

            if (nonClrwl.isPresent()) // Colorwheel shader
            {
                var version = extractColorwheelVersion(shader);

                if (version.isEmpty())
                {
                    LOGGER.error("Could not extract Colorwheel version from {}", shader);
                    continue;
                }

                String baseShader = nonClrwl.get();

                matchMap.computeIfAbsent(baseShader, ($) -> new ArrayList<>())
                        .add(version.get());
            }
            else
            {
                nonClrwlShaders.add(shader);
                matchMap.computeIfAbsent(shader, ($) -> new ArrayList<>());
            }
        }

        // Remove Colorwheel matches without existing base shader

        var keysToRm = matchMap.keySet().stream()
                .filter((k) -> !nonClrwlShaders.contains(k))
                .toList();

        for (var key : keysToRm)
        {
            LOGGER.warn("Base shader '{}' does not exists", key);
            matchMap.remove(key);
        }

        for (var entry : matchMap.entrySet())
        {
            entry.getValue().sort(Version::compareTo);
        }

        return matchMap;
    }

    private static void patchShaderpacks(List<ProcessedConfig.PatchConfig> patches, Map<String, List<Version>> baseShaders, Path shaderpacksFolder, Path configFolder)
    {
        for (var entry : baseShaders.entrySet())
        {
            var shader = entry.getKey();
            var clrwlVersions = entry.getValue();

            if (clrwlVersions.contains(VERSION))
            {
                LOGGER.info("'{}' already patched with version {}", shader, VERSION);
                continue;
            }

            var maybePatch = patches.stream().filter(p -> p.doesMatchWith(shader)).findFirst();

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

            var shaderpack = shaderpacksFolder.resolve(shader);
            var patchedName = getPatchShaderpackName(shader, VERSION, isZip);

            try
            {
                Path tmpFolder = createTmpDirectory();

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

                var patchedPath = shaderpacksFolder.resolve(patchedName);

                if (isZip)
                {
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
                    FileUtils.moveRecursive(tmpFolder, patchedPath);
                }
            }
            catch (IOException e)
            {
                LOGGER.error("Could not patch shaderpack", e);
                continue;
            }

            try
            {
                Path ogConfigPath = null;

                for (Version version : Lists.reverse(clrwlVersions))
                {
                    String filename = getPatchShaderpackName(shader, version, shader.endsWith(".zip")) + ".txt";
                    Path path = shaderpacksFolder.resolve(filename);

                    if (Files.exists(path))
                    {
                        ogConfigPath = path;
                        break;
                    }

                    path = shaderpacksFolder.resolve(OUTDATED_PREFIX + filename);

                    if (Files.exists(path))
                    {
                        ogConfigPath = path;
                        break;
                    }
                }

                if (ogConfigPath == null)
                {
                    Path path = shaderpacksFolder.resolve(shader + ".txt");

                    if (Files.exists(path))
                    {
                        ogConfigPath = path;
                    }
                }

                if (ogConfigPath != null)
                {
                    Path newConfigPath = shaderpacksFolder.resolve(patchedName + ".txt");

                    LOGGER.info("Found option file '{}'", ogConfigPath.getFileName().toString());
                    Files.copy(ogConfigPath, newConfigPath);
                }
                else
                {
                    LOGGER.info("Could not find option file for {}", shader);
                }
            }
            catch (IOException e)
            {
                LOGGER.error("Could not copy shaderpack config file", e);
                continue;
            }
        }
    }

    private static void flagOutdatedShaderpacks(Map<String, List<Version>> baseShaders, Path shaderpacksFolder)
    {
        for (var entry : baseShaders.entrySet())
        {
            var shader = entry.getKey();
            var clrwlVersions = entry.getValue();

            for (var version : clrwlVersions)
            {
                if (version.compareTo(VERSION) >= 0)
                {
                    continue;
                }

                String patchedShader = getPatchShaderpackName(shader, version, shader.endsWith(".zip"));
                Path path = shaderpacksFolder.resolve(patchedShader);

                if (!Files.exists(path))
                {
                    continue;
                }

                LOGGER.info("Flag '{}' as outdated", patchedShader);

                Path outdatedPath = shaderpacksFolder.resolve(OUTDATED_PREFIX + patchedShader);

                try
                {
                    Files.move(path, outdatedPath);
                }
                catch (IOException e)
                {
                    LOGGER.error("Could not set shader to outdated", e);
                    continue;
                }

                Path configPath = shaderpacksFolder.resolve(patchedShader + ".txt");

                if (!Files.exists(configPath))
                {
                    continue;
                }

                LOGGER.info("Flag config file as outdated");

                Path outdatedConfigPath = shaderpacksFolder.resolve(OUTDATED_PREFIX + patchedShader + ".txt");

                try
                {
                    Files.move(configPath, outdatedConfigPath);
                }
                catch (IOException e)
                {
                    LOGGER.error("Could not set config file to outdated", e);
                    continue;
                }
            }
        }
    }

    private static Path createTmpDirectory() throws IOException
    {
        return Files.createTempDirectory("clrwl-patcher");
    }

    private static String getPatchShaderpackName(String shader, Version version, boolean isZip)
    {
        if (isZip)
        {
            return shader.substring(0, shader.length() - 4) + " + " + BRAND + "_" + version + ".zip";
        }
        else
        {
            return shader + " + " + BRAND + "_" + version;
        }
    }

    private static Optional<String> extractBaseShaderName(String shader)
    {
        Matcher matcher = CLRWL_BRAND_VERSION_REGEX.matcher(shader);

        if (matcher.find())
        {
            String clean = matcher.replaceAll("");

            if (clean.startsWith(OUTDATED_PREFIX))
            {
                clean = clean.substring(OUTDATED_PREFIX.length());
            }

            return Optional.of(clean);
        }
        else
        {
            return Optional.empty();
        }
    }

    private static Optional<Version> extractColorwheelVersion(String shader)
    {
        Matcher matcher = CLRWL_BRAND_VERSION_REGEX.matcher(shader);

        if (matcher.find())
        {
            int[] version = Arrays.stream(matcher.group(1).split("\\.")).mapToInt(Integer::parseInt).toArray();

            return Optional.of(Version.fromArray(version));
        }
        else
        {
            return Optional.empty();
        }
    }

    // Accessed by Colorwheel using reflection
    public static Optional<String> findPatchedShaderpackInFolder(String shaderpack, Path shaderpacksFolder)
    {
        File folder = shaderpacksFolder.toFile();

        if (!folder.isDirectory())
        {
            return Optional.empty();
        }

        var children = folder.listFiles();

        if (children == null)
        {
            return Optional.empty();
        }

        Version maxVersion = null;
        String patchedShaderpack = null;

        for (File pack : children)
        {
            if (pack.getName().endsWith(".txt"))
            {
                continue;
            }

            var maybeBasePack = extractBaseShaderName(pack.getName());

            if (maybeBasePack.isEmpty() || !maybeBasePack.get().equals(shaderpack))
            {
                continue;
            }

            var maybeVersion = extractColorwheelVersion(pack.getName());

            if (maybeVersion.isEmpty())
            {
                continue;
            }

            if (maxVersion == null || maybeVersion.get().compareTo(maxVersion) > 0)
            {
                maxVersion = maybeVersion.get();
                patchedShaderpack = pack.getName();
            }
        }

        return Optional.ofNullable(patchedShaderpack);
    }
}
