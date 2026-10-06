package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;

public class DDKudrinLinksHandler implements HttpHandler {
    private static final String LINKS_PATH = "/v0/links";
    private static final String CONTENT_TYPE = "text/html; charset=utf-8";
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 10;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9]{10}");

    private final SecureRandom random = new SecureRandom();
    private final Lock lock = new ReentrantLock();
    private final Dao<String> dao;

    public DDKudrinLinksHandler(Dao<String> dao) {
        this.dao = dao;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            lock.lock();
            try {
                route(exchange);
            } catch (IOException e) {
                if (exchange.getResponseCode() != -1) {
                    throw e;
                }
                exchange.sendResponseHeaders(500, -1);
            } finally {
                lock.unlock();
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (Objects.equals(path, LINKS_PATH)) {
            if (Objects.equals(exchange.getRequestMethod(), "POST")) {
                handlePost(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            return;
        }

        if (!path.startsWith(LINKS_PATH + "/")) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        String id = path.substring(LINKS_PATH.length() + 1);
        if (!ID_PATTERN.matcher(id).matches()) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }

        if (Objects.equals(exchange.getRequestMethod(), "GET")) {
            handleGet(exchange, id);
        } else if (Objects.equals(exchange.getRequestMethod(), "PUT")) {
            handlePut(exchange, id);
        } else if (Objects.equals(exchange.getRequestMethod(), "DELETE")) {
            handleDelete(exchange, id);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleGet(HttpExchange exchange, String id) throws IOException {
        String url = findLink(id);
        if (url == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        sendText(exchange, 200, url);
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String url = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!isValidUrl(url)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }

        String id;
        do {
            id = generateId();
        } while (findLink(id) != null);

        dao.upsert(id, url);
        String shortUrl = "http://localhost:" + exchange.getLocalAddress().getPort() + "/" + id;
        sendText(exchange, 201, shortUrl);
    }

    private void handlePut(HttpExchange exchange, String id) throws IOException {
        String url = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!isValidUrl(url)) {
            exchange.sendResponseHeaders(422, -1);
            return;
        }
        if (findLink(id) == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        dao.upsert(id, url);
        exchange.sendResponseHeaders(200, -1);
    }

    private void handleDelete(HttpExchange exchange, String id) throws IOException {
        dao.delete(id);
        exchange.sendResponseHeaders(202, -1);
    }

    private String findLink(String id) throws IOException {
        try {
            return dao.get(id);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private static boolean isValidUrl(String url) {
        try {
            URI uri = new URI(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getPort() <= 65535;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String generateId() {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
