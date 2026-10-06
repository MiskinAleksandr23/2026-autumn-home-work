package company.vk.edu.distrib.compute.vladimir_rusaleev.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.NoSuchElementException;

public final class FileDao implements Dao<String> {
    private final Path directory;

    public FileDao(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    @Override
    public String get(String key) throws IOException {
        try {
            return Files.readString(pathFor(key), StandardCharsets.UTF_8);
        } catch (NoSuchFileException exception) {
            throw new NoSuchElementException(key, exception);
        }
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Null value");
        }
        Files.writeString(pathFor(key), value, StandardCharsets.UTF_8);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(pathFor(key));
    }

    @Override
    public void close() {
        //
    }

    private Path pathFor(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Empty key");
        }
        String filename = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(filename);
    }
}
