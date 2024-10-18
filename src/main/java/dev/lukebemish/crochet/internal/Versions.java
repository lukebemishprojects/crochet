package dev.lukebemish.crochet.internal;

import java.io.IOException;
import java.util.Properties;

public class Versions {
    public static final String TASK_GRAPH_RUNNER;
    public static final String DEV_LAUNCH;
    public static final String TERMINAL_CONSOLE_APPENDER;
    public static final String CHRISTEN;

    static {
        Properties properties = new Properties();
        try (var is = Versions.class.getResourceAsStream("/versions.properties")) {
            properties.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load versions.properties", e);
        }
        TASK_GRAPH_RUNNER = properties.getProperty("taskgraphrunner");
        DEV_LAUNCH = properties.getProperty("devlaunch");
        TERMINAL_CONSOLE_APPENDER = properties.getProperty("terminalconsoleappender");
        CHRISTEN = properties.getProperty("christen");
    }
}
