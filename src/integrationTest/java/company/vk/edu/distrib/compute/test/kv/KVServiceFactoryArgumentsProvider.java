package company.vk.edu.distrib.compute.test.kv;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.kv.KVServiceTest;
import company.vk.edu.distrib.compute.test.AbstractArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class KVServiceFactoryArgumentsProvider
    extends AbstractArgumentsProvider implements ArgumentsProvider {

    public KVServiceFactoryArgumentsProvider() {
        super(
            AbstractArgumentsProvider.findAnnotatedFactories(KVServiceTest.class),
            AbstractHttpServiceFactory.class
        );
    }
}
