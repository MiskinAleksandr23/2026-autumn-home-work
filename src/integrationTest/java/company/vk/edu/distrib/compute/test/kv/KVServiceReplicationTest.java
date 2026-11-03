package company.vk.edu.distrib.compute.test.kv;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.HttpService;
import company.vk.edu.distrib.compute.kv.KVService;
import company.vk.edu.distrib.compute.kv.ReplicatedKVServiceTest;
import company.vk.edu.distrib.compute.test.AbstractArgumentsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.IOException;
import java.net.BindException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static company.vk.edu.distrib.compute.test.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

@ParameterizedClass(allowZeroInvocations = true)
@ArgumentsSource(KVServiceReplicationTest.ArgumentsProvider.class)
@EnabledIfEnvironmentVariable(named = "CURRENT_DATE", matches = "2026-(10-22|10-23|10-24|10-25|10-26|10-27|10-28|10-29|10-30|10-31|11-01|11-02|11-03)")
class KVServiceReplicationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String ENTITY_PATH = "/v0/entity?id=";
    public static final String CLUSTER = "CLUSTER";

    @Parameter
    AbstractHttpServiceFactory<KVService> kvServiceFactory;

    @AfterEach
    void afterEach() {
        System.clearProperty(CLUSTER);
    }

    @AfterAll
    static void afterAll() {
        HTTP_CLIENT.close();
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void quorumWriteReadSuccess() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            var cluster = makeCluster(5); // Start with 5 nodes
            System.setProperty(CLUSTER, describeCluster(cluster));
            try {
                String key = randomKey();
                byte[] value = randomValue();

                stopNode(cluster); // 4 live nodes left
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(201, update(ENTITY_PATH + key + ack(4), value).statusCode()));

                stopNode(cluster); // 3 live nodes left
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () -> {
                    HttpResponse<byte[]> response = get(ENTITY_PATH + key + ack(2), byte[].class);
                    assertEquals(200, response.statusCode());
                    assertArrayEquals(value, response.body());
                });
            } finally {
                cluster.values().forEach(HttpService::stop);
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void quorumWriteDeleteReadSuccess() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            var cluster = makeCluster(5); // Start with 5 nodes
            System.setProperty(CLUSTER, describeCluster(cluster));
            try {
                String key = randomKey();
                byte[] value = randomValue();

                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(201, update(ENTITY_PATH + key + ack(5), value).statusCode()));

                stopNode(cluster); // 4 live nodes left
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(202, delete(ENTITY_PATH + key + ack(4)).statusCode()));

                stopNode(cluster); // 3 live nodes left
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () -> {
                    HttpResponse<byte[]> response = get(ENTITY_PATH + key + ack(3), byte[].class);
                    assertEquals(404, response.statusCode());
                });
            } finally {
                cluster.values().forEach(HttpService::stop);
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void quorumWriteFailure() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            var cluster = makeCluster(3);
            System.setProperty(CLUSTER, describeCluster(cluster));
            try {
                String key = randomKey();
                byte[] value = randomValue();

                stopNode(cluster); // 2 live nodes
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(503, update(ENTITY_PATH + key + ack(3), value).statusCode()));
            } finally {
                cluster.values().forEach(HttpService::stop);
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void quorumReadFailure() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            var cluster = makeCluster(3);
            System.setProperty(CLUSTER, describeCluster(cluster));
            try {
                String key = randomKey();
                byte[] value = randomValue();

                stopNode(cluster); // 2 live nodes
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(201, update(ENTITY_PATH + key + ack(2), value).statusCode()));

                stopNode(cluster); // 1 live node
                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () -> {
                    HttpResponse<byte[]> response = get(ENTITY_PATH + key + ack(2), byte[].class);
                    assertEquals(503, response.statusCode());
                });
            } finally {
                cluster.values().forEach(HttpService::stop);
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void ackTooLarge() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            var cluster = makeCluster(1);
            System.setProperty(CLUSTER, describeCluster(cluster));
            try {
                String key = randomKey();
                byte[] value = randomValue();

                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(400, update(ENTITY_PATH + key + ack(2), value).statusCode()));
            } finally {
                cluster.values().forEach(HttpService::stop);
            }
        });
    }

    @RetryingTest(onExceptions = {BindException.class}, maxAttempts = 10, suspendForMs = 500)
    void ackIsZero() {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            var cluster = makeCluster(1);
            System.setProperty(CLUSTER, describeCluster(cluster));
            try {
                String key = randomKey();

                runHttpCtx(HTTP_CLIENT, randomNode(cluster), () ->
                        assertEquals(400, get(ENTITY_PATH + key + ack(0)).statusCode()));
            } finally {
                cluster.values().forEach(HttpService::stop);
            }
        });
    }

    private SequencedMap<Integer, KVService> makeCluster(int clusterSize) throws IOException {
        SequencedMap<Integer, KVService> cluster = new LinkedHashMap<>();
        for (int i = 0; i < clusterSize; i++) {
            int port = randomPort();
            KVService node = kvServiceFactory.create(port);
            node.start();
            cluster.put(port, node);
        }
        return cluster;
    }

    private String describeCluster(Map<Integer, KVService> cluster) {
        return cluster.keySet().stream().map(Object::toString).collect(Collectors.joining(","));
    }

    private int randomNode(Map<Integer, KVService> nodes) {
        return nodes.keySet().stream().skip(ThreadLocalRandom.current().nextLong(nodes.size())).findFirst().orElseThrow();
    }

    private void stopNode(SequencedMap<Integer, KVService> cluster) {
        cluster.pollFirstEntry().getValue().stop();
    }

    private String ack(int ack) {
        return "&ack=" + ack;
    }

    private static String randomKey() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    private static byte[] randomValue() {
        final byte[] result = new byte[512];
        ThreadLocalRandom.current().nextBytes(result);
        return result;
    }

    public static class ArgumentsProvider extends AbstractArgumentsProvider {

        public ArgumentsProvider() {
            super(AbstractArgumentsProvider.findAnnotatedFactories(ReplicatedKVServiceTest.class), AbstractHttpServiceFactory.class);
        }
    }
}
