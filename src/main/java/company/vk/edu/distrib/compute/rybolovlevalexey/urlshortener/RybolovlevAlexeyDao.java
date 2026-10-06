package company.vk.edu.distrib.compute.rybolovlevalexey.urlshortener;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import company.vk.edu.distrib.compute.Dao;

public class RybolovlevAlexeyDao implements Dao<String> {
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        final var value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException("no value for key: " + key);
        }
        return value;
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
    public void close() {
        // no resources to close in-memory storage
    }
}
