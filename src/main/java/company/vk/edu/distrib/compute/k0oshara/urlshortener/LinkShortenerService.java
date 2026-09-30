package company.vk.edu.distrib.compute.k0oshara.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

final class LinkShortenerService implements UrlShortenerService {
    private static final int CREATED = 0;
    private static final int RUNNING = 1;
    private static final int STOPPED = 2;
    private final Dao<String> links;
    private final Dao<String> users;
    private final int port;
    private final HttpServer server;
    private int lifecycleState;

    LinkShortenerService(int servicePort, Dao<String> linkDao, Dao<String> userDao) throws IOException {
        links = linkDao;
        users = userDao;
        port = servicePort;
        server = HttpServer.create();
        server.createContext("/", new Handler());
    }

    @Override
    public void start() {
        if (lifecycleState != CREATED) {
            throw new IllegalStateException("Service was already started or stopped");
        }
        try {
            server.bind(new InetSocketAddress("localhost", port), 0);
            server.start();
            lifecycleState = RUNNING;
        } catch (IOException | RuntimeException exception) {
            lifecycleState = STOPPED;
            server.stop(0);
            closeDaos(exception);
            throw new IllegalStateException("Unable to start service", exception);
        }
    }

    @Override
    public void stop() {
        if (lifecycleState != RUNNING) {
            throw new IllegalStateException("Service was not started or was already stopped");
        }
        lifecycleState = STOPPED;
        try {
            server.stop(0);
        } finally {
            closeDaos(null);
        }
    }

    private void closeDaos(Exception original) {
        try {
            links.close();
            users.close();
        } catch (IOException exception) {
            if (original == null) {
                throw new IllegalStateException("Unable to close DAO", exception);
            }
            original.addSuppressed(exception);
        }
    }

    private final class Handler implements HttpHandler {
        private static final String GET = "GET";
        private static final String PUT = "PUT";
        private static final String DELETE = "DELETE";
        private static final String ROOT_PATH = "/";
        private static final String GET_STATUS_ROUTE = "GET /v0/status";
        private static final String POST_USERS_ROUTE = "POST /internal/users";
        private static final String POST_LINKS_ROUTE = "POST /v0/links";
        private static final String LINKS_COLLECTION_PATH = "/v0/links";
        private static final String LINKS_PATH = "/v0/links/";
        private static final Pattern ID = Pattern.compile("[A-Za-z0-9]{10}");
        private static final String CHALLENGE = "Basic realm=\"url-shortener\", charset=\"UTF-8\"";

        @Override
        public void handle(HttpExchange exchange) {
            try (exchange) {
                try {
                    handleRequest(exchange);
                } catch (IOException | RuntimeException exception) {
                    HttpUtils.safely(exchange, 503);
                }
            }
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            switch (method + " " + path) {
                case GET_STATUS_ROUTE -> {
                    HttpUtils.find(links, "0000000000");
                    HttpUtils.find(users, "status");
                    exchange.sendResponseHeaders(200, -1);
                }
                case POST_USERS_ROUTE -> createUser(exchange);
                default -> {
                    if (GET.equals(method) && path.lastIndexOf(ROOT_PATH) == 0) {
                        redirect(exchange, path.substring(1));
                    } else if (authenticate(exchange)) {
                        routeLinks(exchange, method, path);
                    }
                }
            }
        }

        private void createUser(HttpExchange exchange) throws IOException {
            HttpUtils.Credentials credentials = HttpUtils.credentials(HttpUtils.read(exchange));
            if (credentials == null) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            users.upsert(credentials.username(), credentials.password());
            exchange.sendResponseHeaders(200, -1);
        }

        private boolean authenticate(HttpExchange exchange) throws IOException {
            HttpUtils.Credentials credentials = HttpUtils.authorization(
                exchange.getRequestHeaders().getFirst("Authorization"));
            if (credentials != null && credentials.password().equals(
                HttpUtils.find(users, credentials.username()))) {
                return true;
            }
            exchange.getResponseHeaders().set("WWW-Authenticate", CHALLENGE);
            exchange.sendResponseHeaders(401, -1);
            return false;
        }

        private void redirect(HttpExchange exchange, String id) throws IOException {
            if (!ID.matcher(Objects.toString(id, "")).matches()) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            String value = HttpUtils.find(links, id);
            if (value == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.getResponseHeaders().set("Location", URI.create(value).toASCIIString());
                exchange.sendResponseHeaders(301, -1);
            }
        }

        private void routeLinks(HttpExchange exchange, String method, String path) throws IOException {
            String route = method + " " + path;
            if (POST_LINKS_ROUTE.equals(route)) {
                create(exchange);
                return;
            }
            if (LINKS_COLLECTION_PATH.equals(path)) {
                methodNotAllowed(exchange, "POST");
                return;
            }
            if (!path.startsWith(LINKS_PATH)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String id = path.substring(LINKS_PATH.length());
            if (!ID.matcher(Objects.toString(id, "")).matches()) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            switch (method) {
                case GET -> get(exchange, id);
                case PUT -> update(exchange, id);
                case DELETE -> {
                    links.delete(id);
                    exchange.sendResponseHeaders(202, -1);
                }
                default -> methodNotAllowed(exchange, "GET, PUT, DELETE");
            }
        }

        private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
            exchange.getResponseHeaders().set("Allow", allowed);
            exchange.sendResponseHeaders(405, -1);
        }

        private void create(HttpExchange exchange) throws IOException {
            String value = HttpUtils.read(exchange);
            if (!HttpUtils.isValidUrl(value)) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            String id;
            do {
                id = HttpUtils.randomId();
            } while (HttpUtils.find(links, id) != null);
            links.upsert(id, value);
            HttpUtils.text(exchange, 201, "http://localhost:" + port + "/" + id);
        }

        private void get(HttpExchange exchange, String id) throws IOException {
            String value = HttpUtils.find(links, id);
            if (value == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                HttpUtils.text(exchange, 200, value);
            }
        }

        private void update(HttpExchange exchange, String id) throws IOException {
            String value = HttpUtils.read(exchange);
            if (!HttpUtils.isValidUrl(value)) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            if (HttpUtils.find(links, id) == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                links.upsert(id, value);
                exchange.sendResponseHeaders(200, -1);
            }
        }

    }

    static final class HttpUtils {
        private static final String CONTENT_TYPE = "text/html; charset=utf-8";
        private static final Pattern HTTP_SCHEME = Pattern.compile("https?", Pattern.CASE_INSENSITIVE);
        private static final String ID_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        private static final SecureRandom RANDOM = new SecureRandom();

        private HttpUtils() {
        }

        static String find(Dao<String> dao, String key) throws IOException {
            try {
                return dao.get(key);
            } catch (NoSuchElementException exception) {
                return null;
            }
        }

        static boolean isValidUrl(String value) {
            try {
                URI uri = new URI(Objects.toString(value, ""));
                return HTTP_SCHEME.matcher(Objects.toString(uri.getScheme(), "")).matches()
                    && uri.getHost() != null && uri.getPort() <= 65535;
            } catch (URISyntaxException exception) {
                return false;
            }
        }

        static String randomId() {
            StringBuilder result = new StringBuilder(10);
            for (int index = 0; index < 10; index++) {
                result.append(ID_CHARACTERS.charAt(RANDOM.nextInt(ID_CHARACTERS.length())));
            }
            return result.toString();
        }

        static String read(HttpExchange exchange) throws IOException {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }

        static Credentials credentials(String value) {
            int colon = value.indexOf(':');
            return colon > 0 && value.chars().noneMatch(Character::isISOControl)
                ? new Credentials(value.substring(0, colon), value.substring(colon + 1)) : null;
        }

        static Credentials authorization(String header) {
            String value = Objects.toString(header, "");
            if (!value.regionMatches(true, 0, "Basic ", 0, 6)) {
                return null;
            }
            try {
                return credentials(new String(Base64.getDecoder().decode(value.substring(6).trim()),
                    StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        record Credentials(String username, String password) {
        }

        static void text(HttpExchange exchange, int status, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        static void safely(HttpExchange exchange, int status) {
            try {
                exchange.sendResponseHeaders(status, -1);
            } catch (IOException | RuntimeException expected) {
            }
        }
    }
}
