package company.vk.edu.distrib.compute.rsmt98.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

final class BasicAuthentication {
    static final String CHALLENGE = "Basic realm=\"urlshortener\", charset=\"UTF-8\"";
    private static final Pattern AUTH_PATTERN =
            Pattern.compile(
                    "[ \\t]*Basic +([A-Za-z0-9+/]+={0,2})[ \\t]*", Pattern.CASE_INSENSITIVE);
    private final Dao<String> users;

    BasicAuthentication(Dao<String> users) {
        this.users = users;
    }

    boolean authenticate(Map<String, List<String>> headers) throws IOException {
        var values = headers.get("Authorization");
        if (values == null || values.size() != 1) {
            return false;
        }
        var matcher = AUTH_PATTERN.matcher(values.getFirst());
        if (!matcher.matches()) {
            return false;
        }

        final Credentials credentials;
        try {
            byte[] bytes = Base64.getDecoder().decode(matcher.group(1));
            String decoded =
                    StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            credentials = parseCredentials(decoded);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return false;
        }

        try {
            return credentials.password().equals(users.get(credentials.username()));
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    void register(String credentials) throws IOException {
        var parsed = parseCredentials(credentials);
        users.upsert(parsed.username(), parsed.password());
    }

    private static Credentials parseCredentials(String credentials) {
        int sep = credentials.indexOf(':');
        if (sep < 0) {
            throw new IllegalArgumentException(
                    "Credentials must contain a username and password separated by a colon");
        }
        int offset = 0;
        while (offset < credentials.length()) {
            int codePoint = credentials.codePointAt(offset);
            int characterType = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || characterType == Character.SURROGATE
                    || characterType == Character.LINE_SEPARATOR
                    || characterType == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException(
                        "Credentials must be one line without control characters");
            }
            offset += Character.charCount(codePoint);
        }
        return new Credentials(credentials.substring(0, sep), credentials.substring(sep + 1));
    }

    private record Credentials(String username, String password) {}
}
