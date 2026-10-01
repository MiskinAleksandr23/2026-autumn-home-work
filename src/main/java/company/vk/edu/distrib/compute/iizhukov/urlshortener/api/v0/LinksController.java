package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.v0;

import java.util.List;
import java.util.NoSuchElementException;

import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.BaseController;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.HttpStatus;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Request;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Response;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.middlewares.AuthMiddleware;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.dao.LinksDao;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.utils.GeneratorUtils;

public class LinksController extends BaseController {
    private final LinksDao dao = LinksDao.create();

    public LinksController(int port) {
        super(port, List.of(new AuthMiddleware()));
    }

    @Override
    public String path() {
        return "/v0/links";
    }

    @Override
    public Response get(Request request) {
        var key = request.path().substring(path().length() + 1);

        try {
            return Response.builder()
                    .setContent(dao.get(key))
                    .setStatus(HttpStatus.OK)
                    .build();
        } catch (NoSuchElementException e) {
            return Response.builder()
                    .setStatus(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    @Override
    public Response post(Request request) {
        var url = request.body();
        var key = GeneratorUtils.generateKey(10);

        dao.upsert(key, url);

        return Response.builder()
                .setStatus(HttpStatus.CREATED)
                .setContent("http://localhost:%s/%s".formatted(port(), key))
                .build();
    }

    @Override
    public Response put(Request request) {
        var key = request.path().substring(path().length() + 1);
        var url = request.body();

        try {
            dao.get(key);
        } catch (NoSuchElementException e) {
            return Response.builder()
                    .setStatus(HttpStatus.NOT_FOUND)
                    .build();
        }

        dao.upsert(key, url);

        return Response.builder()
                .setStatus(HttpStatus.OK)
                .setContent("http://localhost:%s/%s".formatted(port(), key))
                .build();
    }

    @Override
    public Response delete(Request request) {
        var key = request.path().substring(path().length() + 1);
        dao.delete(key);

        return Response.builder()
                .setStatus(HttpStatus.ACCEPTED)
                .build();
    }
}
