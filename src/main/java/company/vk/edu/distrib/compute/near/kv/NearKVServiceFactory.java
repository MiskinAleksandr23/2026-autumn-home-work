package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import company.vk.edu.distrib.compute.AbstractHttpServiceFactory;
import company.vk.edu.distrib.compute.kv.KVService;
import company.vk.edu.distrib.compute.kv.KVServiceTest;
import company.vk.edu.distrib.compute.kv.ReplicatedKVServiceTest;

@KVServiceTest
@ReplicatedKVServiceTest
public final class NearKVServiceFactory extends AbstractHttpServiceFactory<KVService> {
    private static final int SINGLE_NODE_COUNT = 1;

    private final int[] configuredPorts;
    private final int configuredReplicas;

    public NearKVServiceFactory() {
        super();
        configuredPorts = new int[0];
        configuredReplicas = 0;
    }

    public NearKVServiceFactory(int[] clusterPorts, int replicas) {
        super();
        configuredPorts = clusterPorts.clone();
        configuredReplicas = replicas;
        if (clusterPorts.length == 0) {
            throw new IllegalArgumentException("Expected cluster ports");
        }
    }

    @Override
    protected KVService doCreate(int port) throws IOException {
        int[] clusterPorts = clusterPorts(port);
        int replicas = replicationFactor(clusterPorts.length);
        String dataDirectory = System.getenv("KV_DATA_DIR");
        Path directory = dataDirectory == null || dataDirectory.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir"), "near-kv", Integer.toString(port))
            : Path.of(dataDirectory);
        if (dataDirectory != null && !dataDirectory.isBlank() && clusterPorts.length > SINGLE_NODE_COUNT) {
            directory = directory.resolve(Integer.toString(port));
        }
        String threadCount = System.getenv("KV_THREADS");
        int threads = threadCount == null ? 1 : Integer.parseInt(threadCount);
        String clusterSetting = System.getenv("KV_CLUSTER_PORTS");
        if (configuredPorts.length == 0 && (clusterSetting == null || clusterSetting.isBlank())
            && System.getenv("KV_REPLICATION_FACTOR") == null) {
            return new NearKVService(port, new FileDao(directory), threads);
        }
        return new NearKVService(port, new FileDao(directory), threads, clusterPorts, replicas);
    }

    private int[] clusterPorts(int port) {
        if (configuredPorts.length != 0) {
            return configuredPorts.clone();
        }
        String setting = System.getenv("KV_CLUSTER_PORTS");
        if (setting == null || setting.isBlank()) {
            return new int[]{port};
        }
        return Arrays.stream(setting.split(",")).map(String::strip).mapToInt(Integer::parseInt).toArray();
    }

    private int replicationFactor(int clusterSize) {
        if (configuredPorts.length != 0) {
            return configuredReplicas;
        }
        String setting = System.getenv("KV_REPLICATION_FACTOR");
        return setting == null ? clusterSize : Integer.parseInt(setting);
    }
}
