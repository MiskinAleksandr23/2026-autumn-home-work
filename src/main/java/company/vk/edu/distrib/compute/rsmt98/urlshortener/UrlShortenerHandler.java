package company.vk.edu.distrib.compute.rsmt98.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.function.BooleanSupplier;

final class UrlShortenerHandler implements HttpHandler {
    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String PUT_METHOD = "PUT";
    private static final String DELETE_METHOD = "DELETE";
    private static final String STATUS_PATH = "/v0/status";
    private static final String USERS_PATH = "/internal/users";
    private static final String LINKS_PATH = "/v0/links/";
    private final String shortLinkPrefix;
    private final LinkStore links;
    private final BasicAuthentication auth;
    private final BooleanSupplier available;

    UrlShortenerHandler(int port, Dao<String> links, Dao<String> users, BooleanSupplier available) {
        shortLinkPrefix = "http://localhost:" + port + '/';
        this.links = new LinkStore(links);
        auth = new BasicAuthentication(users);
        this.available = available;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Response response = process(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            if (response.body().isEmpty()) {
                exchange.sendResponseHeaders(response.status(), -1);
            } else {
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
            }
        }
    }

    private Response process(HttpExchange exchange) {
        try {
            return route(exchange);
        } catch (NoSuchElementException e) {
            return new Response(404);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return new Response(422);
        } catch (IOException e) {
            return new Response(503);
        }
    }

    private Response route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        if (STATUS_PATH.equals(path)) {
            return status(exchange);
        }
        if (USERS_PATH.equals(path)) {
            return registerUser(exchange);
        }
        boolean redirectPath = path.startsWith("/") && path.indexOf('/', 1) == -1;
        if (redirectPath && GET_METHOD.equals(exchange.getRequestMethod())) {
            String id = path.substring(1);
            String longLink = links.get(id);
            exchange.getResponseHeaders().set("Location", URI.create(longLink).toASCIIString());
            return new Response(301);
        }
        if (!auth.authenticate(exchange.getRequestHeaders())) {
            exchange.getResponseHeaders().set("WWW-Authenticate", BasicAuthentication.CHALLENGE);
            return new Response(401);
        }
        return routeLinks(exchange, path, redirectPath);
    }

    private Response routeLinks(HttpExchange exchange, String path, boolean redirectPath)
            throws IOException {
        if (path.length() == LINKS_PATH.length() - 1 && LINKS_PATH.startsWith(path)) {
            return POST_METHOD.equals(exchange.getRequestMethod())
                    ? new Response(201, shortLinkPrefix + links.create(readBody(exchange)))
                    : methodNotAllowed(exchange, POST_METHOD);
        }
        if (path.startsWith(LINKS_PATH)) {
            String id = path.substring(LINKS_PATH.length());
            return accessLink(exchange, id);
        }
        return redirectPath ? methodNotAllowed(exchange, GET_METHOD) : new Response(404);
    }

    private Response status(HttpExchange exchange) {
        return GET_METHOD.equals(exchange.getRequestMethod())
                ? new Response(available.getAsBoolean() ? 200 : 503)
                : methodNotAllowed(exchange, GET_METHOD);
    }

    private Response registerUser(HttpExchange exchange) throws IOException {
        if (!POST_METHOD.equals(exchange.getRequestMethod())) {
            return methodNotAllowed(exchange, POST_METHOD);
        }
        auth.register(readBody(exchange));
        return new Response(200);
    }

    private Response accessLink(HttpExchange exchange, String id) throws IOException {
        return switch (exchange.getRequestMethod()) {
            case GET_METHOD -> new Response(200, links.get(id));
            case PUT_METHOD -> {
                links.update(id, readBody(exchange));
                yield new Response(200);
            }
            case DELETE_METHOD -> {
                links.delete(id);
                yield new Response(202);
            }
            default -> {
                yield methodNotAllowed(
                        exchange, GET_METHOD + ", " + PUT_METHOD + ", " + DELETE_METHOD);
            }
        };
    }

    private static Response methodNotAllowed(HttpExchange exchange, String methods) {
        exchange.getResponseHeaders().set("Allow", methods);
        return new Response(405);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    private record Response(int status, String body) {
        private Response(int status) {
            this(status, "");
        }
    }
}
