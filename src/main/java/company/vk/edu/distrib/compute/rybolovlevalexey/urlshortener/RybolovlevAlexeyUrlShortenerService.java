package company.vk.edu.distrib.compute.rybolovlevalexey.urlshortener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RybolovlevAlexeyUrlShortenerService implements UrlShortenerService {

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";
    private static final String PATH_PREFIX_V0_LINKS = "/v0/links/";

    private final int port;
    private final HttpServer server;
    private static final Logger log = LoggerFactory.getLogger(RybolovlevAlexeyUrlShortenerService.class);
    private final Dao<String> dao;
    private final Dao<String> authDao;
    private final ShortLinkIDGenerator shortLinkGenerator = new ShortLinkIDGenerator(10);

    public RybolovlevAlexeyUrlShortenerService(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        // используются захардкоженные названия файлов, чтобы не заморачиваться
        // можно добавить как параметры инициализации
        final Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "rybolovlevalexey-data");
        this.dao = new RybolovlevAlexeyPersistentDao(dataDir.resolve("links.properties"));
        this.authDao = new RybolovlevAlexeyPersistentDao(dataDir.resolve("users.properties"));

        server.createContext("/v0/status", new ErrorHandler(statusHandler()));
        server.createContext("/v0/links", new ErrorHandler(linksHandler()));
        server.createContext("/", new ErrorHandler(redirectHandler()));
        server.createContext("/internal/users", new ErrorHandler(usersHandler()));
    }

    private HttpHandler statusHandler() {
        return httpExchange -> {
            log.info("Received request /v0/status");
            final var requestMethod = httpExchange.getRequestMethod();
            if (METHOD_GET.equals(requestMethod)) {
                httpExchange.sendResponseHeaders(200, 0);
            } else {
                httpExchange.sendResponseHeaders(405, 0);
            }
            httpExchange.close();
        };
    }

    private HttpHandler linksHandler() {
        return httpExchange -> {
            if (!checkAuth(httpExchange)) {
                httpExchange.sendResponseHeaders(401, 0);
                httpExchange.close();
                return;
            }

            final var requestMethod = httpExchange.getRequestMethod();
            log.info("Request to {} with method {}", PATH_PREFIX_V0_LINKS, requestMethod);

            switch (requestMethod) {
                case METHOD_GET:
                    getV0LinksHandler(httpExchange);
                    break;
                case METHOD_POST:
                    postV0LinksHandler(httpExchange);
                    break;
                case METHOD_PUT:
                    putV0LinksHandler(httpExchange);
                    break;
                case METHOD_DELETE:
                    deleteV0LinksHandler(httpExchange);
                    break;
                default:
                    httpExchange.sendResponseHeaders(405, 0);
                    httpExchange.close();
                    break;
            }
        };
    }

    private HttpHandler redirectHandler() {
        return httpExchange -> {
            final var requestMethod = httpExchange.getRequestMethod();
            final var path = httpExchange.getRequestURI().getPath();
            log.info("Received request for redirect with method {} and ID {}", requestMethod, path);
            final var linkID = path.substring(1);

            if (!Objects.equals(requestMethod, METHOD_GET)) {
                httpExchange.sendResponseHeaders(405, 0);
                return;
            }
            try {
                RybolovlevAlexeyUrlShortenerUtils.validateLinkID(linkID);
                final var longLink = dao.get(linkID);

                httpExchange.getResponseHeaders().add("Location", longLink);
                httpExchange.sendResponseHeaders(301, -1);
            } catch (IllegalArgumentException e) {
                httpExchange.sendResponseHeaders(422, 0);
            } catch (NoSuchElementException e) {
                httpExchange.sendResponseHeaders(404, 0);
            }
            httpExchange.close();
        };
    }

    private HttpHandler usersHandler() {
        return httpExchange -> {
            final var requestMethod = httpExchange.getRequestMethod();
            log.info("Received request to /internal/users with method {}", requestMethod);
            if (!Objects.equals(METHOD_POST, requestMethod)) {
                httpExchange.sendResponseHeaders(405, 0);
                httpExchange.close();
                return;
            }

            final var body = new String(httpExchange.getRequestBody().readAllBytes());
            final var splitIndex = body.indexOf(':');

            if (splitIndex != -1 && body.indexOf(':', splitIndex + 1) == -1) {
                final var username = body.substring(0, splitIndex);
                final var password = body.substring(splitIndex + 1);
                authDao.upsert(username, password);
                httpExchange.sendResponseHeaders(200, 0);
            } else {
                httpExchange.sendResponseHeaders(422, 0);
            }
            httpExchange.close();
        };
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        this.server.stop(1);
        try {
            this.dao.close();
            this.authDao.close();
        } catch (IOException e) {
            log.error("Failed to save data on stop", e);
        }
    }

    private boolean checkAuth(HttpExchange httpExchange) {
        final var authHeader = httpExchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }
        final var credentials = authHeader.substring("Basic ".length());
        final String decodedStr;
        try {
            decodedStr = new String(
                Base64.getDecoder().decode(credentials), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        final var splitIndex = decodedStr.indexOf(':');
        if (splitIndex == -1) {
            return false;
        }
        final var username = decodedStr.substring(0, splitIndex);
        final var password = decodedStr.substring(splitIndex + 1);

        try {
            final var passwordFromDao = authDao.get(username);
            if (password.isEmpty() || !password.equals(passwordFromDao)) {
                return false;
            }
        } catch (IOException | NoSuchElementException e) {
            return false;
        }
        return true;
    }

    private void getV0LinksHandler(HttpExchange httpExchange) throws IOException {
        final var path = httpExchange.getRequestURI().getPath();
        final var linkID = path.substring(PATH_PREFIX_V0_LINKS.length());
        log.info("Received request to GET {} with ID {}", PATH_PREFIX_V0_LINKS, linkID);

        try {
            RybolovlevAlexeyUrlShortenerUtils.validateLinkID(linkID);
            final var valueLongLink = this.dao.get(linkID);
            httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            httpExchange.sendResponseHeaders(200, valueLongLink.getBytes(StandardCharsets.UTF_8).length);
            httpExchange.getResponseBody().write(valueLongLink.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchElementException e) {
            httpExchange.sendResponseHeaders(404, 0);
        } catch (IllegalArgumentException e) {
            httpExchange.sendResponseHeaders(422, 0);
        }
        httpExchange.close();
    }

    private void postV0LinksHandler(HttpExchange httpExchange) throws IOException {
        final var body = new String(httpExchange.getRequestBody().readAllBytes());
        log.info("Received request to POST {} with body {}", PATH_PREFIX_V0_LINKS, body);
        RybolovlevAlexeyUrlShortenerUtils.validateLink(body);
        final var linkID = shortLinkGenerator.generateID();
        dao.upsert(linkID, body);
        final var resultBody = "http://localhost:%d/%s".formatted(port, linkID);
        log.info("Sending response {}", resultBody);

        httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        httpExchange.sendResponseHeaders(201, resultBody.getBytes(StandardCharsets.UTF_8).length);
        httpExchange.getResponseBody().write(resultBody.getBytes(StandardCharsets.UTF_8));
        httpExchange.close();
    }

    private void putV0LinksHandler(HttpExchange httpExchange) throws IOException {
        final var body = new String(httpExchange.getRequestBody().readAllBytes());
        final var path = httpExchange.getRequestURI().getPath();
        final var linkID = path.substring(PATH_PREFIX_V0_LINKS.length());
        log.info("Received request to PUT {} with body {} and link ID {}", PATH_PREFIX_V0_LINKS, body, linkID);

        try {
            RybolovlevAlexeyUrlShortenerUtils.validateLinkID(linkID);
            RybolovlevAlexeyUrlShortenerUtils.validateLink(body);
            dao.get(linkID);
            dao.upsert(linkID, body);
            httpExchange.sendResponseHeaders(200, 0);
        } catch (NoSuchElementException e) {
            httpExchange.sendResponseHeaders(404, 0);
        } catch (IllegalArgumentException e) {
            httpExchange.sendResponseHeaders(422, 0);
        }
        httpExchange.close();
    }

    private void deleteV0LinksHandler(HttpExchange httpExchange) throws IOException {
        final var path = httpExchange.getRequestURI().getPath();
        final var linkID = path.substring(PATH_PREFIX_V0_LINKS.length());
        log.info("Received request to DELETE {} with link ID {}", PATH_PREFIX_V0_LINKS, linkID);

        try {
            RybolovlevAlexeyUrlShortenerUtils.validateLinkID(linkID);
            dao.delete(linkID);
            httpExchange.sendResponseHeaders(202, 0);
        } catch (NoSuchElementException e) {
            httpExchange.sendResponseHeaders(404, 0);
        } catch (IllegalArgumentException e) {
            httpExchange.sendResponseHeaders(422, 0);
        }
        httpExchange.close();
    }

    private record ErrorHandler(HttpHandler delegate) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                delegate.handle(exchange);
            } catch (IllegalArgumentException e) {
                final var message = e.getMessage();
                exchange.sendResponseHeaders(422, message.length());
                exchange.getResponseBody().write(message.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                final var message = e.getMessage();
                exchange.sendResponseHeaders(500, message.length());
                exchange.getResponseBody().write(message.getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        }
    }

    public static class ShortLinkIDGenerator {
        private static final Random RANDOM = new Random();
        private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        private final int idLength;

        public ShortLinkIDGenerator(int idLength) {
            this.idLength = idLength;
        }

        public String generateID() {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < idLength; i++) {
                int index = RANDOM.nextInt(CHARS.length());
                result.append(CHARS.charAt(index));
            }
            return result.toString();
        }
    }
}
