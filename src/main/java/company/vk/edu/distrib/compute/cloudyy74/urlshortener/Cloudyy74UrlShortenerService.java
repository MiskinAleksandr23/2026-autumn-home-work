package company.vk.edu.distrib.compute.cloudyy74.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.NoSuchElementException;

public class Cloudyy74UrlShortenerService implements UrlShortenerService {
    private static final Logger log = LoggerFactory.getLogger(Cloudyy74UrlShortenerService.class);

    private final int port;
    private final HttpServer server;
    private final Dao<String> linksDao = new Cloudyy74PersistentDao("./out/links.log");
    private final Dao<String> usersDao = new Cloudyy74PersistentDao("./out/users.log");

    private static final int SHORT_LINK_ID_LENGTH = 10;
    private static final String SHORT_LINK_ID_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String CONTENT_TYPE_TEXT = "text/html; charset=utf-8";
    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String BASIC_AUTH_SCHEME = "Basic";

    public Cloudyy74UrlShortenerService(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/v0/status", new ErrorHandler(this::handleStatus));
        server.createContext("/v0/links", new ErrorHandler(new BasicAuthHandler(this::handleNewLink, usersDao)));
        server.createContext("/v0/links/", new ErrorHandler(new BasicAuthHandler(this::handleLinks, usersDao)));
        server.createContext("/", new ErrorHandler(this::handleRedirect));
        server.createContext("/internal/users", new ErrorHandler(this::handleNewUser));
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.stop(1);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!GET_METHOD.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.sendResponseHeaders(200, -1);
    }

    private void handleNewLink(HttpExchange exchange) throws IOException {
        if (!POST_METHOD.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        final var longLink = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        validateLink(longLink);

        final var id = generateShortLinkId();
        linksDao.upsert(id, longLink);

        final var shortLink = "http://localhost:%d/%s".formatted(port, id);
        sendText(exchange, 201, shortLink);
    }

    private void handleLinks(HttpExchange exchange) throws IOException {
        final var path = exchange.getRequestURI().getPath();
        final var id = path.substring("/v0/links/".length());
        validateId(id);

        switch (exchange.getRequestMethod()) {
            case GET_METHOD -> getLink(exchange, id);
            case "PUT" -> updateLink(exchange, id);
            case "DELETE" -> deleteLink(exchange, id);
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }

    private void getLink(HttpExchange exchange, String id) throws IOException {
        final var longLink = linksDao.get(id);
        sendText(exchange, 200, longLink);
    }

    private void updateLink(HttpExchange exchange, String id) throws IOException {
        final var newLink = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        validateLink(newLink);

        // check that the record exists
        linksDao.get(id);

        linksDao.upsert(id, newLink);
        exchange.sendResponseHeaders(200, -1);
    }

    private void deleteLink(HttpExchange exchange, String id) throws IOException {
        linksDao.delete(id);
        exchange.sendResponseHeaders(202, -1);
    }

    private void handleRedirect(HttpExchange exchange) throws IOException {
        if (!GET_METHOD.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        final var path = exchange.getRequestURI().getPath();
        final var id = path.substring(1);
        validateId(id);

        final var longLink = linksDao.get(id);

        exchange.getResponseHeaders().set("Location", longLink);
        exchange.sendResponseHeaders(301, -1);
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", CONTENT_TYPE_TEXT);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
    }

    private static String generateShortLinkId() {
        StringBuilder sb = new StringBuilder(SHORT_LINK_ID_LENGTH);

        for (int i = 0; i < SHORT_LINK_ID_LENGTH; i++) {
            int randomIndex = RANDOM.nextInt(SHORT_LINK_ID_ALPHABET.length());
            sb.append(SHORT_LINK_ID_ALPHABET.charAt(randomIndex));
        }

        return sb.toString();
    }

    private static void validateId(String id) {
        if (!id.matches("[A-Za-z0-9]{10}")) {
            throw new IllegalArgumentException("Invalid ID");
        }
    }

    private static void validateLink(String link) {
        try {
            URI uri = new URI(link);
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid link");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid link", e);
        }
    }

    private record ErrorHandler(HttpHandler delegate) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                try {
                    delegate.handle(exchange);
                } catch (NoSuchElementException e) {
                    sendText(exchange, 404, "Not Found");
                } catch (IllegalArgumentException e) {
                    sendText(exchange, 422, "Unprocessable Content");
                } catch (IOException e) {
                    log.error("I/O error while processing HTTP request", e);
                    throw e;
                } catch (Exception e) {
                    log.error("Unexpected error while processing HTTP request", e);
                    sendText(exchange, 500, "Internal Server Error");
                }
            }
        }
    }

    private void handleNewUser(HttpExchange exchange) throws IOException {
        if (!POST_METHOD.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        final var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        final var credentials = parseCredentials(body);
        usersDao.upsert(credentials.username(), credentials.password());

        exchange.sendResponseHeaders(200, -1);
    }

    private record Credentials(String username, String password) {
    }

    private static Credentials parseCredentials(String value) {
        final var sep = value.indexOf(':');

        if (sep <= 0 || sep == value.length() - 1) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        final var username = value.substring(0, sep);
        final var password = value.substring(sep + 1);

        return new Credentials(username, password);
    }

    private static Credentials parseAuthorization(String authorization) {
        final var sep = authorization.indexOf(' ');

        if (sep <= 0 || sep == authorization.length() - 1) {
            throw new IllegalArgumentException("Invalid Authorization header");
        }

        final var scheme = authorization.substring(0, sep);
        if (!BASIC_AUTH_SCHEME.equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Expected Basic Authorization scheme");
        }

        final var encodedCredentials = authorization.substring(sep + 1).trim();

        final var decoded = Base64.getDecoder().decode(encodedCredentials);
        final var credentials = new String(decoded, StandardCharsets.UTF_8);

        return parseCredentials(credentials);
    }

    private record BasicAuthHandler(HttpHandler delegate, Dao<String> usersDao) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                final var authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization == null) {
                    sendUnauthorized(exchange);
                    return;
                }

                final var credentials = parseAuthorization(authorization);
                final var expectedPassword = usersDao.get(credentials.username());
                if (!expectedPassword.equals(credentials.password())) {
                    sendUnauthorized(exchange);
                    return;
                }
            } catch (IllegalArgumentException | NoSuchElementException e) {
                sendUnauthorized(exchange);
                return;
            }
            delegate.handle(exchange);
        }
    }

    private static void sendUnauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"url-shortener\", charset=\"UTF-8\"");
        exchange.sendResponseHeaders(401, -1);
    }
}
