package company.vk.edu.distrib.compute.miiishenka.urlshortener.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.authorization.BasicAuthenticator;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.HttpStatusException;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.NotFoundException;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.UnprocessableContentException;

public class LinksController extends BaseController {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Dao<String> linksDao;
    private final BasicAuthenticator basicAuthenticator;
    private final int port;

    public LinksController(Dao<String> linksDao, BasicAuthenticator basicAuthenticator, int port) {
        super();
        this.linksDao = linksDao;
        this.basicAuthenticator = basicAuthenticator;
        this.port = port;
    }

    @Override
    public String getPath() {
        return "/v0/links";
    }

    @Override
    public void get(HttpExchange exchange) throws HttpStatusException, IOException {
        basicAuthenticator.authenticate(exchange);
        String shortId = extractShortId(exchange);
        String longLink = linksDao.get(shortId);
        if (longLink == null) {
            throw new NotFoundException();
        }
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(longLink.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void post(HttpExchange exchange) throws HttpStatusException, IOException {
        validateExactPath(exchange);
        basicAuthenticator.authenticate(exchange);
        String longLink = readLongLink(exchange);
        String shortId = generateShortId();
        linksDao.upsert(shortId, longLink);
        String shortUrl = "http://localhost:%d/%s".formatted(port, shortId);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(201, 0);
        exchange.getResponseBody().write(shortUrl.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void put(HttpExchange exchange) throws HttpStatusException, IOException {
        basicAuthenticator.authenticate(exchange);
        String shortId = extractShortId(exchange);
        String newLongLink = readLongLink(exchange);
        String oldLongLink = linksDao.get(shortId);
        if (oldLongLink == null) {
            throw new NotFoundException();
        }
        linksDao.upsert(shortId, newLongLink);
        exchange.sendResponseHeaders(200, 0);
    }

    @Override
    public void delete(HttpExchange exchange) throws HttpStatusException, IOException {
        basicAuthenticator.authenticate(exchange);
        String shortId = extractShortId(exchange);
        linksDao.delete(shortId);
        exchange.sendResponseHeaders(202, 0);
    }

    private String generateShortId() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < SHORT_ID_LENGTH; i++) {
            int index = RANDOM.nextInt(ALPHABET.length());
            result.append(ALPHABET.charAt(index));
        }
        return result.toString();
    }

    private boolean isInvalidLongLink(String longLink) {
        try {
            URI uri = new URI(longLink).parseServerAuthority();
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return true;
            }
            return uri.getHost() == null || uri.getPort() > 65535;
        } catch (URISyntaxException e) {
            return true;
        }
    }

    private String readLongLink(HttpExchange exchange) throws UnprocessableContentException, IOException {
        String longLink = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (isInvalidLongLink(longLink)) {
            throw new UnprocessableContentException();
        }
        return longLink;
    }
}
