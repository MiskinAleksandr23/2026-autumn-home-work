package company.vk.edu.distrib.compute.iizhukov.urlshortener.db;

import org.jspecify.annotations.Nullable;

public final class DataValidationUtils {
    private DataValidationUtils() {

    }

    public static void validateKey(@Nullable String key) {
        if (key == null
                || key.isEmpty()
                || key.chars().anyMatch(c -> !Character.isLetterOrDigit(c))
        ) {
            throw new IllegalArgumentException("invalid key");
        }
    }

    public static void validateUrl(@Nullable String url) {
        if (url == null
                || (
                        !url.startsWith("http://")
                        && !url.startsWith("https://")
                )
        ) {
            throw new IllegalArgumentException("invalid url");
        }
    }
}
