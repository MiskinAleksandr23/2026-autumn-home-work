package company.vk.edu.distrib.compute.sovesti.urlshortener.dao;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import company.vk.edu.distrib.compute.Dao;

public final class InMemoryDao<T> implements Dao<T> {

    private final Map<String, T> values = new ConcurrentHashMap<>();

    @Override
    public void close() throws IOException {
        // ignore
    }

    @Override
    public T get(String key) throws NoSuchElementException {
        return Optional.ofNullable(values.get(key)).get();
    }

    @Override
    public void upsert(String key, T value) {
        values.put(key, value);
    }

    @Override
    public void delete(String key) {
        values.remove(key);
    }

}
