package org.gradle.profiler;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gradle.profiler.Logging.*;

public abstract class BuildInvoker {
    private final List<String> jvmArgs;
    private final List<String> gradleArgs;
    private final PidInstrumentation pidInstrumentation;
    private final Consumer<BuildInvocationResult> resultsConsumer;

    public BuildInvoker(List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer) {
        this.jvmArgs = jvmArgs;
        this.gradleArgs = gradleArgs;
        this.pidInstrumentation = pidInstrumentation;
        this.resultsConsumer = resultsConsumer;
    }

    public BuildInvocationResult runBuild(String displayName, List<String> tasks) {
        startOperation("Running " + displayName + " with tasks " + tasks);

        Timer timer = new Timer();
        run(tasks, gradleArgs, jvmArgs);
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        Logging.detailed().println("Used daemon with pid " + pid);
        Main.printExecutionTime(executionTime);

        BuildInvocationResult results = new BuildInvocationResult(displayName, executionTime, pid);
        resultsConsumer.accept(results);
        return results;
    }

    protected abstract void run(List<String> tasks, List<String> gradleArgs, List<String> jvmArgs);

    public static <T extends LongRunningOperation, R> R run(T operation, Function<T, R> function) {
        operation.setStandardOutput(Logging.detailed());
        operation.setStandardError(Logging.detailed());
        try {
            return function.apply(operation);
        } catch (GradleConnectionException e) {
            System.out.println();
            System.out.println("ERROR: failed to run build. See log file for details.");
            System.out.println();
            throw e;
        }
    }

    public BuildInvoker notInstrumented() {
        return copy(jvmArgs, gradleArgs, pidInstrumentation, buildInvocationResult -> { });
    }

    public BuildInvoker withJvmArgs(List<String> jvmArgs) {
        if (jvmArgs.equals(this.jvmArgs)) {
            return this;
        }
        return copy(jvmArgs, gradleArgs, pidInstrumentation, resultsConsumer);
    }

    public BuildInvoker withGradleArgs(List<String> gradleArgs) {
        if (gradleArgs.equals(this.gradleArgs)) {
            return this;
        }
        return copy(jvmArgs, gradleArgs, pidInstrumentation, resultsConsumer);
    }

    protected abstract BuildInvoker copy(List<String> jvmArgs, List<String> gradleArgs, PidInstrumentation pidInstrumentation, Consumer<BuildInvocationResult> resultsConsumer);
}
