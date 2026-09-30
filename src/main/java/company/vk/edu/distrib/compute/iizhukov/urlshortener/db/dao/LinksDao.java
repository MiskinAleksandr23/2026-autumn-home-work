package company.vk.edu.distrib.compute.iizhukov.urlshortener.db.dao;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.DataValidationUtils;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.FileStorage;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.StorageException;

public final class LinksDao implements Dao<String> {
    private static final LinksDao INSTANCE = new LinksDao();
    private final Map<String, String> links;
    private final FileStorage storage;
    private final ReentrantLock lock = new ReentrantLock();

    private LinksDao() {
        try {
            storage = new FileStorage(new File("/tmp/iizhukov-urlshortener/links.db"));
            links = storage.read();
        } catch (IOException e) {
            throw new StorageException("cant open file", e);
        }
    }

    public static LinksDao create() {
        return INSTANCE;
    }

    @Override
    public String get(String key) throws IllegalArgumentException {
        DataValidationUtils.validateKey(key);
        var value = links.get(key);

        if (value == null) {
            throw new NoSuchElementException();
        }

        return value;
    }

    @Override
    public void upsert(String key, String value) throws IllegalArgumentException {
        DataValidationUtils.validateKey(key);
        DataValidationUtils.validateUrl(value);
        lock.lock();
        try {
            storage.upsert(key, value);
            links.put(key, value);
        } catch (IOException e) {
            throw new StorageException("cant write file =(", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) throws IllegalArgumentException {
        DataValidationUtils.validateKey(key);
        lock.lock();
        try {
            storage.delete(key);
            links.remove(key);
        } catch (IOException e) {
            throw new StorageException("cant write file =(", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        try {
            storage.close();
        } catch (IOException e) {
            throw new StorageException("cant close file", e);
        }
    }
}
