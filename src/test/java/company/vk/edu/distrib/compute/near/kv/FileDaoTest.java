package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDaoTest {
    @TempDir
    Path directory;

    @Test
    void valuesAndDeletionSurviveReopening() throws IOException {
        byte[] value = {0, -1, 42};
        try (FileDao dao = new FileDao(directory)) {
            dao.upsert("binary", value);
            dao.upsert("empty", new byte[0]);
            dao.upsert("deleted", value);
            dao.delete("deleted");
        }
        try (FileDao dao = new FileDao(directory)) {
            assertArrayEquals(value, dao.get("binary"));
            assertArrayEquals(new byte[0], dao.get("empty"));
            assertThrows(NoSuchElementException.class, () -> dao.get("deleted"));
            dao.delete("deleted");
        }
    }

    @Test
    void arbitraryKeysStayInsideStorageDirectory() throws IOException {
        String[] keys = {"../outside", "ключ /?&=+%", "x".repeat(1000)};
        try (FileDao dao = new FileDao(directory)) {
            for (int i = 0; i < keys.length; i++) {
                dao.upsert(keys[i], new byte[]{(byte) i});
            }
            for (int i = 0; i < keys.length; i++) {
                assertArrayEquals(new byte[]{(byte) i}, dao.get(keys[i]));
            }
            assertThrows(IllegalArgumentException.class, () -> dao.get(""));
            assertThrows(IllegalArgumentException.class, () -> dao.upsert("", new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> dao.delete(""));
        }
        try (var files = Files.list(directory)) {
            assertEquals(keys.length, files.count());
        }
    }

    @Test
    void concurrentReadersSeeOnlyCompleteValues() throws Exception {
        byte[] first = new byte[65536];
        byte[] second = new byte[32768];
        Arrays.fill(first, (byte) 1);
        Arrays.fill(second, (byte) 2);
        try (FileDao dao = new FileDao(directory); var executor = Executors.newFixedThreadPool(2)) {
            dao.upsert("shared", first);
            var writer = executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    dao.upsert("shared", i % 2 == 0 ? first : second);
                }
                return null;
            });
            var reader = executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    byte[] value = dao.get("shared");
                    assertArrayEquals(value.length == first.length ? first : second, value);
                }
                return null;
            });
            writer.get();
            reader.get();
        }
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
