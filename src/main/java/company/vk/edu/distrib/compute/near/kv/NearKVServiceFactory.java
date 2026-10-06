package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.nio.file.Path;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.kv.KVService;
import company.vk.edu.distrib.compute.kv.KVServiceTest;

@KVServiceTest
public final class NearKVServiceFactory extends AbstractHttpServiceFactory<KVService> {
    @Override
    protected KVService doCreate(int port) throws IOException {
        String dataDirectory = System.getenv("KV_DATA_DIR");
        Path directory = dataDirectory == null || dataDirectory.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir"), "near-kv", Integer.toString(port))
            : Path.of(dataDirectory);
        String threadCount = System.getenv("KV_THREADS");
        int threads = threadCount == null ? 1 : Integer.parseInt(threadCount);
        return new NearKVService(port, new FileDao(directory), threads);
    }
}
