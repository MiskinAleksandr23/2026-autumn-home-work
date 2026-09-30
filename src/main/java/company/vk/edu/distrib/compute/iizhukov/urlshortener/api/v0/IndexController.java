package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.v0;

import java.util.NoSuchElementException;

import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.BaseController;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.HttpStatus;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Request;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Response;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.dao.LinksDao;

public class IndexController extends BaseController {
    private final LinksDao dao = LinksDao.create();

    public IndexController(int port) {
        super(port);
    }

    @Override
    public String path() {
        return "/";
    }

    @Override
    public Response get(Request request) {
        var key = request.path().substring(path().length());

        try {
            return Response.builder()
                    .setStatus(HttpStatus.MOVED_PERMANENTLY)
                    .addHeader("Location", dao.get(key))
                    .build();
        } catch (NoSuchElementException e) {
            return Response.builder()
                    .setStatus(HttpStatus.NOT_FOUND)
                    .build();
        }
    }
}
