package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

public record Request(String path, String body, Map<String, String> headers) {
    public Request {
        headers = Map.copyOf(headers);
    }

    public static Request from(HttpExchange exchange) throws IOException {
        var headers = new ConcurrentHashMap<String, String>();

        exchange.getRequestHeaders().forEach((key, values) ->
                headers.put(key, values.getFirst()));

        return new Request(
                exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                headers
        );
    }
}
