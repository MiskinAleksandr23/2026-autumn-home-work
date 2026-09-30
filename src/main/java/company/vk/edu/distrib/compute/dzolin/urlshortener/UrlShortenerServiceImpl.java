package company.vk.edu.distrib.compute.dzolin.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class UrlShortenerServiceImpl implements UrlShortenerService {
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";
    public static final String INVALID_CREDENTIALS = "invalid credentials";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://[a-zA-Z0-9.-]+(?::[0-9]{1,5})?(?:[/?#][^\\s]*)?$"
    );
    private final HttpServer server;
    private final StringDao linkDao;
    private final StringDao userDao;
    private final int port;

    UrlShortenerServiceImpl(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        linkDao = new StringDao("/tmp/links.txt");
        userDao = new StringDao("/tmp/users.txt");
        this.port = port;

        server.createContext("/v0/status", new ErrorHandler(this::getStatus));
        server.createContext("/v0/links", new ErrorHandler(this::handleLink));
        server.createContext("/", new ErrorHandler(this::redirect));
        server.createContext("/internal/users", new ErrorHandler(this::createUser));
    }

    public void getStatus(HttpExchange exchange) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.sendResponseHeaders(200, -1);
    }

    public void handleLink(HttpExchange exchange) throws IOException {
        var method = exchange.getRequestMethod();
        if (!Set.of(GET, POST, PUT, DELETE).contains(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        checkAuth(exchange);
        switch (method) {
            case GET -> getLink(exchange);
            case POST -> createLink(exchange);
            case PUT -> updateLink(exchange);
            default -> deleteLink(exchange);
        }
    }

    public void createLink(HttpExchange exchange) throws IOException {
        var longLink = getLongLinkFromBody(exchange);
        var linkId = getShortLinkId();

        linkDao.upsert(linkId, longLink);

        var shortLink = getShortLinkPath(linkId);
        sendResult(exchange, 201, shortLink);
    }

    public void updateLink(HttpExchange exchange) throws IOException {
        var longLink = getLongLinkFromBody(exchange);
        var linkId = getLinkIdFromPath(exchange);

        linkDao.get(linkId);
        linkDao.upsert(linkId, longLink);

        sendResult(exchange, 200, "");
    }

    public void deleteLink(HttpExchange exchange) throws IOException {
        var linkId = getLinkIdFromPath(exchange);
        linkDao.delete(linkId);
        sendResult(exchange, 202, "");
    }

    public void getLink(HttpExchange exchange) throws IOException {
        var linkId = getLinkIdFromPath(exchange);
        var longLink = linkDao.get(linkId);
        sendResult(exchange, 200, longLink);
    }

    public void redirect(HttpExchange exchange) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        var linkId = getLinkIdFromPath(exchange);
        var longLink = linkDao.get(linkId);

        exchange.getResponseHeaders().set("Location", longLink);
        exchange.sendResponseHeaders(301, -1);
    }

    private void createUser(HttpExchange exchange) throws IOException {
        var credentialsStr = new String(exchange.getRequestBody().readAllBytes());
        var credentials = parseCredentials(credentialsStr);
        userDao.upsert(credentials.username, credentials.password);
        exchange.sendResponseHeaders(200, -1);
    }

    private void sendResult(HttpExchange exchange, int statusCode, String data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, data.length());
        exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
    }

    private void validateLongLink(String longLink) {
        if (!URL_PATTERN.matcher(longLink).matches()) {
            throw new IllegalArgumentException("long link is invalid");
        }
    }

    private String getLinkIdFromPath(HttpExchange exchange) {
        var registeredPath = exchange.getHttpContext().getPath();
        var requestPath = exchange.getRequestURI().getPath();
        var prefix = registeredPath.endsWith("/") ? registeredPath : registeredPath + "/";
        if (!requestPath.startsWith(prefix)) {
            throw new IllegalArgumentException("link id is missing");
        }
        var linkId = requestPath.substring(prefix.length());
        validateLinkId(linkId);
        return linkId;
    }

    private String getLongLinkFromBody(HttpExchange exchange) throws IOException {
        var longLink = new String(exchange.getRequestBody().readAllBytes());
        validateLongLink(longLink);
        return longLink;
    }

    private String getShortLinkPath(String id) {
        return "http://localhost:" + port + "/" + id;
    }

    private void validateLinkId(String linkId) {
        if (!linkId.matches("[a-zA-Z0-9]{10}")) {
            throw new IllegalArgumentException("link id is invalid");
        }
    }

    private String getShortLinkId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Credentials parseCredentials(String credentialsStr) {
        int separator = credentialsStr.indexOf(':');
        if (separator <= 0) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return new Credentials(credentialsStr.substring(0, separator), credentialsStr.substring(separator + 1));
    }

    private void checkAuth(HttpExchange exchange) throws IOException {
        var authorization = exchange.getRequestHeaders().get("Authorization").getFirst();
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        var encodedCredentials = authorization.substring(6);
        String decodedCredentials;
        try {
            decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException(INVALID_CREDENTIALS, exception);
        }
        var credentials = parseCredentials(decodedCredentials);

        String actualPassword;
        try {
            actualPassword = userDao.get(credentials.username);
        } catch (NoSuchElementException exception) {
            throw new UnauthorizedException(INVALID_CREDENTIALS, exception);
        }
        if (!credentials.password.equals(actualPassword)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        try (linkDao; userDao) {
            server.stop(1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public record Credentials(String username, String password){}
}
