package company.vk.edu.distrib.compute.dariabelll.urlshortener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import static company.vk.edu.distrib.compute.dariabelll.urlshortener.UrlShortenerHttpUtils.extractRedirectId;
import static company.vk.edu.distrib.compute.dariabelll.urlshortener.UrlShortenerHttpUtils.readRequestBody;
import static company.vk.edu.distrib.compute.dariabelll.urlshortener.UrlShortenerHttpUtils.sendEmptyResponse;
import static company.vk.edu.distrib.compute.dariabelll.urlshortener.UrlShortenerHttpUtils.sendTextResponse;

public class UrlShortenerHttpHandler implements HttpHandler {

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";

    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;
    private static final int HTTP_ACCEPTED = 202;
    private static final int HTTP_MOVED_PERMANENTLY = 301;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_UNPROCESSABLE_CONTENT = 422;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private static final String STATUS_ENDPOINT = "/v0/status";
    private static final String USERS_ENDPOINT = "/internal/users";
    private static final String LINKS_ENDPOINT = "/v0/links";
    private static final String LINK_BY_ID_PREFIX = LINKS_ENDPOINT + "/";

    private static final String AUTHENTICATION_CHALLENGE =
            "Basic realm=\"url-shortener\", charset=\"UTF-8\"";
    private static final String ALPHA_NUMERIC_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final int port;
    private final JournaledDao urlDao;
    private final JournaledDao userDao;
    private final UrlShortenerAuthentication authentication;

    public UrlShortenerHttpHandler(
            int port,
            JournaledDao urlDao,
            JournaledDao userDao) {
        this.port = port;
        this.urlDao = urlDao;
        this.userDao = userDao;
        authentication = new UrlShortenerAuthentication(userDao);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                handleRequest(exchange);
            } catch (NoSuchElementException e) {
                sendEmptyResponse(exchange, HTTP_NOT_FOUND);
            } catch (IOException e) {
                if (exchange.getResponseCode() != -1) {
                    throw e;
                }
                sendEmptyResponse(exchange, HTTP_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if (!isPublicEndpoint(method, path)
                && authentication.isUnauthenticated(exchange)
        ) {
            exchange.getResponseHeaders().set(
                    "WWW-Authenticate",
                    AUTHENTICATION_CHALLENGE
            );
            sendEmptyResponse(exchange, HTTP_UNAUTHORIZED);
            return;
        }

        List<String> allowedMethods = getAllowedMethods(path);
        if (allowedMethods.isEmpty()) {
            sendEmptyResponse(exchange, HTTP_NOT_FOUND);
            return;
        }
        if (!allowedMethods.contains(method)) {
            exchange.getResponseHeaders().set(
                    "Allow",
                    String.join(", ", allowedMethods)
            );
            sendEmptyResponse(exchange, HTTP_METHOD_NOT_ALLOWED);
            return;
        }
        dispatchByMethod(exchange, method, path);
    }

    private void dispatchByMethod(
            HttpExchange exchange,
            String method,
            String path
    ) throws IOException {
        switch (method) {
            case METHOD_GET -> handleGet(exchange, path);
            case METHOD_POST -> handlePost(exchange, path);
            case METHOD_PUT -> handlePut(exchange, path);
            case METHOD_DELETE -> handleDelete(exchange, path);
            default -> throw new IllegalStateException("Unexpected HTTP method: " + method);
        }
    }

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        if (STATUS_ENDPOINT.equals(path)) {
            handleGetStatus(exchange);
            return;
        }

        if (path.startsWith(LINK_BY_ID_PREFIX)) {
            String id = path.substring(LINK_BY_ID_PREFIX.length());
            handleGetLink(exchange, id);
            return;
        }

        String id = extractRedirectId(path);
        if (id != null) {
            handleRedirect(exchange, id);
            return;
        }

        sendEmptyResponse(exchange, HTTP_NOT_FOUND);
    }

    private void handleGetStatus(HttpExchange exchange) throws IOException {
        int status = urlDao.isStorageAccessible() && userDao.isStorageAccessible()
                ? HTTP_OK
                : HTTP_SERVICE_UNAVAILABLE;
        sendEmptyResponse(exchange, status);
    }

    private void handleGetLink(HttpExchange exchange, String id) throws IOException {
        if (rejectInvalidId(exchange, id)) {
            return;
        }

        String longLink = urlDao.get(id);
        sendTextResponse(exchange, HTTP_OK, longLink);
    }

    private void handleRedirect(HttpExchange exchange, String id) throws IOException {
        if (rejectInvalidId(exchange, id)) {
            return;
        }

        String longLink = urlDao.get(id);
        exchange.getResponseHeaders().set("Location", longLink);
        sendEmptyResponse(exchange, HTTP_MOVED_PERMANENTLY);
    }

    private void handlePost(HttpExchange exchange, String path) throws IOException {
        if (LINKS_ENDPOINT.equals(path)) {
            handlePostLink(exchange);
            return;
        }
        if (USERS_ENDPOINT.equals(path)) {
            handlePostUser(exchange);
            return;
        }

        sendEmptyResponse(exchange, HTTP_NOT_FOUND);
    }

    private void handlePostLink(HttpExchange exchange) throws IOException {
        String longLink = readRequestBody(exchange);

        if (RequestValidators.isInvalidLink(longLink)) {
            sendEmptyResponse(exchange, HTTP_UNPROCESSABLE_CONTENT);
            return;
        }

        String id = generateUniqueId();
        urlDao.upsert(id, longLink);
        String shortLink = "http://localhost:" + port + "/" + id;
        sendTextResponse(exchange, HTTP_CREATED, shortLink);
    }

    private void handlePostUser(HttpExchange exchange) throws IOException {
        String[] registryBody = readRequestBody(exchange).split(":", 2);
        if (RequestValidators.isInvalidRegistryBody(registryBody)) {
            sendEmptyResponse(exchange, HTTP_UNPROCESSABLE_CONTENT);
            return;
        }

        String nickname = registryBody[0];
        String password = registryBody[1];
        userDao.upsert(nickname, password);
        sendEmptyResponse(exchange, HTTP_OK);
    }

    private void handlePut(HttpExchange exchange, String path) throws IOException {
        String id = path.substring(LINK_BY_ID_PREFIX.length());
        if (rejectInvalidId(exchange, id)) {
            return;
        }

        String longLink = readRequestBody(exchange);

        if (RequestValidators.isInvalidLink(longLink)) {
            sendEmptyResponse(exchange, HTTP_UNPROCESSABLE_CONTENT);
            return;
        }
        urlDao.get(id);

        urlDao.upsert(id, longLink);
        sendEmptyResponse(exchange, HTTP_OK);
    }

    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        String id = path.substring(LINK_BY_ID_PREFIX.length());
        if (rejectInvalidId(exchange, id)) {
            return;
        }

        urlDao.delete(id);
        sendEmptyResponse(exchange, HTTP_ACCEPTED);
    }

    private static boolean rejectInvalidId(HttpExchange exchange, String id) throws IOException {
        if (!RequestValidators.isInvalidId(id)) {
            return false;
        }
        sendEmptyResponse(exchange, HTTP_UNPROCESSABLE_CONTENT);
        return true;
    }

    private static List<String> getAllowedMethods(String path) {
        if (STATUS_ENDPOINT.equals(path)) {
            return List.of(METHOD_GET);
        }
        if (USERS_ENDPOINT.equals(path) || LINKS_ENDPOINT.equals(path)) {
            return List.of(METHOD_POST);
        }
        if (path.startsWith(LINK_BY_ID_PREFIX)) {
            return List.of(METHOD_GET, METHOD_PUT, METHOD_DELETE);
        }
        if (extractRedirectId(path) != null) {
            return List.of(METHOD_GET);
        }
        return List.of();
    }

    private static boolean isPublicEndpoint(String method, String path) {
        if (STATUS_ENDPOINT.equals(path) || USERS_ENDPOINT.equals(path)) {
            return true;
        }
        return METHOD_GET.equals(method) && extractRedirectId(path) != null;
    }

    private String generateUniqueId() {
        StringBuilder idBuilder = new StringBuilder(RequestValidators.ID_SIZE);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (true) {
            idBuilder.setLength(0);
            for (int i = 0; i < RequestValidators.ID_SIZE; ++i) {
                int charIndex = random.nextInt(ALPHA_NUMERIC_ALPHABET.length());
                idBuilder.append(ALPHA_NUMERIC_ALPHABET.charAt(charIndex));
            }
            String id = idBuilder.toString();
            try {
                urlDao.get(id);
            } catch (NoSuchElementException e) {
                return id;
            }
        }
    }
}
