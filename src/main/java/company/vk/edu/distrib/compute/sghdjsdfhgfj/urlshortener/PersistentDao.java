package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

public class PersistentDao implements Dao<String> {
    private final Map<String, String> data;
    private final String filename;
    private final ReentrantLock lock;

    PersistentDao(Path path) throws IOException {
        this.filename = path.toString();
        data = new HashMap<>();
        lock = new ReentrantLock();
        read();
    }

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        if (!data.containsKey(key)) {
            throw new NoSuchElementException();
        }
        return data.get(key);
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            data.put(key, value);
            append(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            data.remove(key);
            append(key, "");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        //
    }

    private void read() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filename, "rw")) {
            while (true) {
                try {
                    String key = file.readUTF();
                    String value = file.readUTF();
                    if (value.isEmpty()) {
                        data.remove(key);
                    } else {
                        data.put(key, value);
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        }
    }

    private void append(String key, String value) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filename, "rw")) {
            file.seek(file.length());
            file.writeUTF(key);
            file.writeUTF(value);
        }
    }
}
