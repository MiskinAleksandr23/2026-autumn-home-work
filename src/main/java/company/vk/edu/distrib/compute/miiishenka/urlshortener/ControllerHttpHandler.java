package company.vk.edu.distrib.compute.miiishenka.urlshortener;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.controller.BaseController;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.HttpStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerHttpHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(ControllerHttpHandler.class);
    private final BaseController controller;

    public ControllerHttpHandler(BaseController controller) {
        this.controller = controller;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> controller.get(exchange);
                    case "POST" -> controller.post(exchange);
                    case "PUT" -> controller.put(exchange);
                    case "DELETE" -> controller.delete(exchange);
                    default -> exchange.sendResponseHeaders(405, 0);
                }
            } catch (HttpStatusException e) {
                exchange.sendResponseHeaders(e.getStatusCode(), 0);
            } catch (Exception e) {
                log.error("Error while handling request", e);
                exchange.sendResponseHeaders(500, 0);
            }
        }
    }
}
