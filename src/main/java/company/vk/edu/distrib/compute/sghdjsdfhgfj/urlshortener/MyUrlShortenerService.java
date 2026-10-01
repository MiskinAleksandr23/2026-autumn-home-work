package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers.*;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class MyUrlShortenerService implements UrlShortenerService {
    private final HttpServer server;
    private final PersistentDao urls;
    private final PersistentDao users;

    public MyUrlShortenerService(int port) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(port);
        server = HttpServer.create(addr, 0);
        Path tempDir = Files.createTempDirectory("sghdjsdfhgfj");
        urls = new PersistentDao(tempDir.resolve("urls.dat"));
        users = new PersistentDao(tempDir.resolve("users.dat"));

        addContext("/v0/status", new StatusHandler());
        addContext("/v0/links", new LinksHandler(this));
        addContext("/internal/users", new UsersHandler(this));
        addContext("/", new RedirectHandler(this));
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.stop(0);
    }

    public String host() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private HttpContext addContext(String path, CustomHttpHandler handler) {
        return server.createContext(path, new CustomHttpHandlerTranslator(handler));
    }

    private boolean isAuthenticated(HttpExchange xch) throws IOException {
        String header = xch.getRequestHeaders().getFirst("Authorization");
        if (header == null) {
            return false;
        }
        if (!header.startsWith("Basic ")) {
            return false;
        }
        Base64.Decoder decoder = Base64.getDecoder();
        String auth = new String(decoder.decode(header.substring(6)), StandardCharsets.UTF_8);
        String[] credentials = auth.split(":");
        return credentials.length == 2
                && users.containsKey(credentials[0])
                && users.get(credentials[0]).equals(credentials[1]);
    }

    public void checkAuthentication(HttpExchange xch) throws IOException, StatusCodeException {
        if (!isAuthenticated(xch)) {
            throw StatusCodeException.unauthorized();
        }
    }

    public boolean isUrlRegistered(String id) {
        return urls.containsKey(id);
    }

    public void upsertUrl(String id, String url) throws IOException {
        urls.upsert(id, url);
    }

    public String getLongUrl(String id) throws IOException {
        return urls.get(id);
    }

    public void deleteUrl(String id) throws IOException {
        urls.delete(id);
    }

    public void upsertUser(String username, String password) throws IOException {
        users.upsert(username, password);
    }
}
