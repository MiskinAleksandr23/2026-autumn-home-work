package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import org.jspecify.annotations.Nullable;

final class ReplicaCoordinator implements AutoCloseable {
    static final byte[] EMPTY_BODY = new byte[0];

    private static final Result UNAVAILABLE = new Result(HttpURLConnection.HTTP_UNAVAILABLE, EMPTY_BODY);
    private static final String REPLICA_HEADER = "X-Near-Replica";
    private static final String REPLICA_REQUEST = "true";
    private static final int MIN_REPLICAS = 1;
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(250);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(750);

    private final int port;
    private final Dao<byte[]> dao;
    private final RendezvousHash routing;
    private final int replicas;
    private final boolean discoverCluster;
    private final HttpClient client;

    ReplicaCoordinator(int port, Dao<byte[]> dao) {
        this(port, dao, new int[]{port}, MIN_REPLICAS, true);
    }

    ReplicaCoordinator(int port, Dao<byte[]> dao, int[] clusterPorts, int replicas) {
        this(port, dao, clusterPorts, replicas, false);
    }

    private ReplicaCoordinator(int port, Dao<byte[]> dao, int[] clusterPorts, int replicas, boolean discoverCluster) {
        routing = new RendezvousHash(clusterPorts);
        if (!routing.contains(port)) {
            throw new IllegalArgumentException("Coordinator is not in the cluster");
        }
        if (replicas < MIN_REPLICAS || replicas > routing.size()) {
            throw new IllegalArgumentException("Replication factor out of range");
        }
        this.port = port;
        this.dao = dao;
        this.replicas = replicas;
        this.discoverCluster = discoverCluster;
        client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1).build();
    }

    int replicationFactor() {
        return discoverCluster ? clusterRouting().size() : replicas;
    }

    private RendezvousHash clusterRouting() {
        if (!discoverCluster) {
            return routing;
        }
        String setting = System.getProperty("CLUSTER");
        if (setting == null || setting.isBlank()) {
            return routing;
        }
        int[] ports = Arrays.stream(setting.split(",")).map(String::strip).mapToInt(Integer::parseInt).toArray();
        RendezvousHash cluster = new RendezvousHash(ports);
        if (!cluster.contains(port)) {
            throw new IllegalArgumentException("Coordinator is not in the cluster");
        }
        return cluster;
    }

    static boolean isReplicaRequest(HttpExchange exchange) {
        return REPLICA_REQUEST.equals(exchange.getRequestHeaders().getFirst(REPLICA_HEADER));
    }

    CompletableFuture<Result> coordinate(String method, String key, byte[] body, int ack) {
        RendezvousHash cluster = clusterRouting();
        int count = discoverCluster ? cluster.size() : replicas;
        Quorum quorum = new Quorum(count, ack, "GET".equals(method), successStatus(method));
        for (int replica : cluster.replicas(key, count)) {
            if (replica == port) {
                quorum.accept(executeLocal(dao, method, key, body));
            } else {
                send(replica, method, key, body).thenAccept(quorum::accept);
            }
        }
        return quorum.result;
    }

    static Result executeLocal(Dao<byte[]> dao, String method, String key, byte[] body) {
        try {
            return switch (method) {
                case "GET" -> new Result(HttpURLConnection.HTTP_OK, dao.get(key));
                case "PUT" -> {
                    dao.upsert(key, body);
                    yield new Result(HttpURLConnection.HTTP_CREATED, EMPTY_BODY);
                }
                case "DELETE" -> {
                    dao.delete(key);
                    yield new Result(HttpURLConnection.HTTP_ACCEPTED, EMPTY_BODY);
                }
                default -> new Result(HttpURLConnection.HTTP_BAD_METHOD, EMPTY_BODY);
            };
        } catch (NoSuchElementException e) {
            return new Result(HttpURLConnection.HTTP_NOT_FOUND, EMPTY_BODY);
        } catch (IOException e) {
            return UNAVAILABLE;
        }
    }

    private CompletableFuture<Result> send(int replica, String method, String key, byte[] body) {
        URI uri = URI.create("http://localhost:" + replica + "/v0/entity?id="
            + URLEncoder.encode(key, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
            .header(REPLICA_HEADER, REPLICA_REQUEST)
            .method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
        CompletableFuture<HttpResponse<byte[]>> pending = client
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        CompletableFuture<HttpResponse<byte[]>> timed = pending.copy()
            .orTimeout(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        timed.whenComplete((response, error) -> {
            if (error != null) {
                pending.cancel(true);
            }
        });
        return timed.handle((response, error) -> error == null
            ? new Result(response.statusCode(), response.body()) : UNAVAILABLE);
    }

    private static int successStatus(String method) {
        return switch (method) {
            case "GET" -> HttpURLConnection.HTTP_OK;
            case "PUT" -> HttpURLConnection.HTTP_CREATED;
            case "DELETE" -> HttpURLConnection.HTTP_ACCEPTED;
            default -> throw new IllegalArgumentException("Unsupported replica method");
        };
    }

    @Override
    public void close() {
        client.close();
    }

    record Result(int status, byte[] body) {
    }

    private static final class Quorum {
        private final int replicas;
        private final int ack;
        private final boolean read;
        private final int successStatus;
        private final CompletableFuture<Result> result = new CompletableFuture<>();
        private final Lock lock = new ReentrantLock();
        private Result successfulReply;
        private int confirmed;
        private int failed;
        private boolean resolved;

        Quorum(int replicas, int ack, boolean read, int successStatus) {
            this.replicas = replicas;
            this.ack = ack;
            this.read = read;
            this.successStatus = successStatus;
            successfulReply = new Result(read ? HttpURLConnection.HTTP_NOT_FOUND : successStatus, EMPTY_BODY);
        }

        void accept(Result reply) {
            @Nullable Result outcome;
            lock.lock();
            try {
                if (resolved) {
                    return;
                }
                if (isConfirmation(reply)) {
                    confirmed++;
                    if (reply.status() == HttpURLConnection.HTTP_OK) {
                        successfulReply = reply;
                    }
                } else {
                    failed++;
                }
                outcome = quorumOutcome();
                resolved = outcome != null;
            } finally {
                lock.unlock();
            }
            if (outcome != null) {
                result.complete(outcome);
            }
        }

        private boolean isConfirmation(Result reply) {
            return reply.status() == successStatus || read && reply.status() == HttpURLConnection.HTTP_NOT_FOUND;
        }

        private @Nullable Result quorumOutcome() {
            if (confirmed >= ack) {
                return successfulReply;
            }
            if (replicas - failed < ack) {
                return UNAVAILABLE;
            }
            return null;
        }
    }
}
