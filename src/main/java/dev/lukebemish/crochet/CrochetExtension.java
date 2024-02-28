package dev.lukebemish.crochet;

import dev.lukebemish.crochet.mapping.Mappings;
import dev.lukebemish.crochet.mapping.RemapTransform;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
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
        mappingClasspathConfiguration(name);
        mappingsConfiguration(name);
        return project.getObjects().named(Mappings.class, name);
    }

    public Configuration mappingClasspathConfiguration(Mappings name) {
        return mappingClasspathConfiguration(name.getName());
    }

    public Configuration mappingsConfiguration(Mappings mappings) {
        return mappingsConfiguration(mappings.getName());
    }

    public synchronized Configuration mappingClasspathConfiguration(String name) {
        if (classpathConfigurations.containsKey(name)) {
            return classpathConfigurations.get(name);
        }

        Configuration mappingClasspath = project.getConfigurations().create("crochetMappingClasspath" + StringUtils.capitalize(name));
        mappingClasspath.setCanBeConsumed(false);
        mappingClasspath.setCanBeResolved(true);
        classpathConfigurations.put(name, mappingClasspath);

        mappingsConfiguration(name);

        return mappingClasspath;
    }

    public synchronized Configuration mappingsConfiguration(String name) {
        if (mappingConfigurations.containsKey(name)) {
            return mappingConfigurations.get(name);
        }

        Configuration mappings = project.getConfigurations().create("crochetMappings" + StringUtils.capitalize(name));
        mappings.setCanBeConsumed(false);
        mappings.setCanBeResolved(true);
        mappingConfigurations.put(name, mappings);

        project.getDependencies().registerTransform(RemapTransform.class, params -> {
            params.getFrom().attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.UNMAPPED));
            params.getTo().attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, name));
            params.getParameters().getMappings().from(mappings);
            Configuration mappingClasspath = mappingClasspathConfiguration(name);
            params.getParameters().getMappingClasspath().from(mappingClasspath);
        });

        return mappings;
    }

    public void remap(Configuration configuration, Mappings mappings) {
        configuration.getAttributes().attribute(Mappings.MAPPINGS_ATTRIBUTE, mappings);
        mappingClasspathConfiguration(mappings).extendsFrom(configuration);
    }

    public void setupConfigurations(String prefix, Mappings mappings, String suffix) {
        var apiElements = withSuffix(prefix, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME);
        var runtimeElements = withSuffix(prefix, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME);
        var compileClasspath = withSuffix(prefix, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        var runtimeClasspath = withSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

        var oApi = withSuffix(prefix, JavaPlugin.API_CONFIGURATION_NAME);
        var oImplementation = withSuffix(prefix, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
        var oCompileOnly = withSuffix(prefix, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
        var oRuntimeOnly = withSuffix(prefix, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME);
        var oCompileOnlyApi = withSuffix(prefix, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME);

        var api = withSuffix(prefix, JavaPlugin.API_CONFIGURATION_NAME, suffix);
        var implementation = withSuffix(prefix, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, suffix);
        var compileOnly = withSuffix(prefix, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, suffix);
        var runtimeOnly = withSuffix(prefix, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, suffix);
        var compileOnlyApi = withSuffix(prefix, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME, suffix);

        var runtimeClasspathRemapped = withSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, suffix);
        var compileClasspathRemapped = withSuffix(prefix, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, suffix);

        project.getDependencies().add(runtimeClasspathRemapped.getName(), lazyOutput(api));
        project.getDependencies().add(runtimeClasspathRemapped.getName(), lazyOutput(implementation));
        project.getDependencies().add(runtimeClasspathRemapped.getName(), lazyOutput(runtimeOnly));

        project.getDependencies().add(compileClasspathRemapped.getName(), lazyOutput(api));
        project.getDependencies().add(compileClasspathRemapped.getName(), lazyOutput(implementation));
        project.getDependencies().add(compileClasspathRemapped.getName(), lazyOutput(compileOnly));
        project.getDependencies().add(compileClasspathRemapped.getName(), lazyOutput(compileOnlyApi));

        runtimeClasspath.extendsFrom(runtimeClasspathRemapped);
        compileClasspath.extendsFrom(compileClasspathRemapped);

        copyAttributes(api, oApi);
        copyAttributes(implementation, oImplementation);
        copyAttributes(compileOnly, oCompileOnly);
        copyAttributes(runtimeOnly, oRuntimeOnly);
        copyAttributes(compileOnlyApi, oCompileOnlyApi);

        remap(api, mappings);
        remap(implementation, mappings);
        remap(compileOnly, mappings);
        remap(runtimeOnly, mappings);
        remap(compileOnlyApi, mappings);

        apiElements.extendsFrom(api, compileOnlyApi);
        runtimeElements.extendsFrom(implementation, runtimeOnly);
    }

    private FileCollection lazyOutput(Configuration configuration) {
        return project.files(project.provider(configuration::resolve));
    }

    private Configuration withSuffix(String prefix, String suffix) {
        var full = prefix.isEmpty() ? suffix : prefix + StringUtils.capitalize(suffix);
        return project.getConfigurations().maybeCreate(full);
    }

    private Configuration withSuffix(String prefix, String suffix, String andSuffix) {
        var full = prefix.isEmpty() ? suffix : prefix + StringUtils.capitalize(suffix);
        full += StringUtils.capitalize(andSuffix);
        return project.getConfigurations().maybeCreate(full);
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
