package company.vk.edu.distrib.compute.ruavee.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class PersistentDao implements Dao<String> {
    private static final String PUT = "P";
    private static final String DELETE = "D";
    private final Map<String, String> storage;
    private final Path file;
    private final ReentrantLock lock = new ReentrantLock();

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void append(String line) throws IOException {
        Files.writeString(
                file,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private void load() throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] parts = line.split(" ", 3);
            if (PUT.equals(parts[0])) {
                storage.put(decode(parts[1]), decode(parts[2]));
            } else if (DELETE.equals(parts[0])) {
                storage.remove(decode(parts[1]));
            }
        }
    }

    public PersistentDao(Path file) throws IOException {
        this.storage = new ConcurrentHashMap<>();
        this.file = file;
        if (Files.exists(file)) {
            load();
        }
    }

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        String value = storage.get(key);
        if (value != null) {
            return value;
        }
        throw new NoSuchElementException();
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException, IOException {
        lock.lock();
        try {
            append(PUT + " " + encode(key) + " " + encode(value));
            storage.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IllegalArgumentException, IOException {
        lock.lock();
        try {
            append(DELETE + " " + encode(key));
            storage.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        // Nothing to do here :)
    }
}
