package company.vk.edu.distrib.compute.test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.function.Executable;

public enum TestUtils {
    ;

    public static final ScopedValue<HttpContext> HTTP_CONTEXT = ScopedValue.newInstance();

    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final String TEST_LINK_ID = "10db3750xY";
    public static final String TEST_LONG_LINK = "https://ya.ru/search/?text=test";
    public static final String TEST_LONG_LINK_2 = "https://ya.ru/search/?text=test2";
    public static final String CONTENT_TYPE_TEXT = "text/html; charset=utf-8";
    public static final Credentials TEST_CREDENTIALS = new Credentials("test-user", "super_pass");
    public static final Credentials SPOTTY_TEST_CREDENTIALS = new Credentials("spotty", "tasty bones");

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    public static int randomPort() {
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 100_000; i++) {
                final var port = ThreadLocalRandom.current().nextInt(10000, 60000);
                if (isTcpPortAvailable(port)) {
                    return port;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while looking for available port");
            }
        }
        throw new IllegalStateException("Can't find available port");
    }

    public static boolean isTcpPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(false);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port), 1);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void runHttpCtx(HttpClient client, int port, Executable executable) {
        ScopedValue.where(HTTP_CONTEXT, new HttpContext(client, port)).run(() -> {
            try {
                executable.execute();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static int status() {
        try {
            HttpContext httpContext = HTTP_CONTEXT.get();
            HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(new URI("http://localhost:" + httpContext.port() + "/v0/status"))
                .timeout(REQUEST_TIMEOUT)
                .build();
            HttpResponse<Void> response = httpContext.client().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpResponse<String> get(String path) {
        return get(null, path, String.class);
    }

    public static <T> HttpResponse<T> get(String path, Class<T> clazz) {
        return get(null, path, clazz);
    }

    public static <T> HttpResponse<T> get(@Nullable Credentials credentials, String path, Class<T> clazz) {
        try {
            HttpContext httpContext = HTTP_CONTEXT.get();
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .GET()
                .uri(new URI("http://localhost:%d%s".formatted(httpContext.port(), path)))
                .timeout(REQUEST_TIMEOUT);
            if (credentials != null) {
                withAuthorization(request, credentials);
            }
            return httpContext.client().send(request.build(), bodyHandlerOf(clazz));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpResponse<Void> update(String path, String value) {
        return update(null, path, isOfString(value));
    }

    public static HttpResponse<Void> update(String path, byte[] value) {
        return update(null, path, isOfBytes(value));
    }

    public static HttpResponse<Void> update(String path, Supplier<? extends InputStream> bodySupplier) {
        return update(null, path, bodySupplier);
    }

    public static HttpResponse<Void> update(@Nullable Credentials credentials, String path, Supplier<? extends InputStream> bodySupplier) {
        try {
            HttpContext httpContext = HTTP_CONTEXT.get();
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofInputStream(bodySupplier))
                .uri(new URI("http://localhost:%d%s".formatted(httpContext.port(), path)))
                .header("Content-Type", CONTENT_TYPE_TEXT)
                .timeout(REQUEST_TIMEOUT);
            if (credentials != null) {
                withAuthorization(request, credentials);
            }
            return httpContext.client().send(request.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpResponse<Void> delete(String path) {
        return delete(null, path);
    }

    public static HttpResponse<Void> delete(@Nullable Credentials credentials, String path) {
        try {
            final var httpContext = HTTP_CONTEXT.get();
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .DELETE()
                .uri(new URI("http://localhost:%d%s".formatted(httpContext.port(), path)))
                .timeout(REQUEST_TIMEOUT);
            if (credentials != null) {
                withAuthorization(request, credentials);
            }
            return httpContext.client().send(request.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpResponse<String> post(String path, String value) {
        return post(null, path, value);
    }

    public static HttpResponse<String> post(@Nullable Credentials credentials, String path, String value) {
        try {
            HttpContext httpContext = HTTP_CONTEXT.get();
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(value))
                .uri(new URI("http://localhost:%d%s".formatted(httpContext.port(), path)))
                .header("Content-Type", CONTENT_TYPE_TEXT)
                .timeout(REQUEST_TIMEOUT);
            if (credentials != null) {
                withAuthorization(request, credentials);
            }
            return httpContext.client().send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Supplier<? extends InputStream> isOfBytes(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    public static Supplier<? extends InputStream> isOfString(String str) {
        return () -> new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    }

    public static <T> HttpResponse.BodyHandler<T> bodyHandlerOf(Class<T> clazz) {
        if (String.class.isAssignableFrom(clazz)) {
            return (HttpResponse.BodyHandler<T>) HttpResponse.BodyHandlers.ofString();
        } else if (byte[].class.isAssignableFrom(clazz)) {
            return (HttpResponse.BodyHandler<T>) HttpResponse.BodyHandlers.ofByteArray();
        } else {
            throw new IllegalArgumentException("unsupported class: " + clazz.getName());
        }
    }

    public static String extractId(int port, String shortLink) {
        final URI uri;
        try {
            uri = new URI(shortLink);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unexpected short link: " + shortLink, e);
        }
        if (uri.getHost() == null || uri.getPort() != port || uri.getPath() == null) {
            throw new IllegalArgumentException("Unexpected short link: " + shortLink);
        }

        String id = uri.getPath().replaceFirst("/", "");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Short link id is blank");
        }

        return id;
    }

    public static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElseThrow();
    }

    public static void withAuthorization(HttpRequest.Builder request, Credentials credentials) {
        if (credentials != null) {
            String token = Base64.getEncoder()
                .encodeToString((credentials.username() + ":" + credentials.password()).getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + token);
        }
    }

    public static void tryCreateTestUser() {
        try {
            createUser(TEST_CREDENTIALS);
        } catch (Exception ignored) {
        }
    }

    public static HttpResponse<Void> createUser(Credentials credentials) {
        try {
            HttpContext httpContext = HTTP_CONTEXT.get();
            HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(credentials.username() + ":" + credentials.password()))
                .uri(new URI("http://localhost:" + httpContext.port() + "/internal/users"))
                .header("Content-Type", CONTENT_TYPE_TEXT)
                .timeout(TIMEOUT)
                .build();
            return httpContext.client().send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record Credentials(String username, String password) {
    }

    public record HttpContext(HttpClient client, int port) {
    }
}
