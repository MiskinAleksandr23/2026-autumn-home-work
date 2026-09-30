package company.vk.edu.distrib.compute.sovesti.urlshortener.dao;

import java.io.IOException;

import company.vk.edu.distrib.compute.Dao;

public sealed interface DaoOperation {

    String UPSERT = "UPSERT";
    String DELETE = "DELETE";

    String label();

    void execute(Dao<String> dao) throws IOException;

    String serialized();

    record Upsert(String key, String value) implements DaoOperation {

        @Override
        public String label() {
            return UPSERT;
        }

        @Override
        public void execute(Dao<String> dao) throws IOException {
            dao.upsert(key, value);
        }

        @Override
        public String serialized() {
            return new KeyValuePair(key, value).raw();
        }

    }

    record Delete(String key) implements DaoOperation {

        @Override
        public String label() {
            return DELETE;
        }

        @Override
        public void execute(Dao<String> dao) throws IOException {
            dao.delete(key);
        }

        @Override
        public String serialized() {
            return key;
        }

    }
}
