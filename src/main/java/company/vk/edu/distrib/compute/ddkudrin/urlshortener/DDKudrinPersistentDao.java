package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.BufferedReader;
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

import company.vk.edu.distrib.compute.Dao;

public final class DDKudrinPersistentDao implements Dao<String> {
    private static final int RECORD_PARTS_COUNT = 3;

    private enum Operation {
        INSERT,
        DEL
    }

    private final Path file;
    private final Lock lock = new ReentrantLock();
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    public DDKudrinPersistentDao(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        Files.createDirectories(this.file.getParent());
        if (!Files.exists(this.file)) {
            Files.createFile(this.file);
        }
        load();
    }

    @Override
    public String get(String key) {
        String value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException("Unknown key: " + key);
        }
        return value;
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        lock.lock();
        try {
            saveLine(Operation.INSERT, key, value);
            storage.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IOException {
        lock.lock();
        try {
            if (storage.containsKey(key)) {
                String val = storage.get(key);
                saveLine(Operation.DEL, key, val);
                storage.remove(key);
            }
            
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        // close
    }

    private void saveLine(Operation operation, String key, String value) throws IOException {
        String line = String.format("%s %s %s%n", operation.name(), key, value);
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private void load() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(" ", RECORD_PARTS_COUNT);
                if (parts.length < RECORD_PARTS_COUNT) {
                    continue;
                }

                Operation op;
                try {
                    op = Operation.valueOf(parts[0]);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                String key = parts[1];
                String value = parts[2];

                switch (op) {
                    case INSERT -> storage.put(key, value);
                    case DEL -> storage.remove(key);
                }
            }
        }
    }
}
