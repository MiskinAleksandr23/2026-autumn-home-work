package company.vk.edu.distrib.compute.miiishenka.urlshortener.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.HttpStatusException;
import company.vk.edu.distrib.compute.miiishenka.urlshortener.exception.UnprocessableContentException;

public class UserController extends BaseController {
    private static final int CREDENTIAL_PARTS_COUNT = 2;

    private final Dao<String> usersDao;

    public UserController(Dao<String> usersDao) {
        super();
        this.usersDao = usersDao;
    }

    @Override
    public String getPath() {
        return "/internal/users";
    }

    @Override
    public void post(HttpExchange exchange) throws HttpStatusException, IOException {
        validateExactPath(exchange);
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String[] parts = requestBody.split(":", CREDENTIAL_PARTS_COUNT);
        if (parts.length != CREDENTIAL_PARTS_COUNT) {
            throw new UnprocessableContentException();
        }
        String user = parts[0];
        String password = parts[1];
        usersDao.upsert(user, password);
        exchange.sendResponseHeaders(200, 0);
    }
}
