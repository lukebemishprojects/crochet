package dev.lukebemish.crochet;

import dev.lukebemish.crochet.mapping.Mappings;
import dev.lukebemish.crochet.mapping.RemapTransform;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;

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

    public void remap(Object dependency, Mappings mappings) {
        if (dependency instanceof ModuleDependency moduleDependency) {
            var original = moduleDependency.copy();
            moduleDependency.attributes(attributeContainer -> {
                attributeContainer.attribute(Mappings.MAPPINGS_ATTRIBUTE, mappings);
            });
            project.getDependencies().add(mappingClasspathConfiguration(mappings).getName(), original);
        } else {
            throw new IllegalArgumentException("Dependency must be a ModuleDependency");
        }
    }
}
