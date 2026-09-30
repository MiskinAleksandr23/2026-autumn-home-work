package company.vk.edu.distrib.compute.k0oshara.urlshortener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import company.vk.edu.distrib.compute.Dao;

final class PersistentDao implements Dao<String> {
    private static final byte UPSERT = 1;
    private static final byte DELETE = 2;
    private static final int RECORD_HEADER_SIZE = 1 + Integer.BYTES * 2;

    private final Path journal;
    private final Properties values = new Properties();
    private final ReentrantLock lock = new ReentrantLock();

    PersistentDao(Path file) throws IOException {
        Path normalizedFile = file.toAbsolutePath().normalize();
        journal = Path.of(normalizedFile + ".log");
        Files.createDirectories(normalizedFile.getParent());
        if (Files.exists(normalizedFile)) {
            try (InputStream input = Files.newInputStream(normalizedFile)) {
                values.loadFromXML(input);
            } catch (RuntimeException exception) {
                throw new IOException("Invalid DAO file", exception);
            }
        } else {
            try (OutputStream output = Files.newOutputStream(normalizedFile)) {
                values.storeToXML(output, null, StandardCharsets.UTF_8);
            }
        }
        replayJournal();
    }

    @Override
    public String get(String key) {
        checkKey(key);
        String value = values.getProperty(key);
        if (value == null) {
            throw new NoSuchElementException(key);
        }
        return value;
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        checkKey(key);
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        lock.lock();
        try {
            append(UPSERT, key, value);
            values.setProperty(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IOException {
        checkKey(key);
        lock.lock();
        try {
            if (values.containsKey(key)) {
                append(DELETE, key, null);
                values.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            values.clear();
        } finally {
            lock.unlock();
        }
    }

    private void replayJournal() throws IOException {
        if (!Files.exists(journal)) {
            return;
        }
        long remaining = Files.size(journal);
        try (DataInputStream input = new DataInputStream(Files.newInputStream(journal))) {
            while (remaining > 0) {
                remaining -= readRecord(input, remaining);
            }
        }
    }

    private long readRecord(DataInputStream input, long remaining) throws IOException {
        if (remaining < RECORD_HEADER_SIZE) {
            throw new IOException("Truncated DAO journal record");
        }
        byte operation = input.readByte();
        int keyLength = input.readInt();
        int valueLength = input.readInt();
        validate(operation, keyLength, valueLength);
        long dataLength = (long) keyLength + Math.max(valueLength, 0);
        if (dataLength > remaining - RECORD_HEADER_SIZE) {
            throw new IOException("Truncated DAO journal value");
        }
        String key = readString(input, keyLength);
        String value = valueLength < 0 ? null : readString(input, valueLength);
        if (operation == UPSERT) {
            values.setProperty(key, value);
        } else {
            values.remove(key);
        }
        return RECORD_HEADER_SIZE + dataLength;
    }

    private static String readString(DataInputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void append(byte operation, String key, String value) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(journal,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            output.writeByte(operation);
            output.writeInt(keyBytes.length);
            output.writeInt(valueBytes == null ? -1 : valueBytes.length);
            output.write(keyBytes);
            if (valueBytes != null) {
                output.write(valueBytes);
            }
        }
    }

    private static void validate(byte operation, int keyLength, int valueLength) throws IOException {
        if (keyLength <= 0 || operation != UPSERT && operation != DELETE
                || operation == DELETE && valueLength != -1
                || operation == UPSERT && valueLength < 0) {
            throw new IOException("Invalid DAO journal record");
        }
    }

    private static void checkKey(String key) {
        if (Objects.toString(key, "").isEmpty()) {
            throw new IllegalArgumentException("Key must not be empty");
        }
    }
}
