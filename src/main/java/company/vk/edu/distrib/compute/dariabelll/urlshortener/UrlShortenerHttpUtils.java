package company.vk.edu.distrib.compute.dariabelll.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class UrlShortenerHttpUtils {

    private static final int EMPTY_RESPONSE_LENGTH = -1;
    private static final String CONTENT_TYPE_TEXT = "text/html; charset=utf-8";

    private UrlShortenerHttpUtils() {
    }

    static @Nullable String extractRedirectId(String path) {
        if (path.lastIndexOf('/') != 0) {
            return null;
        }
        return path.substring(1);
    }

    static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8
        );
    }

    static void sendEmptyResponse(
            HttpExchange exchange,
            int status
    ) throws IOException {
        exchange.sendResponseHeaders(status, EMPTY_RESPONSE_LENGTH);
    }

    static void sendTextResponse(
            HttpExchange exchange,
            int status,
            String response
    ) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_TEXT);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
