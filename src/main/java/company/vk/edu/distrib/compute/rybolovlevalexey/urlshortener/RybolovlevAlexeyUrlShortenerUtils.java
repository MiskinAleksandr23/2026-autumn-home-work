package company.vk.edu.distrib.compute.rybolovlevalexey.urlshortener;

import java.net.URI;
import java.net.URISyntaxException;

public final class RybolovlevAlexeyUrlShortenerUtils {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH_CORRECT_LINK_ID = 10;

    private RybolovlevAlexeyUrlShortenerUtils() {
    }

    public static void validateLinkID(String linkID) throws IllegalArgumentException {
        if (linkID == null) {
            throw new IllegalArgumentException("key can not be null");
        }
        if (linkID.length() != LENGTH_CORRECT_LINK_ID) {
            throw new IllegalArgumentException("invalid length " + linkID.length() + " of key - " + linkID);
        }
        int i = 0;
        while (i < linkID.length()) {
            var value = linkID.charAt(i);
            if (CHARS.indexOf(value) == -1) {
                throw new IllegalArgumentException("contains not allowed char");
            }
            i++;
        }
    }

    public static void validateLink(String link) {
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("key can not be null");
        }
        try {
            URI uri = new URI(link);
            if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("not allowed url syntax");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not allowed url syntax", e);
        }
    }
}
