package dev.lukebemish.crochet.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.configuration.BuildFeatures;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApiStatus.Internal
public class IdeaModelHandlerPlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdeaModelHandlerPlugin.class);

    public static abstract class LayoutFileBuildService implements BuildService<LayoutFileBuildService.Params>, AutoCloseable {
        interface Params extends BuildServiceParameters {
            Property<File> getLayoutFile();
        }

        @Override
        public void close() {
            var file = getParameters().getLayoutFile().get();
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public interface SourceBinaryLinker {
        void accept(Provider<List<RegularFile>> binaries, Provider<List<RegularFile>> sources, Provider<List<RegularFile>> lineMappedBinaries);
    }
    public interface BeforeRunCollector {
        void forTask(TaskProvider<?> task);
    }

    public abstract static class IdeaRun implements Named {
        private final String name;

        @Inject
        public IdeaRun(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public abstract Property<String> getMainClass();
        public abstract Property<String> getJvmArgs();
        public abstract Property<String> getProgramParameters();
        public abstract Property<Project> getProject();
        public abstract Property<SourceSet> getSourceSet();
        public abstract DirectoryProperty getWorkingDirectory();
        public abstract Property<Action<BeforeRunCollector>> getBeforeRun();
    }

    public static boolean isIdeaSyncRelated(Project project) {
        return Boolean.getBoolean("idea.sync.active") || project.getGradle().getStartParameter().getTaskNames().contains("processIdeaSettings");
    }

    public abstract static class IdeaModelOptions {
        private final SourceBinaryLinker sourceBinaryLinker;
        private final NamedDomainObjectContainer<IdeaRun> runs;
        private final List<Object> taskDependencies = new ArrayList<>();

        @Inject
        public IdeaModelOptions(SourceBinaryLinker sourceBinaryLinker, NamedDomainObjectContainer<IdeaRun> runs) {
            this.sourceBinaryLinker = sourceBinaryLinker;
            this.runs = runs;
        }

        public void mapBinaryToSource(Provider<RegularFile> binary, Provider<RegularFile> source) {
            mapBinariesToSources(binary.map(List::of), source.map(List::of));
        }

        public void mapBinariesToSources(Provider<List<RegularFile>> binaries, Provider<List<RegularFile>> sources) {
            sourceBinaryLinker.accept(binaries, sources, binaries);
        }

        public void mapBinariesToSourcesWithLineMaps(Provider<List<RegularFile>> binaries, Provider<List<RegularFile>> sources, Provider<List<RegularFile>> lineMappedBinaries) {
            sourceBinaryLinker.accept(binaries, sources, lineMappedBinaries);
        }

        public void mapBinaryToSourceWithLineMaps(Provider<RegularFile> binary, Provider<RegularFile> source, Provider<RegularFile> lineMappedBinary) {
            mapBinariesToSourcesWithLineMaps(binary.map(List::of), source.map(List::of), lineMappedBinary.map(List::of));
        }

        public void dependsOn(Object task) {
            taskDependencies.add(task);
        }

        public NamedDomainObjectContainer<IdeaRun> getRuns() {
            return runs;
        }
    }

    public abstract static class BuildFeaturesWrapper {
        @Inject
        public BuildFeaturesWrapper() {}

        @Inject
        protected abstract BuildFeatures getBuildFeatures();
    }

    public static IdeaModelOptions retrieve(Project project) {
        if (isIdeaSyncRelated(project)) {
            // We break project isolation here -- not that we have a choice, the whole setup is rather terrible. Thanks IntelliJ...
            var rootProject = project.getRootProject();
            if (!project.equals(rootProject)) {
                var features = project.getObjects().newInstance(BuildFeaturesWrapper.class).getBuildFeatures();
                if (features.getIsolatedProjects().getActive().get()) {
                    LOGGER.error("Attempted to configure IntelliJ options on a non-root project with project isolation enabled; due to restrictions of IntelliJ's model, this will not work at present");
                }
            }
            try {
                rootProject.getPluginManager().apply("dev.lukebemish.crochet.idea");
            } catch (UnknownPluginException e) {
                // Ensures that classpath errors due to multiple subprojects trying to add this are impossible
                throw new IllegalStateException("Crochet requires the 'dev.lukebemish.crochet.idea' plugin to be available to the root project plugin classpath.", e);
            }
            return rootProject.getExtensions().getByType(IdeaModelOptions.class);
        }
        throw new IllegalStateException("IdeaModelOptions can only be retrieved during IntelliJ IDE sync.");
    }

    @Override
    public void apply(Project project) {
        if (isIdeaSyncRelated(project)) {
            // We break project isolation here -- not that we have a choice, the whole setup is rather terrible. Thanks IntelliJ...
            var rootProject = project.getRootProject();
            if (project.getRootProject().equals(project)) {
                applyRoot(project);
            }
            try {
                rootProject.getPluginManager().apply("dev.lukebemish.crochet.idea");
            } catch (UnknownPluginException e) {
                // Ensures that classpath errors due to multiple subprojects trying to add this are impossible
                throw new IllegalStateException("Crochet requires the 'dev.lukebemish.crochet.idea' plugin to be available to the root project plugin classpath.", e);
            }
        }
    }

    private static Object invoke(Object holder, String method, Class<?>[] types, Object[] args) {
        try {
            return holder.getClass().getMethod(method, types).invoke(holder, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Method " + method + " not found on " + holder.getClass().getName());
        }
    }

    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    public abstract static class IdeaSettings {
        private final NamedDomainObjectContainer<IdeaRun> runConfigurations;

        public NamedDomainObjectContainer<IdeaRun> getRunConfigurations() {
            return this.runConfigurations;
        }

        @Override
        public String toString() {
            JsonObject json = new JsonObject();
            json.addProperty("requiresPostprocessing", true);
            json.addProperty("generateImlFiles", true);
            var runConfigurations = new JsonArray();
            for (var run : getRunConfigurations()) {
                JsonObject runJson = new JsonObject();
                runJson.addProperty("name", run.getName());
                runJson.addProperty("type", "application");
                runJson.addProperty("moduleName", computeModuleName(run.getProject().get(), run.getSourceSet().get()));
                runJson.addProperty("mainClass", run.getMainClass().get());
                runJson.addProperty("jvmArgs", run.getJvmArgs().get());
                runJson.addProperty("programParameters", run.getProgramParameters().get());
                runJson.addProperty("workingDirectory", run.getWorkingDirectory().get().getAsFile().getAbsolutePath());
                JsonArray beforeRun = new JsonArray();
                run.getBeforeRun().get().execute(task -> {
                    JsonObject singleTask = new JsonObject();
                    singleTask.addProperty("type", "gradleTask");
                    singleTask.addProperty("taskName", task.getName());
                    singleTask.addProperty("projectPath", run.getProject().get().getProjectDir().getAbsolutePath().replaceAll("\\\\", "/"));
                    beforeRun.add(singleTask);
                });
                runJson.add("beforeRun", beforeRun);
                runConfigurations.add(runJson);
            }
            json.add("runConfigurations", runConfigurations);
            return GSON.toJson(json);
        }

        @Inject
        public IdeaSettings(NamedDomainObjectContainer<IdeaRun> runConfigurations) {
            this.runConfigurations = runConfigurations;
        }

        private static String computeModuleName(Project project, SourceSet sourceSet) {
            var name = project.getRootProject().getName();
            if (project.getPath().equals(":")) {
                return addSourceSetName(name, sourceSet.getName());
            }
            return addSourceSetName(name + project.getPath().replaceAll(":", "."), sourceSet.getName());
        }

        private static String addSourceSetName(String moduleName, String sourceSetName) {
            return moduleName + (sourceSetName.isEmpty() ? "" : "." + sourceSetName);
        }
    }

    public static class IdeaLayoutJson {
        public Map<String, String> modulesMap;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyRoot(Project project) {
        ListProperty<String> sources = project.getObjects().listProperty(String.class);
        ListProperty<String> binaries = project.getObjects().listProperty(String.class);
        ListProperty<String> lineMappedBinaries = project.getObjects().listProperty(String.class);
        NamedDomainObjectContainer<IdeaRun> ideaRuns = project.getObjects().domainObjectContainer(IdeaRun.class);
        var extension = project.getExtensions().create(IdeaModelOptions.class, "crochetIdeaModelOptions", IdeaModelOptions.class, (SourceBinaryLinker) (newBinaries, newSources, newLineMappedBinaries) -> {
            sources.addAll(newSources.map(files -> files.stream().map(f -> f.getAsFile().toPath().normalize().toAbsolutePath().toString()).toList()));
            binaries.addAll(newBinaries.map(files -> files.stream().map(f -> f.getAsFile().toPath().normalize().toAbsolutePath().toString()).toList()));
            lineMappedBinaries.addAll(newLineMappedBinaries.map(files -> files.stream().map(f -> f.getAsFile().toPath().normalize().toAbsolutePath().toString()).toList()));
        }, ideaRuns);
        var layoutFile = project.file("layout.json");
        var serviceProvider = project.getGradle().getSharedServices()
            .registerIfAbsent("crochetLayoutFile", LayoutFileBuildService.class, spec -> spec.getParameters().getLayoutFile().set(layoutFile));
        Action<Project> toExecute = p -> {
            project.getPluginManager().apply("idea");
            var ideaModel = (IdeaModel) project.getExtensions().getByName("idea");
            var ideaProject = (ExtensionAware) ideaModel.getProject();
            // check if idea-ext is applied
            boolean isIdeaExtPresent = p.getPluginManager().hasPlugin("org.jetbrains.gradle.plugin.idea-ext");
            if (isIdeaExtPresent) {
                // idea-ext is present. It sucks, but this means something else is using it so we need to co-exist. That means we have to:
                // - enable "requiresPostprocessing" and "generateImlFiles" through idea-ext
                // - post-process the module files in a doLast block of "processIdeaSettings"
                // - link up the relevant data to the IdeaModelOptions extension

                var settings = ideaProject.getExtensions().getByName("settings");
                invoke(settings, "setGenerateImlFiles", new Class<?>[] {boolean.class}, new Object[] {true});
                invoke(settings, "withIDEADir", new Class<?>[] {Action.class}, new Object[]{(Action<File>) ignored -> {}});
                var runConfigurations = (ExtensiblePolymorphicDomainObjectContainer) ideaProject.getExtensions().getByName("runConfigurations");
                runConfigurations.addAllLater(project.provider(() -> {
                    List list = new ArrayList<>();
                    var applicationClass = Class.forName("org.jetbrains.gradle.ext.Application", false, project.getBuildscript().getClassLoader());
                    var gradleTaskClass = Class.forName("org.jetbrains.gradle.ext.GradleTask", false, project.getBuildscript().getClassLoader());
                    for (IdeaRun run : ideaRuns) {
                        var application = project.getObjects().newInstance(applicationClass, run.getName(), project);
                        invoke(application, "setMainClass", new Class<?>[] {String.class}, new Object[] {run.getMainClass().get()});
                        invoke(application, "setJvmArgs", new Class<?>[] {String.class}, new Object[] {run.getJvmArgs().get()});
                        invoke(application, "setProgramParameters", new Class<?>[] {String.class}, new Object[] {run.getProgramParameters().get()});
                        invoke(application, "moduleRef", new Class<?>[] {Project.class, SourceSet.class}, new Object[] {run.getProject().get(), run.getSourceSet().get()});
                        invoke(application, "setWorkingDir", new Class<?>[] {String.class}, new Object[] {run.getWorkingDirectory().get().getAsFile().getAbsolutePath()});
                        invoke(application, "beforeRun", new Class<?>[] {Action.class}, new Object[] {(Action<? extends PolymorphicDomainObjectContainer>) beforeRun -> {
                            run.getBeforeRun().get().execute(task -> {
                                beforeRun.create(task.getName(), gradleTaskClass, t -> {
                                    invoke(t, "setTask", new Class<?>[] {Task.class}, new Object[] {task.get()});
                                });
                            });
                        }});
                    }
                    return list;
                }));
            } else {
                // idea-ext is not present. That means we have to:
                // - set up "settings" extension on idea.project
                // - enable "requiresPostprocessing" and "generateImlFiles" (handled by the "settings" extension)
                // - create a corresponding "processIdeaSettings" task
                // - link up the relevant data to the IdeaModelOptions extension
                if (ideaProject.getExtensions().findByName("settings") != null) {
                    LOGGER.error("idea-ext is not present, and something else already registered an 'idea.settings' extension. We're not sure what to do here.");
                }

                ideaProject.getExtensions().create("settings", IdeaSettings.class, ideaRuns);
                project.getTasks().register("processIdeaSettings");
            }
            project.getTasks().named("processIdeaSettings", task -> {
                task.getInputs().property("crochetBinaries", binaries);
                task.dependsOn(extension.taskDependencies.toArray());
                task.getInputs().property("crochetSources", sources);
                task.getInputs().property("crochetLineMappedBinaries", lineMappedBinaries);
                task.getOutputs().upToDateWhen(t -> false);
                task.usesService(serviceProvider);
                task.doLast(t -> {
                    var path = serviceProvider.get().getParameters().getLayoutFile().get().toPath();
                    if (!Files.exists(path)) {
                        LOGGER.error("No layout file found at " + path);
                        return;
                    }
                    Map<String, String> binariesToSourcesPathMap = new HashMap<>();
                    Map<String, String> binariesToLineMappedPathMap = new HashMap<>();
                    var finalBinaries = (List<String>) t.getInputs().getProperties().get("crochetBinaries");
                    var finalSources = (List<String>) t.getInputs().getProperties().get("crochetSources");
                    var finalLineMappedBinaries = (List<String>) t.getInputs().getProperties().get("crochetLineMappedBinaries");
                    for (int i = 0; i < finalBinaries.size(); i++) {
                        binariesToSourcesPathMap.put(finalBinaries.get(i), finalSources.get(i));
                        binariesToSourcesPathMap.put(finalLineMappedBinaries.get(i), finalSources.get(i));
                        if (!Objects.equals(finalBinaries.get(i), finalLineMappedBinaries.get(i))) {
                            binariesToLineMappedPathMap.put(finalBinaries.get(i), finalLineMappedBinaries.get(i));
                        }
                    }
                    var tmpFile = t.getTemporaryDir().toPath().resolve("layout.json");
                    try {
                        if (Files.exists(tmpFile)) {
                            Files.delete(tmpFile);
                        }
                        Files.createDirectories(tmpFile.getParent());
                        Files.copy(path, tmpFile);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    IdeaLayoutJson layoutJson;
                    try (var reader = Files.newBufferedReader(tmpFile)) {
                        layoutJson = GSON.fromJson(reader, IdeaLayoutJson.class);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    layoutJson.modulesMap.forEach((module, imlPathString) -> {
                        LOGGER.info("Processing library sources for module " + module + " at "+ imlPathString);
                        var imlPath = new File(imlPathString).toPath();
                        if (Files.exists(imlPath)) {
                            var moduleDir = imlPath.getParent();
                            try {
                                var documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                                var doc = documentBuilder.parse(imlPath.toFile());
                                XPathFactory xPathfactory = XPathFactory.newInstance();
                                XPath xpath = xPathfactory.newXPath();
                                XPathExpression expr = xpath.compile("//component[@name=\"NewModuleRootManager\"]/orderEntry[@type=\"module-library\"]/library");
                                NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                                for (int i = 0; i < nodes.getLength(); i++) {
                                    var node = nodes.item(i);
                                    var classesNode = getNode(node, "CLASSES");
                                    if (classesNode != null) {
                                        var classesRoot = getNode(classesNode, "root");
                                        if (classesRoot != null) {
                                            var urlNode = classesRoot.getAttributes().getNamedItem("url");
                                            if (urlNode != null) {
                                                var url = urlNode.getNodeValue();
                                                if (url.startsWith("jar://") && url.endsWith("!/")) {
                                                    var jarPathString = url.substring(5, url.length() - 2).replace("$MODULE_DIR$", moduleDir.toAbsolutePath().toString());
                                                    var jarPath = Paths.get(jarPathString).normalize().toAbsolutePath().toString();
                                                    if (binariesToSourcesPathMap.containsKey(jarPath)) {
                                                        if (binariesToLineMappedPathMap.containsKey(jarPath)) {
                                                            var relativeLineMappedPath = "$MODULE_DIR$" + File.separator + moduleDir.relativize(Paths.get(binariesToLineMappedPathMap.get(jarPath)));
                                                            var newUrl = "jar://" + relativeLineMappedPath + "!/";
                                                            urlNode.setNodeValue(newUrl);
                                                        }
                                                        var relativeSourcesPath = "$MODULE_DIR$" + File.separator + moduleDir.relativize(Paths.get(binariesToSourcesPathMap.get(jarPath)));
                                                        var newUrl = "jar://" + relativeSourcesPath + "!/";
                                                        var sourcesNode = getNode(node, "SOURCES");
                                                        if (sourcesNode == null) {
                                                            var sourcesRoot = doc.createElement("root");
                                                            sourcesRoot.setAttribute("url", newUrl);
                                                            sourcesRoot.setAttribute("type", "java-source");
                                                            sourcesNode = doc.createElement("SOURCES");
                                                            sourcesNode.appendChild(sourcesRoot);
                                                            node.appendChild(sourcesNode);
                                                        } else {
                                                            var sourceRoot = getNode(sourcesNode, "root");
                                                            if (!(sourceRoot instanceof Element elementRoot)) {
                                                                Element sourceRootElement = doc.createElement("root");
                                                                sourceRootElement.setAttribute("url", newUrl);
                                                                sourceRootElement.setAttribute("type", "java-source");
                                                                sourcesNode.appendChild(sourceRootElement);
                                                            } else {
                                                                elementRoot.setAttribute("url", newUrl);
                                                                elementRoot.setAttribute("type", "java-source");
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                                Transformer transformer = transformerFactory.newTransformer();
                                DOMSource source = new DOMSource(doc);
                                try (var output = Files.newOutputStream(imlPath)) {
                                    StreamResult result = new StreamResult(output);
                                    transformer.transform(source, result);
                                }
                            } catch (Exception e) {
                                if (e instanceof RuntimeException rE) {
                                    throw rE;
                                }
                                throw new RuntimeException(e);
                            }
                        }
                    });
                });
            });
        };
        if (project.getState().getExecuted()) {
            toExecute.execute(project);
        } else {
            project.afterEvaluate(toExecute);
        }
    }

    private @Nullable Node getNode(Node node, String name) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            var child = node.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(name)) {
                return child;
            }
        }
        return null;
    }
}
