package company.vk.edu.distrib.compute.near.kv;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class RendezvousHash {
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int SINGLE_NODE_COUNT = 1;

    private final int[] ports;

    RendezvousHash(int... ports) {
        if (ports.length == 0) {
            throw new IllegalArgumentException("Expected at least one KV port");
        }
        this.ports = ports.clone();
        Arrays.sort(this.ports);
        for (int i = 0; i < this.ports.length; i++) {
            int port = this.ports[i];
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException("Port out of range");
            }
            if (i > 0 && port == this.ports[i - 1]) {
                throw new IllegalArgumentException("Duplicate KV port");
            }
        }
    }

    int size() {
        return ports.length;
    }

    boolean contains(int port) {
        return Arrays.binarySearch(ports, port) >= 0;
    }

    int owner(String key) {
        return ports[ownerIndex(key)];
    }

    int[] replicas(String key, int count) {
        int owner = ownerIndex(key);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = ports[(owner + i) % ports.length];
        }
        return result;
    }

    private int ownerIndex(String key) {
        if (ports.length == SINGLE_NODE_COUNT) {
            return 0;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            int owner = 0;
            long bestScore = 0;
            for (int i = 0; i < ports.length; i++) {
                digest.update(keyBytes);
                digest.update((byte) (ports[i] >>> Byte.SIZE));
                digest.update((byte) ports[i]);
                long score = ByteBuffer.wrap(digest.digest()).getLong();
                if (i == 0 || Long.compareUnsigned(score, bestScore) > 0) {
                    owner = i;
                    bestScore = score;
                }
            }
            return owner;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
