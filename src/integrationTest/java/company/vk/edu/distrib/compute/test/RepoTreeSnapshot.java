package company.vk.edu.distrib.compute.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public enum RepoTreeSnapshot {
    ;

    private static final Set<String> IGNORED_TOP_LEVEL = Set.of(".git", ".gradle", ".idea", "build", "out");

    public static Path repoRoot() {
        final Path start = Path.of("").toAbsolutePath();
        Path dir = start;
        while (dir != null) {
            if (Files.exists(dir.resolve(".git"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return start;
    }

    public static Set<String> capture(Path root) {
        final Set<String> paths = new TreeSet<>();
        try (Stream<Path> topLevel = Files.list(root)) {
            for (final Path entry : topLevel.toList()) {
                if (IGNORED_TOP_LEVEL.contains(entry.getFileName().toString())) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    try (Stream<Path> subtree = Files.walk(entry)) {
                        subtree.forEach(path -> paths.add(root.relativize(path).toString()));
                    }
                } else {
                    paths.add(root.relativize(entry).toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to capture repository tree under " + root, e);
        }
        return paths;
    }
}
