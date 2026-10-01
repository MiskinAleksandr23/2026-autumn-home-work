package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.MyUrlShortenerService;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.RequestUtils;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.StatusCodeException;

import java.io.IOException;

public class LinksHandler implements CustomHttpHandler {
    private final MyUrlShortenerService service;
    private static final int PATH_DEPTH = 3;

    public LinksHandler(MyUrlShortenerService service) {
        this.service = service;
    }

    @Override
    public void handleGet(HttpExchange xch) throws IOException, StatusCodeException {
        service.checkAuthentication(xch);
        String urlId = getId(xch);
        if (!service.isUrlRegistered(urlId)) {
            throw StatusCodeException.notFound();
        }
        String url = service.getLongUrl(urlId);
        xch.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        xch.sendResponseHeaders(200, 0);
        xch.getResponseBody().write(url.getBytes());
        xch.close();
    }

    @Override
    public void handlePost(HttpExchange xch) throws IOException, StatusCodeException {
        service.checkAuthentication(xch);
        String url = RequestUtils.responseBody(xch);
        if (!RequestUtils.isValidUrl(url)) {
            throw StatusCodeException.unprocessable();
        }
        String id = RequestUtils.generateId();
        while (service.isUrlRegistered(id)) {
            id = RequestUtils.generateId();
        }
        service.upsertUrl(id, url);
        xch.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        xch.sendResponseHeaders(201, 0);

        String shortLink = service.host() + "/" + id;
        xch.getResponseBody().write(shortLink.getBytes());
        xch.close();
    }

    @Override
    public void handlePut(HttpExchange xch) throws IOException, StatusCodeException {
        service.checkAuthentication(xch);
        String url = RequestUtils.responseBody(xch);
        String id = getId(xch);
        if (!RequestUtils.isValidUrl(url)) {
            throw StatusCodeException.unprocessable();
        }
        if (!service.isUrlRegistered(id)) {
            throw StatusCodeException.notFound();
        }
        service.upsertUrl(id, url);
        xch.sendResponseHeaders(200, 0);
        xch.close();
    }

    @Override
    public void handleDelete(HttpExchange xch) throws IOException, StatusCodeException {
        service.checkAuthentication(xch);
        String id = getId(xch);
        service.deleteUrl(id);
        xch.sendResponseHeaders(202, 0);
        xch.close();
    }

    private String getId(HttpExchange xch) throws StatusCodeException {
        String[] separatedPath = xch.getRequestURI().getPath().split("/");
        if (separatedPath.length <= PATH_DEPTH) {
            throw StatusCodeException.notFound();
        }
        String id = separatedPath[PATH_DEPTH];
        if (!RequestUtils.isValidId(id)) {
            throw StatusCodeException.unprocessable();
        }
        return id;
    }
}
