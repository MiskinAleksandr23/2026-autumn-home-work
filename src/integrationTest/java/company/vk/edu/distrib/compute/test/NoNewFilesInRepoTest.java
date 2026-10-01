package company.vk.edu.distrib.compute.test;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Order(Integer.MAX_VALUE)
class NoNewFilesInRepoTest {

    @Test
    void noNewFilesOrDirectoriesInRepo() {
        final Set<String> created = new TreeSet<>(RepoTreeSnapshot.capture(RepoTreeSnapshot.repoRoot()));
        created.removeAll(RepoTreeSnapshotListener.baseline());
        assertTrue(
            created.isEmpty(),
            () -> "Tests must not create files or directories inside the repository, but found:\n"
                + String.join("\n", created));
    }
}
