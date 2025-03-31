package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.model.InstallationDistribution;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ConfigurationUtils implements BuildService<ConfigurationUtils.Params>, AutoCloseable {
    @Inject
    public ConfigurationUtils() {}

    enum ValidationMode {
        GENERATE,
        VALIDATE
    }

    interface Params extends BuildServiceParameters {
        Property<ValidationMode> getValidationMode();
        Property<String> getValidationPath();
        Property<Boolean> getValidationEnabled();
    }

    @Inject
    protected abstract Problems getProblems();

    private final Map<String, Set<String>> classesFromConfiguration = new HashMap<>();
    private final Map<String, Map<String, ConfigurationRole>> configurationRoles = new HashMap<>();
    private boolean setup = false;

    private final Map<String, Map<String, ConfigurationRole>> missing = new HashMap<>();

    @Override
    public synchronized void close() throws IOException {
        if (getParameters().getValidationEnabled().get() && getParameters().getValidationMode().get() == ValidationMode.GENERATE && !missing.isEmpty()) {
            var prefix = new ArrayList<String>();
            var existingLines = new LinkedHashMap<String, List<String>>();
            var path = Path.of(getParameters().getValidationPath().get());
            var originalLines = Files.readAllLines(path);
            String className = null;
            var partialPrefix = new ArrayList<String>();
            for (var line : originalLines) {
                if (line.startsWith("## ")) {
                    if (className == null) {
                        prefix.addAll(partialPrefix);
                    } else {
                        existingLines.put(className, new ArrayList<>(partialPrefix));
                    }
                    partialPrefix.clear();
                    className = line.substring(3).trim();
                }
                partialPrefix.add(line);
            }
            if (className != null) {
                existingLines.put(className, new ArrayList<>(partialPrefix));
            } else {
                prefix.addAll(partialPrefix);
            }
            for (var entry : missing.entrySet()) {
                className = entry.getKey();
                var roles = entry.getValue();
                var lines = existingLines.get(className);
                if (lines == null) {
                    lines = new ArrayList<>();
                    lines.add("## "+className);
                    lines.add("");
                    existingLines.put(className, lines);
                }
                for (var roleEntry : roles.entrySet()) {
                    var configurationName = roleEntry.getKey();
                    var role = roleEntry.getValue();
                    lines.add("### `"+configurationName+"`");
                    lines.add("`"+role.value()+"` (TODO: document extendsFrom)");
                    lines.add("");
                    lines.add("TODO: document purpose, attributes, etc.");
                    lines.add("");
                }
            }
            var newLines = new ArrayList<>(prefix);
            for (var entry : existingLines.entrySet()) {
                newLines.addAll(entry.getValue());
            }
            Files.write(Path.of(getParameters().getValidationPath().get()), newLines);
        }
    }

    private synchronized void setup() {
        if (setup) {
            return;
        }
        setup = true;
        if (!getParameters().getValidationEnabled().get()) {
            return;
        }
        try {
            var path = Path.of(getParameters().getValidationPath().get());
            var lines = Files.readAllLines(path);
            String className = null;
            String configurationName = null;
            boolean nextIsRole = false;
            for (var line : lines) {
                if (line.startsWith("## ")) {
                    className = line.substring(3).trim();
                    nextIsRole = false;
                } else if (className != null && line.startsWith("### `")) {
                    var firstIndex = line.indexOf('`');
                    var lastIndex = line.lastIndexOf('`');
                    if (firstIndex != lastIndex) {
                        configurationName = line.substring(firstIndex + 1, lastIndex);
                        classesFromConfiguration.computeIfAbsent(configurationName, k -> new HashSet<>()).add(className);
                        nextIsRole = true;
                    } else {
                        nextIsRole = false;
                    }
                } else if (configurationName != null && nextIsRole) {
                    var firstIndex = line.indexOf('`');
                    var lastIndex = line.indexOf('`', firstIndex + 1);
                    if (firstIndex != lastIndex && lastIndex > 0) {
                        var role = line.substring(firstIndex + 1, lastIndex);
                        configurationRoles.computeIfAbsent(configurationName, k -> new HashMap<>()).put(className, ConfigurationRole.fromValue(role));
                    }
                    nextIsRole = false;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generateOrThrow(Action<ProblemSpec> action, String className, String configurationName, ConfigurationRole role) {
        if (getParameters().getValidationMode().get() == ValidationMode.GENERATE) {
            missing.computeIfAbsent(className, k -> new HashMap<>()).put(configurationName, role);
            getProblems().getReporter().reporting(action);
        } else {
            throw getProblems().getReporter().throwing(action);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void validateRoleAndCaller(String name, ConfigurationRole role) {
        String callingClass = null;
        String fileName = null;
        int lineNumber = -1;
        for (var element : Thread.currentThread().getStackTrace()) {
            var className = element.getClassName();
            if (className.equals(ConfigurationUtils.class.getName()) || className.equals(Thread.class.getName())) {
                continue;
            }
            var parts = className.split("\\.");
            callingClass = parts[parts.length - 1].split("\\$")[0];
            lineNumber = element.getLineNumber();
            fileName = element.getFileName();
            break;
        }
        if (callingClass == null) {
            throw getProblems().getReporter().throwing(problem ->
                problem
                    .id("crochet-configuration-validation-unknown-caller", "Unknown caller for configuration name pattern", SimpleProblemGroup.CONFIGURATION_VALIDATION)
                    .contextualLabel("Could not determine calling class for configuration name pattern "+name)
                    .severity(Severity.ERROR)
                    .solution("Make sure ConfigurationUtils is called in such a way that the stack can be inspected")
                    .stackLocation()
                    .withException(new IllegalStateException("Could not determine calling class for configuration name pattern "+name))
            );
        }
        final var finalCallingClass = callingClass;
        final var finalFileName = Objects.requireNonNull(fileName);
        final var finalLineNumber = lineNumber;
        if (!classesFromConfiguration.getOrDefault(name, Set.of()).contains(callingClass)) {
            generateOrThrow(problem ->
                problem
                    .id("crochet-configuration-validation-undocumented-pattern", "Undocumented configuration name pattern for caller", SimpleProblemGroup.CONFIGURATION_VALIDATION)
                    .contextualLabel("Configuration name pattern "+name+" does not match documented patterns for creating class "+finalCallingClass)
                    .severity(Severity.ERROR)
                    .solution("Document pattern "+name+" for class "+finalCallingClass)
                    .lineInFileLocation(finalFileName, finalLineNumber)
                    .withException(new IllegalStateException("Configuration name pattern "+name+" does not match documented patterns for creating class "+finalCallingClass)),
                callingClass, name, role
            );
            return;
        }
        var roleMap = configurationRoles.get(name);
        if (roleMap == null || roleMap.get(callingClass) == null) {
            throw getProblems().getReporter().throwing(problem ->
                problem
                    .id("crochet-configuration-validation-undocumented-role", "Undocumented configuration name pattern role for caller", SimpleProblemGroup.CONFIGURATION_VALIDATION)
                    .contextualLabel("Could not determine expected role for configuration name pattern "+name+" in creating class "+finalCallingClass)
                    .severity(Severity.ERROR)
                    .solution("Document role for pattern "+name+" for class "+finalCallingClass)
                    .lineInFileLocation(finalFileName, finalLineNumber)
                    .withException(new IllegalStateException("Could not determine expected role for configuration name pattern "+name+" in creating class "+finalCallingClass))
            );
        }
        if (roleMap.get(callingClass) != role) {
            throw getProblems().getReporter().throwing(problem ->
                problem
                    .id("crochet-configuration-validation-incorrect-role", "Incorrect role for configuration name pattern", SimpleProblemGroup.CONFIGURATION_VALIDATION)
                    .contextualLabel("Configuration name pattern "+name+" does not match documented patterns for creating class "+finalCallingClass+"; expected role "+roleMap.get(finalCallingClass)+", got "+role)
                    .severity(Severity.ERROR)
                    .solution("Modify documented or actual role for pattern "+name+" for class "+finalCallingClass)
                    .lineInFileLocation(finalFileName, finalLineNumber)
                    .withException(new IllegalStateException("Configuration name pattern "+name+" does not match documented patterns for creating class "+finalCallingClass+"; expected role "+roleMap.get(finalCallingClass)+", got "+role))
            );
        }
    }

    private void validate(@Nullable String prefix, @Nullable String suffix, ConfigurationRole role) {
        setup();

        if (!getParameters().getValidationEnabled().get()) {
            return;
        }
        var name = NameUtils.name("*", prefix, suffix);
        validateRoleAndCaller(name, role);
    }

    private void validateInternal(@Nullable String suffix, ConfigurationRole role) {
        setup();

        if (!getParameters().getValidationEnabled().get()) {
            return;
        }

        var name = NameUtils.internal("*", suffix);
        validateRoleAndCaller(name, role);
    }

    private static ConfigurationUtils validator(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent("crochetConfigurationNameValidator", ConfigurationUtils.class, spec -> {
            var validationPath = project.getProviders()
                .gradleProperty(CrochetProperties.CROCHET_VALIDATE_CONFIGURATIONS)
                .orElse(project.getProviders().systemProperty(CrochetProperties.CROCHET_VALIDATE_CONFIGURATIONS))
                .map(p -> project.getIsolated().getRootProject().getProjectDirectory().file(p).getAsFile().getAbsolutePath().toString())
                .orElse("");
            spec.getParameters().getValidationPath().set(validationPath);
            var validationMode = project.getProviders()
                .gradleProperty(CrochetProperties.CROCHET_VALIDATE_CONFIGURATIONS_MODE)
                .map(s -> ValidationMode.valueOf(s.toUpperCase(Locale.ROOT)))
                .orElse(ValidationMode.VALIDATE);
            spec.getParameters().getValidationMode().set(validationMode);
            spec.getParameters().getValidationEnabled().set(validationPath.map(s -> !s.isEmpty()));
        }).get();
    }

    private static void validateName(Project project, @Nullable String prefix, @Nullable String suffix, ConfigurationRole role) {
        validator(project).validate(prefix, suffix, role);
    }

    private static void validateInternalName(Project project, @Nullable String suffix, ConfigurationRole role) {
        validator(project).validateInternal(suffix, role);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static DependencyScopeConfiguration dependencyScope(Project project, String parent, @Nullable String prefix, @Nullable String suffix, Action<DependencyScopeConfiguration> action) {
        validateName(project, prefix, suffix, ConfigurationRole.DEPENDENCY_SCOPE);
        var fullName = NameUtils.name(parent, prefix, suffix);
        return project.getConfigurations().dependencyScope(fullName, action).get();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static DependencyScopeConfiguration dependencyScope(ExtensionHolder holder, String parent, @Nullable String prefix, @Nullable String suffix, Action<DependencyScopeConfiguration> action) {
        return dependencyScope(((ProjectHolder) holder.extension).project, parent, prefix, suffix, action);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ResolvableConfiguration resolvable(Project project, String parent, @Nullable String prefix, @Nullable String suffix, Action<ResolvableConfiguration> action) {
        validateName(project, prefix, suffix, ConfigurationRole.RESOLVABLE);
        var fullName = NameUtils.name(parent, prefix, suffix);
        return project.getConfigurations().resolvable(fullName, action).get();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ResolvableConfiguration resolvable(ExtensionHolder holder, String parent, @Nullable String prefix, @Nullable String suffix, Action<ResolvableConfiguration> action) {
        return resolvable(((ProjectHolder) holder.extension).project, parent, prefix, suffix, action);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ConsumableConfiguration consumable(Project project, String parent, @Nullable String prefix, @Nullable String suffix, Action<ConsumableConfiguration> action) {
        validateName(project, prefix, suffix, ConfigurationRole.CONSUMABLE);
        var fullName = NameUtils.name(parent, prefix, suffix);
        return project.getConfigurations().consumable(fullName, action).get();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ConsumableConfiguration consumable(ExtensionHolder holder, String parent, @Nullable String prefix, @Nullable String suffix, Action<ConsumableConfiguration> action) {
        return consumable(((ProjectHolder) holder.extension).project, parent, prefix, suffix, action);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static DependencyScopeConfiguration dependencyScopeInternal(Project project, String parent, @Nullable String suffix, Action<DependencyScopeConfiguration> action) {
        validateInternalName(project, suffix, ConfigurationRole.DEPENDENCY_SCOPE);
        var fullName = NameUtils.internal(parent, suffix);
        return project.getConfigurations().dependencyScope(fullName, action).get();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static DependencyScopeConfiguration dependencyScopeInternal(ExtensionHolder holder, String parent, @Nullable String suffix, Action<DependencyScopeConfiguration> action) {
        return dependencyScopeInternal(((ProjectHolder) holder.extension).project, parent, suffix, action);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ResolvableConfiguration resolvableInternal(Project project, String parent, @Nullable String suffix, Action<ResolvableConfiguration> action) {
        validateInternalName(project, suffix, ConfigurationRole.RESOLVABLE);
        var fullName = NameUtils.internal(parent, suffix);
        return project.getConfigurations().resolvable(fullName, action).get();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ResolvableConfiguration resolvableInternal(ExtensionHolder holder, String parent, @Nullable String suffix, Action<ResolvableConfiguration> action) {
        return resolvableInternal(((ProjectHolder) holder.extension).project, parent, suffix, action);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ConsumableConfiguration consumableInternal(Project project, String parent, @Nullable String suffix, Action<ConsumableConfiguration> action) {
        validateInternalName(project, suffix, ConfigurationRole.CONSUMABLE);
        var fullName = NameUtils.internal(parent, suffix);
        return project.getConfigurations().consumable(fullName, action).get();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ConsumableConfiguration consumableInternal(ExtensionHolder holder, String parent, @Nullable String suffix, Action<ConsumableConfiguration> action) {
        return consumableInternal(((ProjectHolder) holder.extension).project, parent, suffix, action);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void copyAttributes(AttributeContainer source, AttributeContainer destination, ProviderFactory providerFactory) {
        source.keySet().forEach(key -> {
            destination.attributeProvider((Attribute) key, providerFactory.provider(() -> source.getAttribute(key)));
        });
    }

    public static String extractMinecraftVersion(ResolvedComponentResult component) {
        var dependencies = component.getDependencies();
        String candidate = null;
        var queue = new ArrayDeque<DependencyResult>(dependencies);
        var selectors = new HashSet<ComponentSelector>();
        while (!queue.isEmpty()) {
            var dependency = queue.poll();
            if (selectors.add(dependency.getRequested())) {
                if (dependency instanceof ResolvedDependencyResult resolvedDependencyResult) {
                    queue.addAll(resolvedDependencyResult.getSelected().getDependencies());
                    var capabilities = resolvedDependencyResult.getResolvedVariant().getCapabilities();
                    var result = capabilities.stream().flatMap(capability -> {
                        var id = capability.getGroup() + ":" + capability.getName();
                        if ((id.equals(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":" + PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES)) ||
                            (id.equals("net.neoforged:minecraft-dependencies"))) {
                            return Stream.of(capability);
                        }
                        return Stream.of();
                    }).toList();
                    if (result.size() > 1) {
                        throw new IllegalStateException("Expected exactly one capability, got "+result.size()+": "+result);
                    } else if (!result.isEmpty()) {
                        var capability = result.getFirst();
                        if (candidate != null) {
                            throw new IllegalStateException("Expected exactly one candidate, got "+candidate+" and "+capability);
                        }
                        candidate = capability.getVersion();
                    }
                }
            }
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not find minecraft-dependencies");
        }
        return candidate;
    }

    public enum ConfigurationRole {
        DEPENDENCY_SCOPE("dependencyScope"),
        RESOLVABLE("resolvable"),
        CONSUMABLE("consumable");

        private final String value;

        ConfigurationRole(String value) {
            this.value = value;
        }

        private static final Map<String, ConfigurationRole> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(ConfigurationRole::value, e -> e));

        public static ConfigurationRole fromValue(String value) {
            var out = BY_VALUE.get(value);
            if (out == null) {
                throw new IllegalArgumentException("Unknown configuration role: "+value);
            }
            return out;
        }

        public String value() {
            return value;
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    public static DependencyScopeConfiguration pistonMetaDependencies(ExtensionHolder holder, String name) {
        return dependencyScopeInternal(holder, name, "pistonMetaDownloads", c -> {});
    }

    public enum PistonMetaPiece {
        CLIENT_JAR,
        SERVER_JAR,
        VERSION_JSON,
        CLIENT_MAPPINGS,
        SERVER_MAPPINGS;

        private String configName() {
            return switch (this) {
                case CLIENT_JAR -> "clientJarPistonMetaDownloads";
                case SERVER_JAR -> "serverJarPistonMetaDownloads";
                case VERSION_JSON -> "versionJsonPistonMetaDownloads";
                case CLIENT_MAPPINGS -> "clientMappingsPistonMetaDownloads";
                case SERVER_MAPPINGS -> "serverMappingsPistonMetaDownloads";
            };
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ResolvableConfiguration pistonMeta(ExtensionHolder holder, String name, DependencyScopeConfiguration minecraftPistonMeta, PistonMetaPiece piece) {
        var project = ((ProjectHolder) holder.extension).project;
        return resolvableInternal(holder, name, piece.configName(), c -> {
            c.extendsFrom(minecraftPistonMeta);
            switch (piece) {
                case CLIENT_JAR, SERVER_JAR -> {
                    c.exclude(Map.of(
                        "group", CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP,
                        "module", PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES
                    ));
                    c.attributes(attributes -> {
                        (piece == PistonMetaPiece.CLIENT_JAR ? InstallationDistribution.CLIENT : InstallationDistribution.SERVER).apply(attributes);
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                    });
                }
                case CLIENT_MAPPINGS, SERVER_MAPPINGS -> {
                    c.attributes(attributes -> {
                        (piece == PistonMetaPiece.CLIENT_MAPPINGS ? InstallationDistribution.CLIENT : InstallationDistribution.SERVER).apply(attributes);
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, "mappings"));
                    });
                }
                case VERSION_JSON -> {
                    c.attributes(attributes -> {
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, "versionjson"));
                    });
                }
            }
        });
    }
}
