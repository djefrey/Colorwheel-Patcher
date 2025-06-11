package dev.djefrey;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileUtils
{
    public static void copyAndMerge(Path srcPath, Path dstPath) throws IOException
    {
        File src = srcPath.toFile();
        File dst = dstPath.toFile();

        if (src.isDirectory())
        {
            if (dst.isDirectory())
            {
                File[] children = src.listFiles();

                if (children == null)
                {
                    return;
                }

                for (var child : children)
                {
                    copy(child, new File(dst, child.getName()));
                }
            }
            else
            {
                throw new IOException("Cannot copy directory to file");
            }
        }
        else
        {
            if (dst.isDirectory())
            {
                copy(src, new File(dst, src.getName()));
            }
            else
            {
                copy(src, dst);
            }
        }
    }

    private static void copy(File src, File dst) throws IOException
    {
        if (src.isDirectory())
        {
            if (!dst.exists() && !dst.mkdirs())
            {
                throw new IOException("Could not mkdir " + dst.getParent());
            }

            File[] children = src.listFiles();

            if (children == null)
            {
                return;
            }

            for (var child : children)
            {
                copy(child, new File(dst, child.getName()));
            }
        }
        else
        {
            if (!src.exists() && !src.createNewFile())
            {
                throw new IOException("Could not touch " + src);
            }

            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst);)
            {
                in.transferTo(out);
            }
        }
    }

    public static void moveRecursive(Path src, Path dst) throws IOException
    {
        if (Files.isDirectory(src))
        {
            if (!Files.exists(dst))
            {
                Files.createDirectory(dst);
            }

            try (var stream = Files.list(src))
            {
                var children = stream.toList();

                for (var child : children)
                {
                    moveRecursive(child, dst.resolve(child.getFileName()));
                }
            }
        }
        else
        {
            if (Files.exists(dst))
            {
                Files.delete(dst);
            }

            Files.move(src, dst);
        }
    }

    public static void deleteRecursive(File file) throws IOException
    {
        File[] children = file.listFiles();

        if (children != null)
        {
            for (var child : children)
            {
                deleteRecursive(child);
            }
        }

        if (!file.delete())
        {
            throw new IOException("Could not delete " + file);
        }
    }
}
