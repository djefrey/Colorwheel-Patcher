package dev.djefrey.colorwheel_patcher;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// https://www.baeldung.com/java-compress-and-uncompress
public class ZipUtils
{
    public static void compress(File src, File dst) throws IOException
    {
        if (!dst.getName().endsWith(".zip"))
        {
            throw new RuntimeException("Invalid zip " + dst);
        }

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(dst));
        File[] children = src.listFiles();

        if (children != null)
        {
            for (var child : children)
            {
                zipFile(child, "", out);
            }
        }

        out.close();
    }

    private static void zipFile(File src, String path, ZipOutputStream out) throws IOException
    {
        if (src.isDirectory())
        {
            var dirPath = path + src.getName();

            if (!src.getName().endsWith("/"))
            {
                dirPath += "/";
            }

            out.putNextEntry(new ZipEntry(dirPath));

            File[] children = src.listFiles();

            if (children == null)
            {
                return;
            }

            for (File child : children)
            {
                zipFile(child, dirPath, out);
            }
        }
        else if (src.isFile())
        {
            FileInputStream in = new FileInputStream(src);

            out.putNextEntry(new ZipEntry(path + src.getName()));
            in.transferTo(out);
            in.close();
        }
    }

    public static void extract(InputStream stream, File dstFolder) throws IOException
    {
        var zip = new ZipInputStream(stream);
        ZipEntry entry = null;

        while ((entry = zip.getNextEntry()) != null)
        {
            File dst = newFile(dstFolder, entry);

            if (entry.isDirectory())
            {
                if (!dst.exists() && !dst.mkdirs())
                {
                    throw new IOException("Could not mkdir " + dst);
                }
            }
            else
            {
                File parent = dst.getParentFile();

                if (parent.isDirectory() && !parent.exists() && !parent.mkdirs())
                {
                    throw new IOException("Could not mkdir " + parent.getName());
                }

                if (!dst.exists() && !dst.createNewFile())
                {
                    throw new IOException("Could not touch " + dst.getName());
                }

                FileOutputStream out = new FileOutputStream(dst);

                zip.transferTo(out);
                out.close();
            }
        }

        zip.closeEntry();
        zip.close();
    }

    private static File newFile(File dstFolder, ZipEntry zipEntry) throws IOException
    {
        File dstFile = new File(dstFolder, zipEntry.getName());
        String dstDirPath = dstFolder.getCanonicalPath();
        String dstFilePath = dstFile.getCanonicalPath();

        if (!dstFilePath.startsWith(dstDirPath + File.separator))
        {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return dstFile;
    }
}
