package company.vk.edu.distrib.compute.sovesti.urlshortener.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Stream;

import company.vk.edu.distrib.compute.Dao;

final class DaoOperations {

    private final Collection<DaoOperation> operations = new ConcurrentLinkedQueue<>();
    private final Map<String, Function<String, DaoOperation>> parseable = Map.of(//
        DaoOperation.UPSERT, this::parseUpsert, //
        DaoOperation.DELETE, DaoOperation.Delete::new);

    void fill(Stream<String> raw) {
        raw //
            .map(KeyValuePair::new) //
            .filter(KeyValuePair::valid) //
            .map(this::parse) //
            .flatMap(Optional::stream) //
            .forEach(this::add);
    }

    void add(DaoOperation operation) {
        operations.add(operation);
    }

    void execute(Dao<String> dao) throws IOException {
        for (DaoOperation op : new ArrayList<>(operations)) {
            op.execute(dao);
        }
    }

    private Optional<DaoOperation> parse(KeyValuePair row) {
        return Optional.ofNullable(parseable.get(row.key())).map(parse -> parse.apply(row.value()));
    }

    private DaoOperation parseUpsert(String raw) {
        KeyValuePair pair = new KeyValuePair(raw);
        return new DaoOperation.Upsert(pair.key(), pair.value());
    }
}
