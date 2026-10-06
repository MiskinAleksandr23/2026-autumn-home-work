package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.kv.KVService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NearKVService implements KVService {
    private static final Logger log = LoggerFactory.getLogger(NearKVService.class);
    private static final String ENTITY_PATH = "/v0/entity";
    private static final byte[] EMPTY_BODY = new byte[0];

    private final int port;
    private final Dao<byte[]> dao;
    private final HttpServer server;
    private final @Nullable ExecutorService executor;

    public NearKVService(int port, Dao<byte[]> dao, int threads) throws IOException {
        if (threads < 1) {
            throw new IllegalArgumentException("Expected a positive thread count");
        }
        this.port = port;
        this.dao = dao;
        server = HttpServer.create();
        server.createContext("/", this::handle);
        executor = threads == 1 ? null : Executors.newFixedThreadPool(threads);
        if (executor != null) {
            server.setExecutor(executor);
        }
    }

    @Override
    public void start() {
        try {
            server.bind(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.start();
    }

    @Override
    public void stop() {
        server.stop(1);
        if (executor != null) {
            executor.close();
        }
        try {
            dao.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                route(exchange);
            } catch (NoSuchElementException e) {
                respond(exchange, 404, EMPTY_BODY);
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, EMPTY_BODY);
            } catch (IOException e) {
                log.warn("KV request failed", e);
                respond(exchange, 503, EMPTY_BODY);
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/v0/status".equals(path)) {
            respond(exchange, "GET".equals(exchange.getRequestMethod()) ? 200 : 405, EMPTY_BODY);
            return;
        }
        if (!ENTITY_PATH.equals(path) && !path.startsWith(ENTITY_PATH + "/")) {
            respond(exchange, 404, EMPTY_BODY);
            return;
        }
        String key = readKey(exchange);
        switch (exchange.getRequestMethod()) {
            case "GET" -> respond(exchange, 200, dao.get(key));
            case "PUT" -> {
                dao.upsert(key, exchange.getRequestBody().readAllBytes());
                respond(exchange, 201, EMPTY_BODY);
            }
            case "DELETE" -> {
                dao.delete(key);
                respond(exchange, 202, EMPTY_BODY);
            }
            default -> respond(exchange, 405, EMPTY_BODY);
        }
    }

    private static String readKey(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith(ENTITY_PATH + "/")) {
            return requireKey(path.substring(ENTITY_PATH.length() + 1));
        }
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            throw new IllegalArgumentException("Missing id");
        }
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && "id".equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                return requireKey(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        throw new IllegalArgumentException("Missing id");
    }

    private static String requireKey(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Empty id");
        }
        return key;
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
    }
}
