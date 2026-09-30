package company.vk.edu.distrib.compute.dariabelll.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class JournaledDao implements Dao<String> {

    private static final byte RECORD_TERMINATOR = '\n';
    private static final String FIELD_SEPARATOR = "\t";
    private static final int PUT_RECORD_FIELDS_COUNT = 3;
    private static final String PUT_OPERATION = "P";
    private static final int DELETE_RECORD_FIELDS_COUNT = 2;
    private static final String DELETE_OPERATION = "D";

    private final Path filePath;
    private final Map<String, String> entries;
    private final RandomAccessFile journal;
    private final ReentrantLock writeLock = new ReentrantLock();
    private boolean journalFailed;

    public JournaledDao(Path path) throws IOException {
        filePath = path.toAbsolutePath();
        entries = new ConcurrentHashMap<>();

        Files.createDirectories(filePath.getParent());
        journal = new RandomAccessFile(filePath.toFile(), "rw");
        try {
            load();
        } catch (IOException | RuntimeException e) {
            try {
                journal.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException {
        String value = entries.get(key);
        if (value == null) {
            throw new NoSuchElementException(key);
        }
        return value;
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException, IOException {
        writeLock.lock();
        try {
            append(putRecord(key, value));
            entries.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IllegalArgumentException, IOException {
        writeLock.lock();
        try {
            append(DELETE_OPERATION + FIELD_SEPARATOR + encode(key) + '\n');
            entries.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        writeLock.lock();
        try {
            if (!journal.getChannel().isOpen()) {
                return;
            }
            try (journal) {
                if (journalFailed) {
                    throw new IOException("Cannot compact a journal after a failed rollback");
                }
                save();
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String putRecord(String key, String value) {
        return PUT_OPERATION + FIELD_SEPARATOR + encode(key) + FIELD_SEPARATOR + encode(value) + '\n';
    }

    private void append(String record) throws IOException {
        if (journalFailed) {
            throw new IOException("Journal rollback failed");
        }
        long start = journal.getFilePointer();
        try {
            journal.write(record.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            try {
                journal.setLength(start);
                journal.seek(start);
            } catch (IOException rollbackException) {
                journalFailed = true;
                e.addSuppressed(rollbackException);
            }
            throw e;
        }
    }

    private void load() throws IOException {
        while (journal.getFilePointer() < journal.length()) {
            long start = journal.getFilePointer();
            String record = journal.readLine();
            if (record == null) {
                break;
            }
            long end = journal.getFilePointer();
            journal.seek(end - 1);
            if (journal.readByte() != RECORD_TERMINATOR) {
                journal.setLength(start);
                journal.seek(start);
                break;
            }
            applyRecord(record);
        }
    }

    private void applyRecord(String record) throws IOException {
        String[] fields = record.split(FIELD_SEPARATOR, -1);
        try {
            if (fields.length == PUT_RECORD_FIELDS_COUNT && PUT_OPERATION.equals(fields[0])) {
                entries.put(decode(fields[1]), decode(fields[2]));
            } else if (fields.length == DELETE_RECORD_FIELDS_COUNT && DELETE_OPERATION.equals(fields[0])) {
                entries.remove(decode(fields[1]));
            } else {
                throw new IOException("Invalid journal record");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid journal encoding", e);
        }
    }

    private void save() throws IOException {
        Path tempFile = Files.createTempFile(
                filePath.getParent(),
                "journal-",
                ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    writer.write(putRecord(entry.getKey(), entry.getValue()));
                }
            }
            journal.close();
            Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public boolean isStorageAccessible() {
        writeLock.lock();
        try {
            Path directory = filePath.getParent();
            return !journalFailed
                    && journal.getChannel().isOpen()
                    && Files.isDirectory(directory)
                    && Files.isWritable(directory)
                    && Files.isExecutable(directory)
                    && Files.isRegularFile(filePath)
                    && Files.isReadable(filePath)
                    && Files.isWritable(filePath);
        } finally {
            writeLock.unlock();
        }
    }
}
