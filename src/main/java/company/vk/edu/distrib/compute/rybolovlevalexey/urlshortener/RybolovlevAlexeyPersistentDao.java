package company.vk.edu.distrib.compute.rybolovlevalexey.urlshortener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import company.vk.edu.distrib.compute.Dao;

public class RybolovlevAlexeyPersistentDao implements Dao<String> {
    private static final String UPSERT_ACTION = "upsert";
    private static final String REMOVE_ACTION = "remove";

    private final Map<String, String> storage = new ConcurrentHashMap<>();
    private final Path filePath;

    public RybolovlevAlexeyPersistentDao(Path filePath) throws IOException {
        this.filePath = filePath;

        Files.createDirectories(this.filePath.getParent());
        if (Files.notExists(filePath)) {
            Files.createFile(filePath);
        }

        load();
    }

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
        save(String.format("%s;%s;%s", UPSERT_ACTION, key, value));
    }

    @Override
    public void delete(String key) throws IllegalArgumentException, IOException {
        storage.remove(key);
        save(String.format("%s;%s", REMOVE_ACTION, key));
    }

    @Override
    public void close() throws IOException {
        // nothing to close
    }

    private void save(String action) throws IOException {
        Files.writeString(filePath,action + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private void load() throws IOException {
        if (!Files.exists(this.filePath)) {
            return;
        }

        List<String> lines = Files.readAllLines(this.filePath);

        for (String line: lines) {
            String[] terms = line.split(";");

            if (Objects.equals(UPSERT_ACTION, terms[0])) {
                storage.put(terms[1], terms[2]);
            }
            if (Objects.equals(REMOVE_ACTION, terms[0])) {
                storage.remove(terms[1]);
            }
        }
    }
}
