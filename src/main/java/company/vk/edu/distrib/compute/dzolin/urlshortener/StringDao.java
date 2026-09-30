package company.vk.edu.distrib.compute.dzolin.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class StringDao implements Dao<String> {
    private final Map<String, String> storage = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final RandomAccessFile log;

    public StringDao(String path) throws IOException {
        var file = Path.of(path).toAbsolutePath();
        Files.createDirectories(file.getParent());
        log = new RandomAccessFile(file.toFile(), "rw");
        init();
    }

    private void init() throws IOException {
        while (log.getFilePointer() < log.length()) {
            var key = log.readUTF();
            var value = log.readUTF();
            if (value.isEmpty()) {
                storage.remove(key);
            } else {
                storage.put(key, value);
            }
        }
    }

    @Override
    public String get(String key) {
        var value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException("no such value for key: " + key);
        }
        return value;
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        lock.lock();
        try {
            append(key, value);
            storage.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IOException {
        lock.lock();
        try {
            append(key, "");
            storage.remove(key);
        } finally {
            lock.unlock();
        }
    }

    private void append(String key, String value) throws IOException {
        log.writeUTF(key);
        log.writeUTF(value);
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            log.close();
        } finally {
            lock.unlock();
        }
    }
}
