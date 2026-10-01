package company.vk.edu.distrib.compute.test;

import java.util.Set;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class RepoTreeSnapshotListener implements TestExecutionListener {
    private static volatile Set<String> baseline;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        baseline = RepoTreeSnapshot.capture(RepoTreeSnapshot.repoRoot());
    }

    public static Set<String> baseline() {
        final Set<String> snapshot = baseline;
        if (snapshot == null) {
            throw new IllegalStateException("Repository tree baseline was not captured");
        }
        return snapshot;
    }
}
