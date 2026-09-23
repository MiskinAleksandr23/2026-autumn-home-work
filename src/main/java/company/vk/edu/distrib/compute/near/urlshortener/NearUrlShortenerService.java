package company.vk.edu.distrib.compute.near.urlshortener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NearUrlShortenerService implements UrlShortenerService {
    private static final Logger log = LoggerFactory.getLogger(NearUrlShortenerService.class);
    private static final String LINKS_PATH = "/v0/links";
    private static final String STATUS_PATH = "/v0/status";
    private static final String USERS_PATH = "/internal/users";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "text/html; charset=utf-8";
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 10;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9]{10}");

    private final int port;
    private final HttpServer server;
    private final Dao<String> links;
    private final Dao<String> users;
    private final BasicAuthentication authentication;
    private final SecureRandom random = new SecureRandom();

    public NearUrlShortenerService(int port, Dao<String> links, Dao<String> users) throws IOException {
        this.port = port;
        this.links = links;
        this.users = users;
        authentication = new BasicAuthentication(users);
        server = HttpServer.create();
        server.createContext("/", this::handle);
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
        server.stop(0);
        try {
            links.close();
            users.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                route(exchange);
            } catch (NoSuchElementException e) {
                respond(exchange, 404, "");
            } catch (IllegalArgumentException e) {
                respond(exchange, 422, "");
            } catch (IOException e) {
                log.warn("Request failed", e);
                respond(exchange, 503, "");
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (STATUS_PATH.equals(path)) {
            respond(exchange, GET.equals(method) ? 200 : 405, "");
            return;
        }
        if (USERS_PATH.equals(path)) {
            createUser(exchange);
            return;
        }
        if (GET.equals(method) && path.indexOf('/', 1) < 0) {
            String id = path.substring(1);
            validateId(id);
            exchange.getResponseHeaders().set("Location", URI.create(links.get(id)).toASCIIString());
            respond(exchange, 301, "");
            return;
        }
        if (!authentication.isAuthenticated(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"shortener\", charset=\"UTF-8\"");
            respond(exchange, 401, "");
            return;
        }
        routeLinks(exchange, path);
    }

    private void routeLinks(HttpExchange exchange, String path) throws IOException {
        if (LINKS_PATH.equals(path)) {
            if (POST.equals(exchange.getRequestMethod())) {
                createLink(exchange);
            } else {
                respond(exchange, 405, "");
            }
            return;
        }
        if (!path.startsWith(LINKS_PATH + "/")) {
            respond(exchange, 404, "");
            return;
        }
        String id = path.substring(LINKS_PATH.length() + 1);
        validateId(id);
        switch (exchange.getRequestMethod()) {
            case GET -> respond(exchange, 200, links.get(id));
            case "PUT" -> {
                String link = readLink(exchange);
                links.get(id);
                links.upsert(id, link);
                respond(exchange, 200, "");
            }
            case "DELETE" -> {
                links.delete(id);
                respond(exchange, 202, "");
            }
            default -> respond(exchange, 405, "");
        }
    }

    private void createLink(HttpExchange exchange) throws IOException {
        String link = readLink(exchange);
        String id = newId();
        links.upsert(id, link);
        respond(exchange, 201, "http://localhost:" + port + "/" + id);
    }

    private String newId() throws IOException {
        String id;
        StringBuilder builder = new StringBuilder(ID_LENGTH);
        do {
            builder.setLength(0);
            for (int i = 0; i < ID_LENGTH; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            id = builder.toString();
        } while (linkExists(id));
        return id;
    }

    private boolean linkExists(String id) throws IOException {
        try {
            links.get(id);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private void createUser(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "");
            return;
        }
        authentication.register(readBody(exchange));
        respond(exchange, 200, "");
    }

    private static void validateId(String id) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Expected a 10-character alpha or numeric ID");
        }
    }

    private static String readLink(HttpExchange exchange) throws IOException {
        String link = readBody(exchange);
        URI uri = URI.create(link);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Expected an absolute HTTP or HTTPS URL");
        }
        return link;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
