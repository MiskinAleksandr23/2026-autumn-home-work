package company.vk.edu.distrib.compute.sovesti.urlshortener.route;

import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class RandomId implements Supplier<String> {

    private final Random random = new Random();
    private static final String ALPHANUM = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM1234567890";
    private static final int SIZE = 10;

    @Override
    public String get() {
        return Stream.generate(this::randomChar)
            .limit(SIZE)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();
    }

    String throwIfInvalid(String id) {
        if (SIZE != id.length() || !id.chars().allMatch(this::valid)) {
            throw new IllegalArgumentException("Invalid id: %s".formatted(id));
        }
        return id;
    }

    private boolean valid(int character) {
        return ALPHANUM.chars().anyMatch(valid -> character == valid);
    }

    private char randomChar() {
        return ALPHANUM.charAt(random.nextInt(ALPHANUM.length()));
    }
}
