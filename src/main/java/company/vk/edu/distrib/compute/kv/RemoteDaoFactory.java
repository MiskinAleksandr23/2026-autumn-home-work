package company.vk.edu.distrib.compute.kv;

import java.io.IOException;

import company.vk.edu.distrib.compute.Dao;

@FunctionalInterface
public interface RemoteDaoFactory<T> {

    /**
     * Construct a remote Dao instance.
     *
     * @param ports one or more ports that a remote dao service uses
     * @return a remote dao instance
     */

    Dao<T> create(int... ports) throws IOException;
}
