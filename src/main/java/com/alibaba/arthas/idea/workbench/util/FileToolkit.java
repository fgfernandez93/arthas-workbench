package com.alibaba.arthas.idea.workbench.util;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 封装文件解压与目录清理等基础 IO 操作。
 */
public final class FileToolkit {

    private FileToolkit() {}

    /**
     * 解压 Zip 包到目标目录，并显式阻止 Zip Slip 路径穿越。
     */
    public static void unzip(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream inputStream = Files.newInputStream(zipPath);
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IllegalArgumentException(
                            ArthasWorkbenchBundle.message("util.file.error.invalid_zip_entry", entry.getName()));
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zipInputStream, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
    }

    /**
     * 递归删除目录，删除失败的条目会在后续覆盖写入阶段再次兜底。
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // 忽略清理失败，后续会用覆盖写入兜底。
                }
            });
        }
    }
}
