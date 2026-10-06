package company.vk.edu.distrib.compute.masha533.urlshortener;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

public class UrlShortenerServiceImpl implements UrlShortenerService {
    private final HttpServer server;
    private final int port;
    private final Dao<String> linksDao;
    private final Dao<String> usersDao;
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String ID_PATTERN = "[A-Za-z0-9]{10}";
    private static final String LINKS_PATH_PREFIX = "/v0/links/";
    private static final String LINKS_PATH = "/v0/links";
    private static final int PARTS_COUNT = 2;
    private static final String POST_METHOD = "POST";
    private static final String GET_METHOD = "GET";
    private static final String PUT_METHOD = "PUT";
    private static final String DELETE_METHOD = "DELETE";

    private boolean isValidLink(String link) {
        try {
            URI uri = new URI(link);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }
            return "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        try {
            var authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, "Basic ".length())) {
                return false;
            }
            var encoded = authorization.substring("Basic ".length());
            var decoded = Base64.getDecoder().decode(encoded);
            var body = new String(decoded, StandardCharsets.UTF_8);
            var parts = body.split(":", PARTS_COUNT);
            if (parts.length != PARTS_COUNT) {
                return false;
            }
            final var savedPassword = usersDao.get(parts[0]);
            return savedPassword.equals(parts[1]);
        } catch (NoSuchElementException | IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        final var body = new String(exchange.getRequestBody().readAllBytes());
        if (!isValidLink(body)) {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
            return;
        }
        final var id = getShortLinkId();
        linksDao.upsert(id, body);
        final var shortLink = "http://localhost:" + port + "/" + id;
        final var response = shortLink.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");

        exchange.sendResponseHeaders(201, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        final var path = exchange.getRequestURI().getPath();
        final var id = path.substring(LINKS_PATH_PREFIX.length());

        if (!id.matches(ID_PATTERN)) {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
            return;
        }
        try {
            var link = linksDao.get(id);
            final var response = link.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");

            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        } catch (NoSuchElementException e) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        final var path = exchange.getRequestURI().getPath();
        final var id = path.substring(LINKS_PATH_PREFIX.length());
        if (!id.matches(ID_PATTERN)) {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
            return;
        }
        final var body = new String(exchange.getRequestBody().readAllBytes());
        if (!isValidLink(body)) {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
            return;
        }
        try {
            linksDao.get(id);
            linksDao.upsert(id, body);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        } catch (NoSuchElementException e) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        final var path = exchange.getRequestURI().getPath();
        final var id = path.substring(LINKS_PATH_PREFIX.length());
        if (!id.matches(ID_PATTERN)) {
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
            return;
        }
        linksDao.delete(id);
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private void handleLinks(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"url-shortener\"");
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }

        final var method = exchange.getRequestMethod();
        final var path = exchange.getRequestURI().getPath();
        if (POST_METHOD.equals(method)) {
            if (LINKS_PATH.equals(path)) {
                handleCreate(exchange);
            }
        } else if (path.startsWith(LINKS_PATH_PREFIX)) {
            if (GET_METHOD.equals(method)) {
                handleGet(exchange);
            } else if (PUT_METHOD.equals(method)) {
                handleUpdate(exchange);
            } else if (DELETE_METHOD.equals(method)) {
                handleDelete(exchange);
            }
        }
    }

    private void handleRedirect(HttpExchange exchange) throws IOException {
        final var method = exchange.getRequestMethod();
        if (GET_METHOD.equals(method)) {
            final var path = exchange.getRequestURI().getPath();
            final var id = path.substring("/".length());
            if (!id.matches(ID_PATTERN)) {
                exchange.sendResponseHeaders(422, -1);
                exchange.close();
                return;
            }
            try {
                var link = linksDao.get(id);
                exchange.getResponseHeaders().set("Location", link);
                exchange.sendResponseHeaders(301, -1);
                exchange.close();
            } catch (NoSuchElementException e) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }

        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    private void handleUsers(HttpExchange exchange) throws IOException {
        final var method = exchange.getRequestMethod();
        if (POST_METHOD.equals(method)) {
            final var body = new String(exchange.getRequestBody().readAllBytes());
            var parts = body.split(":", PARTS_COUNT);
            if (parts.length == PARTS_COUNT) {
                usersDao.upsert(parts[0],parts[1]);
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } else {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
            }
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }

    }

    public UrlShortenerServiceImpl(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.linksDao = new PersistentDao(Path.of(System.getProperty("java.io.tmpdir"), "masha533-links.properties"));
        this.usersDao = new PersistentDao(Path.of(System.getProperty("java.io.tmpdir"), "masha533-users.properties"));
        server.createContext("/v0/status", this::handleStatus);
        server.createContext(LINKS_PATH, this::handleLinks);
        server.createContext("/", this::handleRedirect);
        server.createContext("/internal/users", this::handleUsers);
    }

    private String getShortLinkId() {
        StringBuilder id = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            int index = ThreadLocalRandom.current().nextInt(ID_CHARS.length());
            id.append(ID_CHARS.charAt(index));
        }
        return id.toString();
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.stop(5);
    }
}
