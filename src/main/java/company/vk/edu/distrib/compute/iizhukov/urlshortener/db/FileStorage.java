package company.vk.edu.distrib.compute.iizhukov.urlshortener.db;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FileStorage implements Closeable {
    private static final byte PUT = 1;
    private static final byte DELETE = 2;

    private final RandomAccessFile file;

    public FileStorage(File path) throws IOException {
        File parent = path.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        file = new RandomAccessFile(path, "rw");
    }

    public Map<String, String> read() throws IOException {
        var data = new ConcurrentHashMap<String, String>();
        load(data);
        write(data);
        return data;
    }

    public void upsert(String key, String value) throws IOException {
        append(PUT, key, value);
    }

    public void delete(String key) throws IOException {
        append(DELETE, key, "");
    }

    private void load(Map<String, String> data) throws IOException {
        file.seek(0);

        while (file.getFilePointer() < file.length()) {
            long position = file.getFilePointer();

            try {
                var command = file.readByte();
                var key = file.readUTF();

                if (command == PUT) {
                    data.put(key, file.readUTF());
                } else if (command == DELETE) {
                    data.remove(key);
                } else {
                    throw new IOException("Unknown command: " + command);
                }
            } catch (EOFException e) {
                file.setLength(position);
            }
        }
    }

    private void write(Map<String, String> data) throws IOException {
        file.setLength(0);

        for (var entry : data.entrySet()) {
            append(PUT, entry.getKey(), entry.getValue());
        }
    }

    private void append(byte command, String key, String value) throws IOException {
        long position = file.length();
        file.seek(position);

        try {
            file.writeByte(command);
            file.writeUTF(key);

            if (command == PUT) {
                file.writeUTF(value);
            }
        } catch (IOException e) {
            file.setLength(position);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
