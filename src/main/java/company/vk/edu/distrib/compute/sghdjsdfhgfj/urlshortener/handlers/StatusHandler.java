package company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.handlers;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.sghdjsdfhgfj.urlshortener.StatusCodeException;

import java.io.IOException;

public class StatusHandler implements CustomHttpHandler {
    @Override
    public void handleGet(HttpExchange xch) throws IOException, StatusCodeException {
        xch.sendResponseHeaders(200, 0);
    }
}
