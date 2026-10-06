package company.vk.edu.distrib.compute.near.kv;

import java.io.IOException;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.kv.RemoteDaoFactory;
import company.vk.edu.distrib.compute.kv.RemoteDaoFactoryTest;

@RemoteDaoFactoryTest
public final class NearRemoteDaoFactory implements RemoteDaoFactory<String> {
    private static final int SUPPORTED_NODE_COUNT = 1;

    @Override
    public Dao<String> create(int... ports) throws IOException {
        if (ports.length != SUPPORTED_NODE_COUNT) {
            throw new IllegalArgumentException("Expected exactly one KV port");
        }
        return new NearRemoteDao(ports[0]);
    }
}
