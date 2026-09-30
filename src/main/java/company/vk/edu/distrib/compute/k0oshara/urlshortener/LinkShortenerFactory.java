package company.vk.edu.distrib.compute.k0oshara.urlshortener;

import java.io.IOException;
import java.nio.file.Path;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;

@UrlShortenerTest
@UrlShortenerAuthTest
public final class LinkShortenerFactory extends AbstractHttpServiceFactory<UrlShortenerService> {
    private final Path directory;

    public LinkShortenerFactory() {
        this(Path.of(System.getProperty("urlshortener.data.dir",
            Path.of(System.getProperty("java.io.tmpdir"), "urlshortener").toString())));
    }

    LinkShortenerFactory(Path directory) {
        super();
        this.directory = directory;
    }

    @Override
    protected UrlShortenerService doCreate(int port) throws IOException {
        return new LinkShortenerService(port,
            new PersistentDao(directory.resolve("links.xml")),
            new PersistentDao(directory.resolve("users.xml")));
    }
}
