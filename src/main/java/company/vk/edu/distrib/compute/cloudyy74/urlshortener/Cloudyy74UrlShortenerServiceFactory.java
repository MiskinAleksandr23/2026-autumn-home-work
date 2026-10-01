package company.vk.edu.distrib.compute.cloudyy74.urlshortener;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;

import java.io.IOException;

@UrlShortenerTest
@UrlShortenerAuthTest
public class Cloudyy74UrlShortenerServiceFactory extends AbstractHttpServiceFactory<Cloudyy74UrlShortenerService> {
    @Override
    protected Cloudyy74UrlShortenerService doCreate(int port) throws IOException {
        return new Cloudyy74UrlShortenerService(port);
    }
}
