package company.vk.edu.distrib.compute.iizhukov.urlshortener.api.v0;

import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.BaseController;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.HttpStatus;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Request;
import company.vk.edu.distrib.compute.iizhukov.urlshortener.api.helpers.Response;

public class StatusController extends BaseController {
    public StatusController(int port) {
        super(port);
    }

    @Override
    public String path() {
        return "/v0/status";
    }

    @Override
    public Response get(Request request) {
        return Response.builder()
                .setStatus(HttpStatus.OK)
                .build();
    }
}
