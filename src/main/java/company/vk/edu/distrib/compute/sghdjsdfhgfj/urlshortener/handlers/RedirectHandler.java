package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.MyUrlShortenerService;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.RequestUtils;

import java.io.IOException;

public class RedirectHandler implements CustomHttpHandler {
    private final MyUrlShortenerService service;

    public RedirectHandler(MyUrlShortenerService service) {
        this.service = service;
    }

    @Override
    public void handleGet(HttpExchange xch) throws IOException {
        String urlId = xch.getRequestURI().getPath().replaceFirst("/", "");
        if (RequestUtils.isValidId(urlId)) {
            if (service.isUrlRegistered(urlId)) {
                xch.getResponseHeaders().add("Location", service.getLongUrl(urlId));
                xch.sendResponseHeaders(301, 0);
            } else {
                xch.sendResponseHeaders(404, 0);
            }
        } else {
            xch.sendResponseHeaders(422, 0);
        }
    }
}
