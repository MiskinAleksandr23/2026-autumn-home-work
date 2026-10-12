package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.NoSuchElementException;

import company.vk.edu.distrib.compute.Dao;

public final class NearRemoteDao implements Dao<String> {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int NOT_FOUND = 404;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int SINGLE_NODE_COUNT = 1;

    private final int[] ports;
    private final String[] endpoints;
    private final HttpClient client;

    public NearRemoteDao(int... ports) {
        if (ports.length == 0) {
            throw new IllegalArgumentException("Expected at least one KV port");
        }
        this.ports = ports.clone();
        Arrays.sort(this.ports);
        endpoints = new String[this.ports.length];
        for (int i = 0; i < this.ports.length; i++) {
            int port = this.ports[i];
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException("Port out of range");
            }
            if (i > 0 && port == this.ports[i - 1]) {
                throw new IllegalArgumentException("Duplicate KV port");
            }
            endpoints[i] = "http://localhost:" + port + "/v0/entity?id=";
        }
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT).version(HttpClient.Version.HTTP_1_1).build();
    }

    @Override
    public String get(String key) throws IOException {
        HttpResponse<byte[]> response = send(request(key).GET().build());
        if (response.statusCode() == NOT_FOUND) {
            throw new NoSuchElementException(key);
        }
        checkStatus(response, 200);
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        HttpResponse<byte[]> response = send(request(key)
            .PUT(HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8)).build());
        checkStatus(response, 201);
    }

    @Override
    public void delete(String key) throws IOException {
        checkStatus(send(request(key).DELETE().build()), 202);
    }

    @Override
    public void close() {
        client.close();
    }

    private HttpRequest.Builder request(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Empty key");
        }
        return HttpRequest.newBuilder(URI.create(endpointFor(key) + URLEncoder.encode(key, StandardCharsets.UTF_8)))
            .timeout(TIMEOUT);
    }

    private String endpointFor(String key) {
        if (ports.length == SINGLE_NODE_COUNT) {
            return endpoints[0];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            int owner = 0;
            long bestScore = 0;
            for (int i = 0; i < ports.length; i++) {
                digest.update(keyBytes);
                digest.update((byte) (ports[i] >>> Byte.SIZE));
                digest.update((byte) ports[i]);
                long score = ByteBuffer.wrap(digest.digest()).getLong();
                if (i == 0 || Long.compareUnsigned(score, bestScore) > 0) {
                    owner = i;
                    bestScore = score;
                }
            }
            return endpoints[owner];
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("KV request interrupted", e);
        }
    }

    private static void checkStatus(HttpResponse<?> response, int expected) throws IOException {
        if (response.statusCode() != expected) {
            throw new IOException("Unexpected KV status: " + response.statusCode());
        }
    }
}
