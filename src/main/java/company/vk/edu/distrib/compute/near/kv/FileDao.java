package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;

import company.vk.edu.distrib.compute.Dao;

public final class FileDao implements Dao<byte[]> {
    private final Path directory;

    public FileDao(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    @Override
    public byte[] get(String key) throws IOException {
        try {
            return Files.readAllBytes(pathFor(key));
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException(key, e);
        }
    }

    @Override
    public void upsert(String key, byte[] value) throws IOException {
        Path target = pathFor(key);
        Path temporary = Files.createTempFile(directory, "write-", ".tmp");
        try {
            Files.write(temporary, value);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(pathFor(key));
    }

    @Override
    @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
    public void close() {
    }

    private Path pathFor(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Empty key");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
