package company.vk.edu.distrib.compute.rsmt98.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class FileStringDao implements Dao<String> {
    private final Path dir;
    private final Lock lock = new ReentrantLock();
    private boolean isClosed;

    public FileStringDao(Path path) throws IOException {
        this.dir = path;
        Files.createDirectories(path);
    }

    @Override
    public String get(String key) throws IOException {
        lock.lock();
        try {
            checkOpen();
            return Files.readString(resolveKeyPath(key), StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new IOException("Stored value is not valid UTF-8", e);
        } catch (NoSuchFileException e) {
            if (!Files.isDirectory(dir)) {
                throw e;
            }
            throw new NoSuchElementException("No value exists for the key", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        lock.lock();
        try {
            checkOpen();
            Path target = resolveKeyPath(key);
            Path tmpFile = Files.createTempFile(dir, null, null);
            try {
                try (FileChannel channel = FileChannel.open(tmpFile, StandardOpenOption.WRITE);
                        var writer =
                                new BufferedWriter(
                                        Channels.newWriter(channel, StandardCharsets.UTF_8))) {
                    writer.write(value);
                    writer.flush();
                    channel.force(true);
                }
                Files.move(
                        tmpFile,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException | Error e) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ee) {
                    e.addSuppressed(ee);
                }
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IOException {
        lock.lock();
        try {
            checkOpen();
            if (!Files.deleteIfExists(resolveKeyPath(key)) && !Files.isDirectory(dir)) {
                throw new NoSuchFileException(dir.toString());
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isAvailable() {
        lock.lock();
        try {
            return !isClosed
                    && Files.isDirectory(dir)
                    && Files.isReadable(dir)
                    && Files.isWritable(dir);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            isClosed = true;
        } finally {
            lock.unlock();
        }
    }

    private void checkOpen() throws IOException {
        if (isClosed) {
            throw new IOException("Storage is closed");
        }
    }

    private Path resolveKeyPath(String key) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        for (int i = 0; i < key.length(); ++i) {
            char ch = key.charAt(i);
            digest.update((byte) (ch >>> Byte.SIZE));
            digest.update((byte) ch);
        }
        return dir.resolve(HexFormat.of().formatHex(digest.digest()) + ".txt");
    }
}
