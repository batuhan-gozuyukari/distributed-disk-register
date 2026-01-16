package com.example.family;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ToleranceConfig {
    private final Path path;
    public ToleranceConfig(String fileName) {
        this.path = Path.of(fileName);
    }

    public int readToleranceOrDefault(int def) {
        try {
            if (!Files.exists(path)) return def;
            String line = Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse("");
            if (!line.contains("=")) return def;
            int val = Integer.parseInt(line.split("=", 2)[1].trim());
            if (val < 1) return 1;
            if (val > 7) return 7; 
            return val;
        } catch (IOException | NumberFormatException e) {
            return def;
        }
    }
}
