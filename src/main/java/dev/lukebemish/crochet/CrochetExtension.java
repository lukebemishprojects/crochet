package dev.lukebemish.crochet;

import dev.lukebemish.crochet.mapping.Mappings;
import dev.lukebemish.crochet.mapping.MappingsConfiguration;
import dev.lukebemish.crochet.mapping.RemapTransform;
import dev.lukebemish.crochet.mapping.config.RemapParameters;
import dev.lukebemish.crochet.mapping.config.TinyRemapperConfiguration;
import dev.lukebemish.crochet.tasks.RemapJarTask;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class CrochetExtension {
    private static final String TINY_REMAPPER_MAVEN_NAME = "FabricMC Maven: Tiny-Remapper";

    private final Project project;

    private final Map<String, Configuration> mappingConfigurations = new HashMap<>();

    private final Map<String, Provider<RemapParameters>> remapParameters = new HashMap<>();
    private final Map<String, MappingsConfiguration> configuredMappings = new HashMap<>();

    private final TaskProvider<Task> idePostSync;

    public abstract Property<Action<RemapParameters>> getDefaultRemapConfiguration();

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
        this.idePostSync = project.getTasks().register("crochetIdeSetup");
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
        Configuration tinyRemapperConfiguration = project.getConfigurations().maybeCreate("crochetTinyRemapper");
        project.getDependencies().add(tinyRemapperConfiguration.getName(), "dev.lukebemish.crochet.remappers:tiny-remapper:"+CrochetPlugin.VERSION);
        tinyRemapperConfiguration.setVisible(false);
        tinyRemapperConfiguration.setCanBeConsumed(false);
        tinyRemapperConfiguration.setCanBeResolved(true);
        copyExternalAttributes(tinyRemapperConfiguration, project.getConfigurations().maybeCreate(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        // Suppress warning to make the captured type FileCollection
        @SuppressWarnings("UnnecessaryLocalVariable") FileCollection files = tinyRemapperConfiguration;
        Action<RemapParameters> action = it -> {
            it.getClasspath().from(tinyRemapperConfiguration);
            it.getMainClass().set("dev.lukebemish.crochet.remappers.tiny.TinyRemapperLauncher");
            it.getExtraArguments().addAll(configuration.makeExtraArguments());
        };
        getDefaultRemapConfiguration().set(action);
    }

    public Mappings mappings(String source, String target) {
        return mappings(source, target, it -> {});
    }

    public Mappings mappings(String source, String target, Action<MappingsConfiguration> action) {
        String name = Mappings.of(source, target);

        if (configuredMappings.containsKey(name)) {
            action.execute(configuredMappings.get(name));
            return project.getObjects().named(Mappings.class, name);
        }

        var mappings = project.getObjects().named(Mappings.class, name);

        MappingsConfiguration config = project.getObjects().newInstance(MappingsConfiguration.class, mappings, this, project);
        action.execute(config);
        config.getRemapping().convention(getDefaultRemapConfiguration().orElse(it -> {}));
        configuredMappings.put(name, config);

        var mappingsConfig = mappingsConfiguration(name);
        mappingsConfig.getDependencies().addAllLater(config.getMappings().getDependencies());

        var remappingAction = config.getRemapping();
        Provider<RemapParameters> remapperParameters = project.provider(() -> {
            RemapParameters instance = project.getObjects().newInstance(RemapParameters.class);
            remappingAction.get().execute(instance);
            instance.getMappings().from(mappingsConfig);
            return instance;
        });
        remapParameters.put(name, remapperParameters);

        Action<TransformSpec<RemapTransform.Parameters>> configure = params -> {
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

        return mappings;
    }

    public Configuration mappingsConfiguration(Mappings mappings) {
        mappings(Mappings.source(mappings), Mappings.target(mappings));
        return mappingConfigurations.get(mappings.getName());
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
    }

    public void extraConfigurations(String prefix) {
        var localRuntime = withSuffix(prefix, CrochetPlugin.LOCAL_RUNTIME_CONFIGURATION_NAME);
        localRuntime.setVisible(false);
        localRuntime.setCanBeConsumed(false);
        localRuntime.setCanBeResolved(false);

        var runtimeClasspath = withSuffix(prefix, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        runtimeClasspath.extendsFrom(localRuntime);
    }

    public void remapOutgoing(SourceSet sourceSet, Mappings mappings) {
        String prefix = SourceSet.isMain(sourceSet) ? "" : sourceSet.getName();
        var runtimeClasspath = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
        var runtimeElements = project.getConfigurations().getByName(sourceSet.getRuntimeElementsConfigurationName());

        var namedElements = project.getConfigurations().create("named"+StringUtils.capitalize(prefix)+"Elements");
        namedElements.setCanBeDeclared(false);
        namedElements.setCanBeConsumed(true);
        namedElements.setCanBeResolved(false);

        namedElements.getDependencies().addAllLater(project.provider(() -> runtimeElements.getIncoming().getDependencies()));

        copyExternalAttributes(namedElements, runtimeElements);
        namedElements.attributes(attributes -> {
            Mappings runtimeClasspathMappings = runtimeClasspath.getAttributes().getAttribute(Mappings.MAPPINGS_ATTRIBUTE);
            if (runtimeClasspathMappings != null) {
                attributes.attribute(Mappings.MAPPINGS_ATTRIBUTE, runtimeClasspathMappings);
            }
        });
        runtimeElements.getOutgoing().getVariants().forEach(configurationVariant -> {
            configurationVariant.attributes(attributes -> {
                attributes.attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.source(mappings)));
            });
            namedElements.getOutgoing().getVariants().add(configurationVariant);
        });
        runtimeElements.getOutgoing().getVariants().clear();

        TaskProvider<Jar> jarTask = project.getTasks().named(nameWithSuffix(prefix, JavaPlugin.JAR_TASK_NAME), Jar.class);
        jarTask.configure(task -> {
            task.getArchiveClassifier().set(prefix.isEmpty() ? "dev" : prefix + "-dev");
        });
        var remapJarTask = project.getTasks().register("remap"+StringUtils.capitalize(prefix)+"Jar", RemapJarTask.class, task -> {
            //task.getRemapParameters().set(remapParameters.get(Mappings.of(Mappings.source(mappings), Mappings.target(mappings))));
            task.from(project.zipTree(jarTask.get().getArchiveFile()).matching(pattern -> {
                pattern.exclude("META-INF/MANIFEST.MF");
            }));
            task.dependsOn(jarTask);
            task.manifest(manifest -> {
                manifest.from(jarTask.get().getManifest());
            });
            task.getArchiveClassifier().set(prefix);
        });
        runtimeElements.getArtifacts().forEach(artifact -> {
            namedElements.getArtifacts().add(artifact);
        });
        runtimeElements.getArtifacts().clear();
        project.artifacts(artifacts -> {
            artifacts.add(runtimeElements.getName(), remapJarTask.flatMap(RemapJarTask::getArchiveFile), artifact -> {
                artifact.builtBy(remapJarTask);
            });
        });
        project.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME).configure(task -> {
            task.dependsOn(remapJarTask);
        });
    }

    private Configuration withSuffix(String prefix, String suffix) {
        var full = prefix.isEmpty() ? suffix : prefix + StringUtils.capitalize(suffix);
        return project.getConfigurations().maybeCreate(full);
    }

    private String nameWithSuffix(String prefix, String suffix) {
        return prefix.isEmpty() ? suffix : prefix + StringUtils.capitalize(suffix);
    }

    private void copyExternalAttributes(Configuration to, Configuration from) {
        for (var attr : from.getAttributes().keySet()) {
            if (attr.getName().equals(Mappings.MAPPINGS_ATTRIBUTE.getName())) {
                continue;
            }
            copyAttribute(to, from, attr);
        }
    }

    private <T> void copyAttribute(Configuration to, Configuration from, Attribute<T> attr) {
        var value = from.getAttributes().getAttribute(attr);
        if (value != null) {
            to.getAttributes().attribute(attr, value);
        }
    }
}
