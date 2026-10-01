package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.MyUrlShortenerService;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.RequestUtils;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.StatusCodeException;

import java.io.IOException;

public class UsersHandler implements CustomHttpHandler {
    private final MyUrlShortenerService service;

    public UsersHandler(MyUrlShortenerService service) {
        this.service = service;
    }

    @Override
    public void handlePost(HttpExchange xch) throws IOException, StatusCodeException {
        String body = RequestUtils.responseBody(xch);
        if (!body.contains(":")) {
            throw StatusCodeException.unprocessable();
        }
        String[] credentials = body.split(":", 2);
        service.upsertUser(credentials[0], credentials[1]);
        xch.sendResponseHeaders(200, 0);
    }
}
