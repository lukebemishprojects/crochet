package dev.lukebemish.crochet;

import dev.lukebemish.crochet.mapping.Mappings;
import dev.lukebemish.crochet.mapping.RemapTransform;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class CrochetExtension {
    private final Project project;

    private final Map<String, Configuration> mappingConfigurations = new HashMap<>();
    private final Map<String, Configuration> classpathConfigurations = new HashMap<>();

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
    }

    public Mappings mappings(String name) {
        var classpathConfig = mappingClasspathConfiguration(name);
        var mappingsConfig = mappingsConfiguration(name);
        project.getDependencies().registerTransform(RemapTransform.class, params -> {
            params.getFrom()
                .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.UNMAPPED))
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            params.getTo()
                .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, name))
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            params.getParameters().getMappings().from(mappingsConfig);
            params.getParameters().getMappingClasspath().from(classpathConfig);
        });
        return project.getObjects().named(Mappings.class, name);
    }

    public Configuration mappingClasspathConfiguration(Mappings mappings) {
        mappings(mappings.getName());
        return classpathConfigurations.get(mappings.getName());
    }

    public Configuration mappingsConfiguration(Mappings mappings) {
        mappings(mappings.getName());
        return mappingConfigurations.get(mappings.getName());
    }

    private synchronized Configuration mappingClasspathConfiguration(String name) {
        if (classpathConfigurations.containsKey(name)) {
            return classpathConfigurations.get(name);
        }

        Configuration mappingClasspath = project.getConfigurations().create("crochetMappingClasspath" + StringUtils.capitalize(name));
        mappingClasspath.setCanBeConsumed(false);
        mappingClasspath.setCanBeResolved(true);
        classpathConfigurations.put(name, mappingClasspath);

        return mappingClasspath;
    }

    private synchronized Configuration mappingsConfiguration(String name) {
        if (mappingConfigurations.containsKey(name)) {
            return mappingConfigurations.get(name);
        }

        Configuration mappings = project.getConfigurations().create("crochetMappings" + StringUtils.capitalize(name));
        mappings.setCanBeConsumed(false);
        mappings.setCanBeResolved(true);
        mappingConfigurations.put(name, mappings);

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
