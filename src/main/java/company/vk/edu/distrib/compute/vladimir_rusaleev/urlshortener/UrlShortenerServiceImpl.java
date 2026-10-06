package company.vk.edu.distrib.compute.vladimir_rusaleev.urlshortener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

public final class UrlShortenerServiceImpl implements UrlShortenerService {
    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String ROOT_PATH = "/";
    private static final char SLASH = '/';

    private HttpServer server;
    private final Dao<String> links;
    private final Dao<String> users;
    private final UrlLinks linkApi;
    private final UserAuth userAuth;
    private final int port;

    public UrlShortenerServiceImpl(int port) throws IOException {
        Path root = Path.of(System.getProperty("user.home"), ".vk-urlshortener", "vladimir");
        links = new FileDao(root.resolve(Integer.toString(port)).resolve("links"));
        users = new FileDao(root.resolve(Integer.toString(port)).resolve("users"));
        linkApi = new UrlLinks(links, port);
        userAuth = new UserAuth(users);
        this.port = port;
    }

    @Override
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext("/", this::handle);
            server.start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not start server", exception);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(1);
        }
        try {
            links.close();
            users.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close stores", exception);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                route(exchange);
            } catch (IOException exception) {
                UrlLinks.respond(exchange, 503, null);
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/v0/status".equals(path)) {
            UrlLinks.respond(exchange, GET_METHOD.equals(exchange.getRequestMethod()) ? 200 : 405, null);
            return;
        }
        if ("/internal/users".equals(path)) {
            userAuth.createUser(exchange);
            return;
        }
        if (path.startsWith("/v0/links")) {
            routeLinks(exchange, path);
            return;
        }
        routeRedirect(exchange, path);
    }

    private void routeLinks(HttpExchange exchange, String path) throws IOException {
        if (!userAuth.authenticated(exchange)) {
            exchange.getResponseHeaders().set(
                "WWW-Authenticate", "Basic realm=\"urlshortener\", charset=\"UTF-8\"");
            UrlLinks.respond(exchange, 401, null);
            return;
        }
        if ("/v0/links".equals(path)) {
            if (POST_METHOD.equals(exchange.getRequestMethod())) {
                linkApi.createLink(exchange);
            } else {
                UrlLinks.respond(exchange, 405, null);
            }
            return;
        }
        if (path.startsWith("/v0/links/")) {
            linkApi.linkById(exchange, path.substring("/v0/links/".length()));
            return;
        }
        UrlLinks.respond(exchange, 404, null);
    }

    private void routeRedirect(HttpExchange exchange, String path) throws IOException {
        if (path.startsWith(ROOT_PATH)
            && path.indexOf(SLASH, ROOT_PATH.length()) == -1
            && GET_METHOD.equals(exchange.getRequestMethod())) {
            linkApi.redirect(exchange, path.substring(ROOT_PATH.length()));
            return;
        }
        UrlLinks.respond(exchange, 404, null);
    }
}
