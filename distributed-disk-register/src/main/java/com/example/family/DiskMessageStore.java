package com.example.family;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class DiskMessageStore {
    private final Path dir;

    public DiskMessageStore(String folderName) {
        this.dir = Paths.get(folderName);
    }

    private Path fileOf(int id) {
        return dir.resolve(id + ".msg");
    }

    private void ensureDir() throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    public void write(int id, String msg) throws IOException {
        ensureDir();
        Path file = fileOf(id);
        try (BufferedWriter w = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write(msg);
        }
    }

    public String read(int id) throws IOException {
        ensureDir();
        Path file = fileOf(id);
        if (!Files.exists(file)) return null;
        return Files.readString(file, StandardCharsets.UTF_8);
    }
    // GÜNCELLEME: Stage 6 - Ekrana yazdırmak için dosya sayısını döndürür
    public int getStoredMessageCount() {
        if (!Files.exists(dir)) return 0;
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            // Sadece .msg dosyalarını say
            return (int) stream.filter(p -> p.toString().endsWith(".msg")).count();
        } catch (IOException e) {
            return 0;
        }
    }
}
