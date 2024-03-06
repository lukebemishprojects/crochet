package dev.lukebemish.crochet;

import dev.lukebemish.crochet.mapping.Mappings;
import dev.lukebemish.crochet.mapping.RemapTransform;
import dev.lukebemish.crochet.mapping.config.RemapParameters;
import dev.lukebemish.crochet.mapping.config.TinyRemapperConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class CrochetExtension {
    private static final String TINY_REMAPPER_MAVEN_NAME = "FabricMC Maven: Tiny-Remapper";

    private final Project project;

    private final Map<String, Configuration> mappingConfigurations = new HashMap<>();
    private final Map<String, Configuration> classpathConfigurations = new HashMap<>();

    public abstract Property<Action<RemapParameters>> getDefaultRemapConfiguration();

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
    }

    public void useTinyRemapper() {
        useTinyRemapper(it -> {});
    }

    public void useTinyRemapper(Action<TinyRemapperConfiguration> configurationAction) {
        TinyRemapperConfiguration configuration = project.getObjects().newInstance(TinyRemapperConfiguration.class);
        configurationAction.execute(configuration);

        if (project.getRepositories().named(s -> s.equals(TINY_REMAPPER_MAVEN_NAME)).isEmpty()) {
            project.getRepositories().maven(maven -> {
                maven.setUrl("https://maven.fabricmc.net/");
                maven.setName("FabricMC Maven: Tiny-Remapper");
                maven.content(content ->
                    content.includeModule("net.fabricmc", "tiny-remapper")
                );
            });
        }
        Configuration detached = project.getConfigurations().detachedConfiguration(
            project.getDependencies().create("dev.lukebemish.crochet.remappers:tiny-remapper:"+CrochetPlugin.VERSION)
        );
        detached.setVisible(false);
        detached.setCanBeConsumed(false);
        detached.setCanBeResolved(true);
        copyAttributes(detached, project.getConfigurations().maybeCreate(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        // Suppress warning to make the captured type FileCollection
        @SuppressWarnings("UnnecessaryLocalVariable") FileCollection files = detached;
        Action<RemapParameters> action = it -> {
            it.getClasspath().from(files);
            it.getMainClass().set("dev.lukebemish.crochet.remappers.tiny.TinyRemapperLauncher");
            it.getExtraArguments().addAll(configuration.makeExtraArguments());
        };
        getDefaultRemapConfiguration().set(action);
    }

    public Mappings mappings(String source, String target) {
        var action = getDefaultRemapConfiguration().orElse(it -> {});
        return mappings(source, target, it -> action.get().execute(it));
    }

    public Mappings mappings(String source, String target, Action<RemapParameters> action) {
        String name = Mappings.of(source, target);
        var classpathConfig = mappingClasspathConfiguration(name);
        var mappingsConfig = mappingsConfiguration(name);
        RemapParameters unconfiguredParams = project.getObjects().newInstance(RemapParameters.class);
        Provider<RemapParameters> remapperParameters = project.provider(() -> unconfiguredParams).map(it -> {
            action.execute(it);
            return it;
        });
        Action<TransformSpec<RemapTransform.Parameters>> configure = params -> {
            params.getParameters().getMappings().from(mappingsConfig.getIncoming().artifactView(config ->
                config.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, CrochetPlugin.TINY_ARTIFACT_TYPE)
            ).getFiles());
            params.getParameters().getMappingClasspath().from(classpathConfig);
            // TODO: fix this now resolving the configuration properly, and so leaving out subproject/task dependencies
            // params.getParameters().getMappingClasspath().builtBy(classpathConfig);
            params.getParameters().getRemapParameters().set(remapperParameters);
        };
        project.getDependencies().registerTransform(RemapTransform.class, params -> {
            params.getFrom()
                .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.UNMAPPED))
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            params.getTo()
                .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, name))
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            configure.execute(params);
        });
        project.getDependencies().registerTransform(RemapTransform.class, params -> {
            params.getFrom()
                .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, source))
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            params.getTo()
                .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, target))
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            configure.execute(params);
        });
        return project.getObjects().named(Mappings.class, name);
    }

    public Configuration mappingClasspathConfiguration(Mappings mappings) {
        mappings(Mappings.source(mappings), Mappings.target(mappings));
        return classpathConfigurations.get(mappings.getName());
    }

    public Configuration mappingsConfiguration(Mappings mappings) {
        mappings(Mappings.source(mappings), Mappings.target(mappings));
        return mappingConfigurations.get(mappings.getName());
    }

    private synchronized Configuration mappingClasspathConfiguration(String mappingsName) {
        String name = Mappings.source(mappingsName) + "To" + StringUtils.capitalize(Mappings.target(mappingsName));
        if (classpathConfigurations.containsKey(mappingsName)) {
            return classpathConfigurations.get(mappingsName);
        }

        Configuration mappingClasspath = project.getConfigurations().create("crochetMappingClasspath" + StringUtils.capitalize(name));
        mappingClasspath.setCanBeConsumed(false);
        mappingClasspath.setCanBeResolved(true);
        classpathConfigurations.put(mappingsName, mappingClasspath);

        return mappingClasspath;
    }

    private synchronized Configuration mappingsConfiguration(String mappingsName) {
        String name = Mappings.source(mappingsName) + "To" + StringUtils.capitalize(Mappings.target(mappingsName));
        if (mappingConfigurations.containsKey(mappingsName)) {
            return mappingConfigurations.get(mappingsName);
        }

        Configuration mappings = project.getConfigurations().create("crochetMappings" + StringUtils.capitalize(name));
        mappings.setCanBeConsumed(false);
        mappings.setCanBeResolved(true);
        mappingConfigurations.put(mappingsName, mappings);

        return mappings;
    }

    public void remap(Configuration configuration, Mappings mappings) {
        configuration.getAttributes().attribute(Mappings.MAPPINGS_ATTRIBUTE, mappings);
        mappingClasspathConfiguration(mappings).extendsFrom(configuration);
    }

    public void extraConfigurations(String prefix) {
        var localRuntime = withSuffix(prefix, CrochetPlugin.LOCAL_RUNTIME_CONFIGURATION_NAME);
        localRuntime.setVisible(false);
        localRuntime.setCanBeConsumed(false);
        localRuntime.setCanBeResolved(false);

        var runtimeClasspath = withSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        runtimeClasspath.extendsFrom(localRuntime);
    }

    public void setupConfigurations(String prefix, Mappings mappings, String suffix) {
        var oApiElements = withSuffix(prefix, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME);
        var oRuntimeElements = withSuffix(prefix, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME);
        var oCompileClasspath = withSuffix(prefix, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        var oRuntimeClasspath = withSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        var oAnnotationProcessor = withSuffix(prefix, JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

        var api = createWithSuffix(prefix, JavaPlugin.API_CONFIGURATION_NAME, suffix, false, false, true);
        var implementation = createWithSuffix(prefix, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, suffix, false, false, true);
        var compileOnly = createWithSuffix(prefix, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, suffix, false, false, true);
        var runtimeOnly = createWithSuffix(prefix, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, suffix, false, false, true);
        var compileOnlyApi = createWithSuffix(prefix, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME, suffix, false, false, true);
        var annotationProcessor = createWithSuffix(prefix, JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, suffix, true, false, true);
        var localRuntime = createWithSuffix(prefix, CrochetPlugin.LOCAL_RUNTIME_CONFIGURATION_NAME, suffix, false, false, true);

        var runtimeClasspathCollector = createWithSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME + "Collector", suffix, true, false, false);
        var compileClasspathCollector = createWithSuffix(prefix, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME + "Collector", suffix, true, false, false);

        runtimeClasspathCollector.extendsFrom(api);
        runtimeClasspathCollector.extendsFrom(implementation);
        runtimeClasspathCollector.extendsFrom(runtimeOnly);
        runtimeClasspathCollector.extendsFrom(localRuntime);

        compileClasspathCollector.extendsFrom(api);
        compileClasspathCollector.extendsFrom(implementation);
        compileClasspathCollector.extendsFrom(compileOnly);
        compileClasspathCollector.extendsFrom(compileOnlyApi);

        var runtimeClasspathRemapped = createWithSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, suffix, false, false, true);
        var compileClasspathRemapped = createWithSuffix(prefix, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, suffix, false, false, true);

        project.getDependencies()
            .add(runtimeClasspathRemapped.getName(), lazyOutput(runtimeClasspathCollector));

        project.getDependencies()
            .add(compileClasspathRemapped.getName(), lazyOutput(compileClasspathCollector));

        project.getDependencies()
            .add(oAnnotationProcessor.getName(), lazyOutput(annotationProcessor));

        oRuntimeClasspath.extendsFrom(runtimeClasspathRemapped);
        oCompileClasspath.extendsFrom(compileClasspathRemapped);

        copyAttributes(compileClasspathCollector, oCompileClasspath);
        copyAttributes(runtimeClasspathCollector, oRuntimeClasspath);
        copyAttributes(annotationProcessor, oAnnotationProcessor);

        remap(runtimeClasspathCollector, mappings);
        remap(compileClasspathCollector, mappings);
        remap(annotationProcessor, mappings);

        oApiElements.extendsFrom(api, compileOnlyApi);
        oRuntimeElements.extendsFrom(implementation, runtimeOnly);
    }

    private FileCollection lazyOutput(Configuration configuration) {
        var resolveTask = project.getTasks().register(configuration.getName() + "Resolve", task ->
            task.dependsOn(configuration)
        );

        var files = project.files(project.provider(configuration::resolve));
        files.builtBy(resolveTask);
        return files;
    }

    private Configuration withSuffix(String prefix, String suffix) {
        var full = prefix.isEmpty() ? suffix : prefix + StringUtils.capitalize(suffix);
        return project.getConfigurations().maybeCreate(full);
    }

    @SuppressWarnings("UnstableApiUsage")
    private Configuration createWithSuffix(String prefix, String suffix, String andSuffix, boolean resolvable, boolean consumable, boolean declarable) {
        var full = prefix.isEmpty() ? suffix : prefix + StringUtils.capitalize(suffix);
        full += StringUtils.capitalize(andSuffix);
        var config = project.getConfigurations().maybeCreate(full);

        config.setVisible(false);
        config.setCanBeConsumed(consumable);
        config.setCanBeResolved(resolvable);
        config.setCanBeDeclared(declarable);

        return config;
    }

    private void copyAttributes(Configuration configuration, Configuration target) {
        for (var attr : target.getAttributes().keySet()) {
            copyAttribute(configuration, target, attr);
        }
    }

    private <T> void copyAttribute(Configuration configuration, Configuration target, Attribute<T> attr) {
        var value = target.getAttributes().getAttribute(attr);
        if (value != null) {
            configuration.getAttributes().attribute(attr, value);
        }
    }
}
