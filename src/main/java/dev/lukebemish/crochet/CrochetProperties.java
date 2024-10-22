package dev.lukebemish.crochet;

public final class CrochetProperties {
    private CrochetProperties() {}

    public static final String TASKGRAPHRUNNER_LOG_LEVEL = "dev.lukebemish.crochet.taskgraphrunner.logging.level";
    public static final String TASKGRAPHRUNNER_REMOVE_ASSET_DURATION = "dev.lukebemish.crochet.taskgraphrunner.cache.remove-assets-after";
    public static final String TASKGRAPHRUNNER_REMOVE_OUTPUT_DURATION = "dev.lukebemish.crochet.taskgraphrunner.cache.remove-outputs-after";
    public static final String TASKGRAPHRUNNER_REMOVE_LOCK_DURATION = "dev.lukebemish.crochet.taskgraphrunner.cache.remove-locks-after";

    public static final String USE_STUB_GENERATED_MINECRAFT_DEPENDENCIES = "dev.lukebemish.crochet.dependencies.use-stub-generated-minecraft-dependencies";
    public static final String ADD_LIKELY_REPOSITORIES = "dev.lukebemish.crochet.dependencies.add-likely-repositories";
}
