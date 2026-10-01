package company.vk.edu.distrib.compute.miiishenka.urlshortener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.authorization.BasicAuthenticator;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.controller.BaseController;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.controller.LinksController;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.controller.RedirectController;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.controller.StatusController;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.controller.UserController;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.dao.PersistentDao;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

public class MiiishenkaUrlShortenerService implements UrlShortenerService {
    private final HttpServer server;
    private final PersistentDao linksDao;
    private final PersistentDao usersDao;

    public MiiishenkaUrlShortenerService(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        linksDao = new PersistentDao("/tmp/links");
        usersDao = new PersistentDao("/tmp/user");
        BasicAuthenticator basicAuthenticator = new BasicAuthenticator(usersDao);
        List<BaseController> controllers = List.of(
                new StatusController(),
                new LinksController(linksDao, basicAuthenticator, port),
                new UserController(usersDao),
                new RedirectController(linksDao)
        );
        controllers.forEach(
                controller -> server.createContext(controller.getPath(), new ControllerHttpHandler(controller))
        );
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        try (usersDao; linksDao) {
            server.stop(1);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist DAO data", e);
        }
    }
}
