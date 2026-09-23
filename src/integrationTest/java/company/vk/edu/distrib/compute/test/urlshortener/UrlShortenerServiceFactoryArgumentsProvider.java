package company.vk.edu.distrib.compute.test.urlshortener;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerTest;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class UrlShortenerServiceFactoryArgumentsProvider
    extends AbstractArgumentsProvider implements ArgumentsProvider {

    public UrlShortenerServiceFactoryArgumentsProvider() {
        super(
            AbstractArgumentsProvider.findAnnotatedFactories(UrlShortenerTest.class),
            AbstractHttpServiceFactory.class
        );
    }
}
