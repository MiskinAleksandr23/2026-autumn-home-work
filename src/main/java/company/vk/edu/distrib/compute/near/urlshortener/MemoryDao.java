package company.vk.edu.distrib.compute.near.urlshortener;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import company.vk.edu.distrib.compute.Dao;

public final class MemoryDao implements Dao<String> {
    private final Map<String, String> entries = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        String value = entries.get(key);
        if (value == null) {
            throw new NoSuchElementException(key);
        }
        return value;
    }

    @Override
    public void upsert(String key, String value) {
        entries.put(key, value);
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    @Override
    public void close() {
        entries.clear();
    }
}
