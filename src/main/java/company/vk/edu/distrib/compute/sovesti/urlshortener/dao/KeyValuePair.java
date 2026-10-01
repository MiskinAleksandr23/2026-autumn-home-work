package company.vk.edu.distrib.compute.sovesti.urlshortener.dao;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;

import company.vk.edu.distrib.compute.Dao;

public final class KeyValuePair {

    private final String raw;
    private final String separator;

    public KeyValuePair(String raw, char separator) {
        this.raw = Objects.requireNonNull(raw);
        this.separator = String.valueOf(Objects.requireNonNull(separator));
    }

    public KeyValuePair(String raw) {
        this(raw, ':');
    }

    public KeyValuePair(String key, String value, char separator) {
        this(key + separator + value, separator);
    }

    public KeyValuePair(String key, String value) {
        this(key, value, ':');
    }

    public void upsert(Dao<String> dao) throws IOException {
        dao.upsert(key(), value());
    }

    public boolean exists(Dao<String> dao) throws IOException {
        try {
            return dao.get(key()).equals(value());
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public String key() {
        return parts()[0];
    }

    public String value() {
        return parts()[1];
    }

    public String raw() {
        return raw;
    }

    public boolean valid() {
        return parts().length == 2;
    }

    private String[] parts() {
        return raw.split(separator, 2);
    }
}
