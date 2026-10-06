package company.vk.edu.distrib.compute.ddkudrin.urlshortener;

import java.io.IOException;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;

@UrlShortenerTest
@UrlShortenerAuthTest
public class DDKudrinUrlShortenerServiceFactory extends AbstractHttpServiceFactory<DDKudrinUrlShortenerService> {

    @Override
    protected DDKudrinUrlShortenerService doCreate(int port) throws IOException {
        return new DDKudrinUrlShortenerService(port);
    }
}
