package company.vk.edu.distrib.compute.miiishenka.urlshortener.dao;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import company.vk.edu.distrib.compute.Dao;

public class PersistentDao implements Dao<String> {
    private final String fileName;
    private final Map<String, String> storage;

    public PersistentDao(String fileName) throws IOException {
        this.fileName = fileName;
        this.storage = new ConcurrentHashMap<>();
        try (RandomAccessFile file = new RandomAccessFile(fileName, "rw")) {
            if (file.length() == 0) {
                return;
            }
            int size = file.readInt();
            for (int i = 0; i < size; i++) {
                storage.put(file.readUTF(), file.readUTF());
            }
        }
    }

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        return storage.get(key);
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException, IOException {
        storage.put(key, value);
    }

    @Override
    public void delete(String key) throws IllegalArgumentException, IOException {
        storage.remove(key);
    }

    @Override
    public void close() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(fileName, "rw")) {
            file.setLength(0);
            file.writeInt(storage.size());
            for (var entry : storage.entrySet()) {
                file.writeUTF(entry.getKey());
                file.writeUTF(entry.getValue());
            }
        }
    }
}
