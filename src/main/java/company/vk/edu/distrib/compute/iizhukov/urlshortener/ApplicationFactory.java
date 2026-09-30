package company.vk.edu.distrib.compute.iizhukov.urlshortener;

import java.io.IOException;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;

@UrlShortenerAuthTest
@UrlShortenerTest
public class ApplicationFactory extends AbstractHttpServiceFactory<Application> {
    @Override
    protected Application doCreate(int port) throws IOException {
        var app = new Application();
        app.init(port);
        return app;
    }
}
