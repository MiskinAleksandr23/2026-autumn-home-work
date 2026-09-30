package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class BaseController {
    private final int port;
    private final Map<String, Function<Request, Response>> methods = Map.of(
            "GET", this::get,
            "POST", this::post,
            "PUT", this::put,
            "DELETE", this::delete
    );
    private final List<Middleware> middlewares;

    public BaseController(int port) {
        this(port, List.of());
    }

    public BaseController(int port, List<Middleware> middlewares) {
        this.port = port;
        this.middlewares = List.copyOf(middlewares);
    }

    public HttpHandler handler(List<Middleware> globalMiddlewares) {
        var pipeline = new ArrayList<>(globalMiddlewares);
        pipeline.addAll(middlewares);
        var allMiddlewares = List.copyOf(pipeline);
        return exchange -> handle(exchange, allMiddlewares);
    }

    private void handle(HttpExchange exchange, List<Middleware> allMiddlewares) throws IOException {
        try (exchange) {
            var request = Request.from(exchange);
            var method = methods.getOrDefault(exchange.getRequestMethod(), value -> methodNotAllowed());
            var handler = chain(value -> Objects.requireNonNull(method.apply(value)), allMiddlewares);
            var response = handler.handle(request);
            makeExchange(exchange, response);
        }
    }

    private void makeExchange(HttpExchange exchange, Response response) throws IOException {
        response.headers().forEach((k, v) -> {
            exchange.getResponseHeaders().set(k, v);
        });
        exchange.sendResponseHeaders(response.status(), response.length());

        try (var out = exchange.getResponseBody()) {
            out.write(response.content().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Handler chain(
            Handler handler,
            List<Middleware> middlewares
    ) {
        Handler result = handler;

        for (int i = middlewares.size() - 1; i >= 0; i--) {
            result = middlewares.get(i).apply(result);
        }

        return result;
    }

    protected int port() {
        return port;
    }

    private Response methodNotAllowed() {
        return Response.builder()
                .setStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .build();
    }

    public abstract String path();

    public Response get(Request request) {
        return methodNotAllowed();
    }

    public Response post(Request request) {
        return methodNotAllowed();
    }

    public Response put(Request request) {
        return methodNotAllowed();
    }

    public Response delete(Request request) {
        return methodNotAllowed();
    }
}
