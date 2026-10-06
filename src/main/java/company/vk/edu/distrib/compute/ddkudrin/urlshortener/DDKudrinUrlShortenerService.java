package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

public class DDKudrinUrlShortenerService implements UrlShortenerService {

    private final HttpServer server;

    public DDKudrinUrlShortenerService(int port) throws IOException {
        Dao<String> linkDao = new DDKudrinPersistentDao(
                Path.of(System.getProperty("user.home"), "data", "links.wal")
        );
        Dao<String> userDao = new DDKudrinPersistentDao(
                Path.of(System.getProperty("user.home"), "data", "users.wal")
        );
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/v0/status", new DDKudrinStatusHandler());
        this.server.createContext(
            "/v0/links",
            new DDKudrinAuthMiddleware(new DDKudrinLinksHandler(linkDao), userDao)
        );
        this.server.createContext("/internal/users", new DDKudrinUserHandler(userDao));
        this.server.createContext("/", new DDKudrinRedirectHandler(linkDao));
    }

    @Override
    public void start() {
        server.start();
    }

    @Override 
    public void stop() {
        server.stop(1);
    }
}
