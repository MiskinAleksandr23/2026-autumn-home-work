package company.vk.edu.distrib.compute.sovesti.urlshortener.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;
import java.util.Objects;

import company.vk.edu.distrib.compute.Dao;

public final class InFileDao implements Dao<String> {

    private final Path path;
    private final PrintWriter write;
    private final DaoOperations operations;
    private final Dao<String> memory;

    public InFileDao(Path path) throws IOException {
        this.path = Objects.requireNonNull(path);
        write = new PrintWriter(open(path), true);
        operations = new DaoOperations();
        memory = new InMemoryDao<>();
    }

    private OutputStream open(Path path) throws IOException {
        return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void read() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            operations.fill(reader.lines());
        }
        operations.execute(memory);
    }

    @Override
    public void close() throws IOException {
        write.close();
        memory.close();
    }

    @Override
    public String get(String key) throws NoSuchElementException, IOException {
        return memory.get(key);
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        addOperation(new DaoOperation.Upsert(key, value));
    }

    @Override
    public void delete(String key) throws IOException {
        addOperation(new DaoOperation.Delete(key));
    }

    private void addOperation(DaoOperation op) throws IOException {
        operations.add(op);
        op.execute(memory);
        write.println(new KeyValuePair(op.label(), op.serialized()).raw());
    }
}
