package company.vk.edu.distrib.compute.test.urlshortener;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerAuthTest;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class AuthenticatedUrlShortenerServiceFactoryArgumentsProvider
    extends AbstractArgumentsProvider implements ArgumentsProvider {

    public AuthenticatedUrlShortenerServiceFactoryArgumentsProvider() {
        super(
            AbstractArgumentsProvider.findAnnotatedFactories(UrlShortenerAuthTest.class),
            AbstractHttpServiceFactory.class
        );
    }
}
