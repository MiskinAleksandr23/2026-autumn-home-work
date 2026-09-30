package company.vk.edu.distrib.compute.sovesti.urlshortener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.sovesti.urlshortener.auth.Authentication;
import company.vk.edu.distrib.compute.sovesti.urlshortener.auth.AuthenticationScheme;
import company.vk.edu.distrib.compute.sovesti.urlshortener.auth.BasicAuthentication;
import company.vk.edu.distrib.compute.sovesti.urlshortener.dao.InFileDao;
import company.vk.edu.distrib.compute.sovesti.urlshortener.handler.WriteResponse;
import company.vk.edu.distrib.compute.sovesti.urlshortener.route.HttpRoute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.route.InternalUsersRoute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.route.LinksRoute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.route.RootRoute;
import company.vk.edu.distrib.compute.sovesti.urlshortener.route.StatusRoute;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;

public final class VfedorovUrlShortenerService implements UrlShortenerService {

    private final HttpServer server;
    private final List<Dao<?>> daos;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public VfedorovUrlShortenerService(HttpServer server) {
        this.server = Objects.requireNonNull(server);
        this.daos = new ArrayList<>();
    }

    @Override
    public void start() {
        Dao<String> links = tryCreateDao("links");
        Dao<String> users = tryCreateDao("users");
        AuthenticationScheme authentication = new BasicAuthentication("URL shortener", users);
        createContext(new StatusRoute(), authentication);
        createContext(new RootRoute(links), authentication);
        createContext(new InternalUsersRoute(users), authentication);
        createAuthenticatedContext(new LinksRoute(links), authentication);
        server.start();
    }

    private Dao<String> tryCreateDao(String key) {
        try {
            return createDao(key);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start DAO at %s".formatted(key), e);
        }
    }

    private Dao<String> createDao(String key) throws IOException {
        InFileDao dao = new InFileDao(temporaryDirectory().resolve(key));
        dao.read();
        daos.add(dao);
        return dao;
    }

    private Path temporaryDirectory() throws IOException {
        return Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "vfedorov_urlshortener"));
    }

    private void createAuthenticatedContext(HttpRoute route, AuthenticationScheme authentication) {
        createContext(route, authentication).getFilters().add(new Authentication(authentication));
    }

    private HttpContext createContext(HttpRoute route, AuthenticationScheme authentication) {
        HttpContext context = server.createContext(route.prefix(), route.handler());
        context.getFilters().add(Filter.beforeHandler("Parse request path", route::parsePath));
        context.getFilters().add(new WriteResponse(authentication));
        return context;
    }

    @Override
    public void stop() {
        daos.forEach(this::closeDao);
        server.stop(1);
    }

    private void closeDao(Dao<?> dao) {
        try {
            dao.close();
        } catch (IOException e) {
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}
