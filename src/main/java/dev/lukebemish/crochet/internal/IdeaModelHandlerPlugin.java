package dev.lukebemish.crochet.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.UnknownPluginException;

import javax.inject.Inject;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class IdeaModelHandlerPlugin implements Plugin<Project> {
    public abstract static class IdeaModelOptions {

    }

    public static IdeaModelOptions getForProject(Project project) {
        if (Boolean.getBoolean("idea.sync.active")) {
            // We break project isolation here -- not that we have a choice, the whole setup is rather terrible. Thanks IntelliJ...
            var rootProject = project.getRootProject();
            try {
                rootProject.getPluginManager().apply("dev.lukebemish.crochet.idea");
            } catch (UnknownPluginException e) {
                // Ensures that classpath errors due to multiple subprojects trying to add this are impossible
                throw new IllegalStateException("Crochet requires the 'dev.lukebemish.crochet.idea' plugin to be available to the root project plugin classpath.", e);
            }
            return rootProject.getExtensions().getByType(IdeaModelOptions.class);
        }
        return null;
    }

    @Override
    public void apply(Project project) {
        if (Boolean.getBoolean("idea.sync.active")) {
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

    private static Object get(Object holder, String property) {
        try {
            var field = holder.getClass().getField(property);
            if (!field.accessFlags().contains(AccessFlag.STATIC)) {
                return field.get(holder);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException("Field " + property + " not found on " + holder.getClass().getName());
    }

    private static void set(Object holder, String property, Object value) {
        try {
            var field = holder.getClass().getField(property);
            if (!field.accessFlags().contains(AccessFlag.STATIC)) {
                field.set(holder, value);
                return;
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException("Field " + property + " not found on " + holder.getClass().getName());
    }

    public abstract static class IdeaSettings {
        private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

        private final IdeaModelOptions options;

        @Inject
        public IdeaSettings(IdeaModelOptions options) {
            this.options = options;
        }


        @Override
        public String toString() {
            JsonObject json = new JsonObject();
            json.addProperty("requiresPostprocessing", true);
            json.addProperty("generateImlFiles", true);
            return GSON.toJson(json);
        }
    }

    private void applyRoot(Project project) {
        project.getExtensions().create(IdeaModelOptions.class, "crochetIdeaModelOptions", IdeaModelOptions.class);
        project.afterEvaluate(p -> {
            project.getPluginManager().apply("idea");
            var ideaModel = (ExtensionAware) project.getExtensions().getByName("idea");
            var ideaProject = (ExtensionAware) ideaModel.getExtensions().getByName("project");
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
            } else {
                // idea-ext is not present. That means we have to:
                // - set up "settings" extension on idea.project
                // - enable "requiresPostprocessing" and "generateImlFiles" (handled by the "settings" extension)
                // - create a corresponding "processIdeaSettings" task
                // - link up the relevant data to the IdeaModelOptions extension

                ideaProject.getExtensions().create("settings", IdeaSettings.class);
            }
        });
    }
}
