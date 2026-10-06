package company.vk.edu.distrib.compute.vladimir_rusaleev.urlshortener;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.NoSuchElementException;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;

final class UrlLinks {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int ID_LENGTH = 10;
    private static final int MAX_BODY_BYTES = 65536;

    private final Dao<String> links;
    private final SecureRandom random = new SecureRandom();
    private final int port;

    UrlLinks(Dao<String> links, int port) {
        this.links = links;
        this.port = port;
    }

    void createLink(HttpExchange exchange) throws IOException {
        String longLink = readBody(exchange);
        if (!validUrl(longLink)) {
            respond(exchange, 422, null);
            return;
        }
        String id = newId();
        boolean unique = false;
        while (!unique) {
            try {
                links.get(id);
                id = newId();
            } catch (NoSuchElementException exception) {
                unique = true;
            }
        }
        links.upsert(id, longLink);
        respond(exchange, 201, "http://localhost:" + port + "/" + id);
    }

    void linkById(HttpExchange exchange, String id) throws IOException {
        if (!validId(id)) {
            respond(exchange, 422, null);
            return;
        }
        switch (exchange.getRequestMethod()) {
            case "GET" -> get(exchange, id);
            case "PUT" -> update(exchange, id);
            case "DELETE" -> delete(exchange, id);
            default -> respond(exchange, 405, null);
        }
    }

    private void get(HttpExchange exchange, String id) throws IOException {
        try {
            respond(exchange, 200, links.get(id));
        } catch (NoSuchElementException exception) {
            respond(exchange, 404, null);
        }
    }

    private void update(HttpExchange exchange, String id) throws IOException {
        String longLink = readBody(exchange);
        if (!validUrl(longLink)) {
            respond(exchange, 422, null);
            return;
        }
        try {
            links.get(id);
            links.upsert(id, longLink);
            respond(exchange, 200, null);
        } catch (NoSuchElementException exception) {
            respond(exchange, 404, null);
        }
    }

    private void delete(HttpExchange exchange, String id) throws IOException {
        links.delete(id);
        respond(exchange, 202, null);
    }

    void redirect(HttpExchange exchange, String id) throws IOException {
        if (!validId(id)) {
            respond(exchange, 422, null);
            return;
        }
        try {
            exchange.getResponseHeaders().set("Location", links.get(id));
            respond(exchange, 301, null);
        } catch (NoSuchElementException exception) {
            respond(exchange, 404, null);
        }
    }

    private String newId() {
        StringBuilder result = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    private static boolean validId(String id) {
        if (id.length() != ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (ALPHABET.indexOf(id.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
