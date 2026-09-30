package company.vk.edu.distrib.compute.test.kv;

import java.net.BindException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.kv.KVService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junitpioneer.jupiter.RetryingTest;

import static company.vk.edu.distrib.compute.test.TestUtils.TIMEOUT;
import static company.vk.edu.distrib.compute.test.TestUtils.delete;
import static company.vk.edu.distrib.compute.test.TestUtils.get;
import static company.vk.edu.distrib.compute.test.TestUtils.randomPort;
import static company.vk.edu.distrib.compute.test.TestUtils.runHttpCtx;
import static company.vk.edu.distrib.compute.test.TestUtils.update;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@ParameterizedClass(allowZeroInvocations = true)
@ArgumentsSource(KVServiceFactoryArgumentsProvider.class)
@EnabledIfEnvironmentVariable(named = "CURRENT_DATE", matches = "2026-(09-28|09-29|09-30|10-01|10-02|10-03|10-04|10-05|10-06)")
class KVServiceSingleNodeTest {

    static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    static final String ENTITY_PATH = "/v0/entity/";

    @Parameter
    AbstractHttpServiceFactory<KVService> kvServiceFactory;

    @AfterAll
    static void afterAll() {
        HTTP_CLIENT.close();
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void emptyKey() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            String path = ENTITY_PATH;
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    assertEquals(400, get(path).statusCode());
                    assertEquals(400, delete(path).statusCode());
                    assertEquals(400, update(path, new byte[]{0}).statusCode());
                });
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void badRequest() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> assertEquals(404, get("/abracadabra").statusCode()));
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void getAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> assertEquals(404, get(ENTITY_PATH + "missing").statusCode()));
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void deleteAbsent() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> assertEquals(202, delete(ENTITY_PATH + "absent").statusCode()));
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void insert() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    String key = randomKey();
                    byte[] value = randomValue();

                    assertEquals(201, update(ENTITY_PATH + key, value).statusCode());

                    HttpResponse<byte[]> response = get(ENTITY_PATH + key, byte[].class);
                    assertEquals(200, response.statusCode());
                    assertArrayEquals(value, response.body());
                });
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void insertEmpty() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    final String key = randomKey();
                    final byte[] value = new byte[0];

                    final var path = ENTITY_PATH + key;
                    assertEquals(201, update(path, value).statusCode());

                    final HttpResponse<byte[]> response = get(path, byte[].class);
                    assertEquals(200, response.statusCode());
                    assertArrayEquals(value, response.body());
                });
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void lifecycle2keys() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    final String key1 = randomKey();
                    final byte[] value1 = randomValue();
                    final String key2 = randomKey();
                    final byte[] value2 = randomValue();

                    assertEquals(201, update(ENTITY_PATH + key1, value1).statusCode());

                    assertArrayEquals(value1, get(ENTITY_PATH + key1, byte[].class).body());

                    assertEquals(201, update(ENTITY_PATH + key2, value2).statusCode());

                    assertArrayEquals(value1, get(ENTITY_PATH + key1, byte[].class).body());
                    assertArrayEquals(value2, get(ENTITY_PATH + key2, byte[].class).body());

                    assertEquals(202, delete(ENTITY_PATH + key1).statusCode());

                    assertEquals(404, get(ENTITY_PATH + key1).statusCode());
                    assertArrayEquals(value2, get(ENTITY_PATH + key2, byte[].class).body());

                    assertEquals(202, delete(ENTITY_PATH + key2).statusCode());

                    assertEquals(404, get(ENTITY_PATH + key2).statusCode());
                });
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void upsert() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    final String key = randomKey();
                    final byte[] value1 = randomValue();
                    final byte[] value2 = randomValue();

                    assertEquals(201, update(ENTITY_PATH + key, value1).statusCode());

                    assertEquals(201, update(ENTITY_PATH + key, value2).statusCode());

                    HttpResponse<byte[]> response = get(ENTITY_PATH + key, byte[].class);
                    assertEquals(200, response.statusCode());
                    assertArrayEquals(value2, response.body());
                });
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void upsertEmpty() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    final String key = randomKey();
                    final byte[] value = randomValue();
                    final byte[] empty = new byte[0];

                    assertEquals(201, update(ENTITY_PATH + key, value).statusCode());

                    assertEquals(201, update(ENTITY_PATH + key, empty).statusCode());

                    HttpResponse<byte[]> response = get(ENTITY_PATH + key, byte[].class);
                    assertEquals(200, response.statusCode());
                    assertArrayEquals(empty, response.body());
                });
            } finally {
                storage.stop();
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void deleteTest() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            int port = randomPort();
            KVService storage = kvServiceFactory.create(port);
            storage.start();
            try {
                runHttpCtx(HTTP_CLIENT, port, () -> {
                    final String key = randomKey();
                    final byte[] value = randomValue();

                    assertEquals(201, update(ENTITY_PATH + key, value).statusCode());

                    assertEquals(202, delete(ENTITY_PATH + key).statusCode());

                    assertEquals(404, get(ENTITY_PATH + key).statusCode());
                });
            } finally {
                storage.stop();
            }
        });
    }

    static String randomKey() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    static byte[] randomValue() {
        final byte[] result = new byte[1024];
        ThreadLocalRandom.current().nextBytes(result);
        return result;
    }

}
