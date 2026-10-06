package company.vk.edu.distrib.compute.ruavee.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

public class UrlShortenerHandler implements HttpHandler {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int ID_LENGTH = 10;

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";

    private static final String STATUS_PATH = "/v0/status";
    private static final String LINKS_PATH = "/v0/links";
    private static final String LINKS_PREFIX = "/v0/links/";
    private static final String USERS_PATH = "/internal/users";
    private static final String ROOT_PATH = "/";

    private final SecureRandom random = new SecureRandom();
    private final Dao<String> dao;
    private final BasicAuth auth;
    private final int port;
    private final ReentrantLock lock = new ReentrantLock();

    public UrlShortenerHandler(Dao<String> dao, BasicAuth auth, int port) {
        this.dao = dao;
        this.auth = auth;
        this.port = port;
    }

    private boolean isValidUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
                return true;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return false;
    }

    private boolean isValidId(String id) {
        if (id.length() == ID_LENGTH) {
            for (int i = 0; i < ID_LENGTH; i++) {
                if (ALPHABET.indexOf(id.charAt(i)) == -1) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private String randomId() throws IOException {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        boolean exists = true;
        do {
            id.setLength(0);
            for (int i = 0; i < ID_LENGTH; i++) {
                id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            try {
                dao.get(id.toString());
            } catch (NoSuchElementException e) {
                exists = false;
            }
        } while (exists);
        return id.toString();
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!isValidUrl(body)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        String id;
        lock.lock();
        try {
            id = randomId();
            dao.upsert(id, body);
        } finally {
            lock.unlock();
        }
        byte[] response = ("http://localhost:" + port + "/" + id).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(201, response.length);
        exchange.getResponseBody().write(response);
    }

    private void handleGet(HttpExchange exchange, String id) throws IOException {
        if (!isValidId(id)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        try {
            String url = dao.get(id);
            byte[] response = url.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (NoSuchElementException e) {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleUpdate(HttpExchange exchange, String id) throws IOException {
        if (!isValidId(id)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!isValidUrl(body)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        boolean found;
        lock.lock();
        try {
            dao.get(id);
            dao.upsert(id, body);
            found = true;
        } catch (NoSuchElementException e) {
            found = false;
        } finally {
            lock.unlock();
        }
        if (found) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleDelete(HttpExchange exchange, String id) throws IOException {
        if (!isValidId(id)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        lock.lock();
        try {
            dao.delete(id);
        } finally {
            lock.unlock();
        }
        exchange.sendResponseHeaders(202, -1);
    }

    private void handleRedirect(HttpExchange exchange, String id) throws IOException {
        if (!isValidId(id)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        try {
            String url = dao.get(id);
            exchange.getResponseHeaders().add("Location", url);
            exchange.sendResponseHeaders(301, -1);
        } catch (NoSuchElementException e) {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleLinks(HttpExchange exchange, String method, String path) throws IOException {
        if (!auth.checkAuthorization(exchange)) {
            return;
        }

        if (METHOD_POST.equals(method) && LINKS_PATH.equals(path)) {
            handleCreate(exchange);
            return;
        }

        if (!path.startsWith(LINKS_PREFIX)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        String id = path.substring(LINKS_PREFIX.length());

        switch (method) {
            case METHOD_GET -> handleGet(exchange, id);
            case METHOD_PUT -> handleUpdate(exchange, id);
            case METHOD_DELETE -> handleDelete(exchange, id);
            default -> exchange.sendResponseHeaders(404, -1);
        }
    }

    private boolean isLinksPath(String path) {
        return LINKS_PATH.equals(path) || path.startsWith(LINKS_PREFIX);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (METHOD_GET.equals(method) && STATUS_PATH.equals(path)) {
            exchange.sendResponseHeaders(200, -1);
        } else if (isLinksPath(path)) {
            handleLinks(exchange, method, path);
        } else if (METHOD_POST.equals(method) && USERS_PATH.equals(path)) {
            auth.handleCreateUser(exchange);
        } else if (METHOD_GET.equals(method) && path.startsWith(ROOT_PATH)) {
            String id = path.substring(ROOT_PATH.length());
            handleRedirect(exchange, id);
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
        exchange.close();
    }
}
