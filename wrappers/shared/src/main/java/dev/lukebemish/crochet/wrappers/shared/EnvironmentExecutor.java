package dev.lukebemish.crochet.wrappers.shared;

public final class EnvironmentExecutor {
    private EnvironmentExecutor() {}
    private static final boolean STACKTRACE = !Boolean.getBoolean("dev.lukebemish.crochet.wrappers.hidestacktrace");

    public static void execute(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            logException(t);
        }
    }

    private static void logException(Throwable t) {
        if (STACKTRACE) {
            t.printStackTrace(System.err);
        } else {
            System.err.println(t);
        }
    }
}
