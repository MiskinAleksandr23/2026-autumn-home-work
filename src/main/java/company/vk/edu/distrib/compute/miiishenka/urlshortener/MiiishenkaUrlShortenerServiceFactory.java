package company.vk.edu.distrib.compute.miiishenka.urlshortener;

import java.io.IOException;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;

@UrlShortenerTest
@UrlShortenerAuthTest
public class MiiishenkaUrlShortenerServiceFactory extends AbstractHttpServiceFactory<MiiishenkaUrlShortenerService> {
    @Override
    protected MiiishenkaUrlShortenerService doCreate(int port) throws IOException {
        return new MiiishenkaUrlShortenerService(port);
    }
}
