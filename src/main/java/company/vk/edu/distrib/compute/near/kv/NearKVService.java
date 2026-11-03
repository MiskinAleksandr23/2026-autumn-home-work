package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.kv.KVService;
import company.vk.edu.distrib.compute.near.kv.ReplicaCoordinator.Result;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NearKVService implements KVService {
    private static final Logger log = LoggerFactory.getLogger(NearKVService.class);
    private static final String ENTITY_PATH = "/v0/entity";
    private static final String STATUS_PATH = "/v0/status";
    private static final String ID_PARAMETER = "id";
    private static final String ACK_PARAMETER = "ack";
    private static final int MIN_ACK = 1;
    private static final int SINGLE_THREAD_COUNT = 1;
    private static final int QUERY_PARAMETER_PARTS = 2;
    private static final int MAX_VALUE_BYTES = 1024 * 1024;
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final Set<String> METHODS = Set.of("GET", "PUT", "DELETE");

    private final int port;
    private final Dao<byte[]> dao;
    private final HttpServer server;
    private final @Nullable ExecutorService executor;
    private final ReplicaCoordinator coordinator;

    public NearKVService(int port, Dao<byte[]> dao, int threads) throws IOException {
        this(port, dao, threads, new int[0], MIN_ACK);
    }

    public NearKVService(int port, Dao<byte[]> dao, int threads, int[] clusterPorts, int replicas) throws IOException {
        if (threads < SINGLE_THREAD_COUNT) {
            throw new IllegalArgumentException("Expected a positive thread count");
        }
        this.port = port;
        this.dao = dao;
        coordinator = clusterPorts.length == 0 ? new ReplicaCoordinator(port, dao)
            : new ReplicaCoordinator(port, dao, clusterPorts, replicas);
        try {
            server = HttpServer.create();
        } catch (IOException e) {
            coordinator.close();
            throw e;
        }
        server.createContext("/", this::handle);
        executor = threads == SINGLE_THREAD_COUNT ? null : Executors.newFixedThreadPool(threads);
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
        try (coordinator) {
            dao.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handle(HttpExchange exchange) {
        CompletableFuture<Result> result;
        try {
            result = route(exchange);
        } catch (IllegalArgumentException e) {
            result = completed(400);
        } catch (IOException e) {
            log.warn("KV request failed", e);
            result = completed(503);
        }
        result.exceptionally(error -> {
            log.warn("KV replication failed", error);
            return new Result(503, EMPTY_BODY);
        }).thenAccept(reply -> {
            try (exchange) {
                respond(exchange, reply.status(), reply.body());
            } catch (IOException e) {
                log.warn("KV response failed", e);
            }
        });
    }

    private CompletableFuture<Result> route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (STATUS_PATH.equals(path)) {
            return completed("GET".equals(exchange.getRequestMethod()) ? 200 : 405);
        }
        if (!ENTITY_PATH.equals(path)) {
            return completed(404);
        }
        String key = readKey(exchange);
        final int ack = readAck(exchange);
        String method = exchange.getRequestMethod();
        if (!METHODS.contains(method)) {
            return completed(405);
        }
        byte[] body = "PUT".equals(method)
            ? exchange.getRequestBody().readNBytes(MAX_VALUE_BYTES + 1) : EMPTY_BODY;
        if (body.length > MAX_VALUE_BYTES) {
            return completed(413);
        }
        if (ReplicaCoordinator.isReplicaRequest(exchange)) {
            return CompletableFuture.completedFuture(ReplicaCoordinator.executeLocal(dao, method, key, body));
        }
        return coordinator.coordinate(method, key, body, ack);
    }

    private static String readKey(HttpExchange exchange) {
        String key = queryParameter(exchange, ID_PARAMETER);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty id");
        }
        return key;
    }

    private int readAck(HttpExchange exchange) {
        String value = queryParameter(exchange, ACK_PARAMETER);
        int ack = Integer.parseInt(Objects.requireNonNullElse(value, Integer.toString(MIN_ACK)));
        if (ack < MIN_ACK || ack > coordinator.replicationFactor()) {
            throw new IllegalArgumentException("Ack out of range");
        }
        return ack;
    }

    private static @Nullable String queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        @Nullable String value = null;
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", QUERY_PARAMETER_PARTS);
            if (name.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                if (value != null || pair.length != QUERY_PARAMETER_PARTS) {
                    throw new IllegalArgumentException("Invalid or duplicate query parameter");
                }
                value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return value;
    }

    private static CompletableFuture<Result> completed(int status) {
        return CompletableFuture.completedFuture(new Result(status, EMPTY_BODY));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
    }
}
