package dev.djefrey.colorwheel_patcher.config;

import com.google.gson.Gson;

import java.io.Reader;
import java.util.Collections;
import java.util.List;

public record ClrwlConfig(List<PatchConfig> patches)
{
    public static ClrwlConfig empty()
    {
        return new ClrwlConfig(Collections.emptyList());
    }

    public static ClrwlConfig loadJson(Reader reader)
    {
        Gson loader = new Gson();

        return loader.fromJson(reader, ClrwlConfig.class);
    }

    public record PatchConfig(String shaderName,
                              List<String> versions,
                              List<String> aliases)
    {
    }
}
