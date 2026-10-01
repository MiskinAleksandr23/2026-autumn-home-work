package company.vk.edu.distrib.compute.cloudyy74.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

public class Cloudyy74Dao implements Dao<String> {
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException {
        final var value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException("no value for key: " + key);
        }

        return value;
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException {
        storage.put(key, value);
    }

    @Override
    public void delete(String key) throws IllegalArgumentException {
        storage.remove(key);
    }

    @Override
    public void close() {
        // no resources to release
    }
}
