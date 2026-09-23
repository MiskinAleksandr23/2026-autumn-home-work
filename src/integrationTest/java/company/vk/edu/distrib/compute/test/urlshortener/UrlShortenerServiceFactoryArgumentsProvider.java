package company.vk.edu.distrib.compute.test.urlshortener;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.near.urlshortener.NearUrlShortenerServiceFactory;
import company.vk.edu.distrib.compute.urlshortener.UrlShortenerService;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;
import org.junit.platform.commons.util.ReflectionUtils;

public class UrlShortenerServiceFactoryArgumentsProvider implements ArgumentsProvider {

    private final Collection<Class<? extends AbstractHttpServiceFactory<? extends UrlShortenerService>>> factories =
        List.of(
            NearUrlShortenerServiceFactory.class
        );

    @Override
    public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context) {
        return factories.stream()
                .map(ReflectionUtils::newInstance)
                .map(Arguments::of);
    }
}
