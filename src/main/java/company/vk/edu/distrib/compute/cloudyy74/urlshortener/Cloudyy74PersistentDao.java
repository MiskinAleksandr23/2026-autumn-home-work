package company.vk.edu.distrib.compute.cloudyy74.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Cloudyy74PersistentDao implements Dao<String> {
    private final Map<String, String> storage = new ConcurrentHashMap<>();
    private final Lock writeLock = new ReentrantLock();
    private final Path logFile;

    public Cloudyy74PersistentDao(String logFile) throws IOException {
        this.logFile = Path.of(logFile);

        Path parent = this.logFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        load();
    }

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException {
        final var value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException("no value for key: " + key);
        }

        return value;
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException, IOException {
        writeLock.lock();
        try {
            append("PUT " + key + " " + value);
            storage.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IllegalArgumentException, IOException {
        writeLock.lock();
        try {
            append("DEL " + key);
            storage.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        // no resources to release
    }

    private void append(String operation) throws IOException {
        Files.writeString(
                logFile,
                operation + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private void load() throws IOException {
        if (!Files.exists(logFile)) {
            return;
        }

        try (var lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                String[] parts = line.split(" ", 3);

                switch (parts[0]) {
                    case "PUT" -> storage.put(parts[1], parts[2]);
                    case "DEL" -> storage.remove(parts[1]);
                    default -> throw new IllegalStateException(
                            "Unknown log operation: " + line
                    );
                }
            });
        }
    }
}
