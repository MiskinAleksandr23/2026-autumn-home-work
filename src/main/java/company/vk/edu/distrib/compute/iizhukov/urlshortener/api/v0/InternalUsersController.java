package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.v0;

import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.BaseController;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.HttpStatus;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Request;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Response;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.db.dao.UserDao;

public class InternalUsersController extends BaseController {
    private final UserDao dao = UserDao.create();

    public InternalUsersController(int port) {
        super(port);
    }

    @Override
    public String path() {
        return "/internal/users";
    }

    @Override
    public Response post(Request request) {
        var raw = request.body().split(":");
        var user = raw[0];
        var password = raw[1];

        dao.upsert(user, Integer.toString(password.hashCode()));

        return Response.builder()
                .setStatus(HttpStatus.OK)
                .build();
    }
}
