package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;

import company.vk.edu.distrib.compute.Dao;

public final class NearRemoteDao implements Dao<String> {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int NOT_FOUND = 404;

    private final String endpoint;
    private final HttpClient client;

    public NearRemoteDao(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range");
        }
        endpoint = "http://localhost:" + port + "/v0/entity?id=";
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
        return HttpRequest.newBuilder(URI.create(endpoint + URLEncoder.encode(key, StandardCharsets.UTF_8)))
            .timeout(TIMEOUT);
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
