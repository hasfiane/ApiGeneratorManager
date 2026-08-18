package com.api.generator.util;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.*;
/** Low-level file utilities used by the generation job service. */
@SuppressWarnings("NullableProblems")
public final class FileUtils {
    private FileUtils() {}
    public static void ensureDir(Path p) throws IOException { Files.createDirectories(p); }
    public static void deleteDirectoryIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException
                { Files.deleteIfExists(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException
                { Files.deleteIfExists(d); return FileVisitResult.CONTINUE; }
        });
    }
    public static void deleteDirectory(Path dir) throws IOException { deleteDirectoryIfExists(dir); }

    public static void copyDirectory(Path src, Path dst) throws IOException {
        ensureDir(dst);
        Files.walkFileTree(src.toAbsolutePath().normalize(), new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(d)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.copy(f, dst.resolve(src.relativize(f)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir))
            throw new IOException("Source directory does not exist: " + sourceDir);
        ensureDir(zipFile.getParent());
        try (OutputStream os = Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            Path root = sourceDir.toAbsolutePath().normalize();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                    String entry = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\','/');
                    zos.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static void unzip(Path zipFile, Path targetDir) throws IOException {
        ensureDir(targetDir);
        try (InputStream is = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir.normalize())) {
                    throw new IOException("Invalid ZIP entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
