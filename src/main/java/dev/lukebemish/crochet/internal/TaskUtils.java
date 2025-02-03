package dev.lukebemish.crochet.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.Nullable;

public final class TaskUtils {
    private TaskUtils() {}

    public static <T extends Task> TaskProvider<T> register(TaskContainer container, Class<T> type, String parent, @Nullable String prefix, @Nullable String suffix, Action<? super T> action) {
        var fullName = NameUtils.name(parent, prefix, suffix);
        return container.register(fullName, type, action);
    }

    public static <T extends Task> TaskProvider<T> register(Project project, Class<T> type, String parent, @Nullable String prefix, @Nullable String suffix, Action<? super T> action) {
        return register(project.getTasks(), type, parent, prefix, suffix, action);
    }

    public static <T extends Task> TaskProvider<T> register(ExtensionHolder holder, Class<T> type, String parent, @Nullable String prefix, @Nullable String suffix, Action<? super T> action) {
        return register(((ProjectHolder) holder.extension).project, type, parent, prefix, suffix, action);
    }

    public static <T extends Task> TaskProvider<T> registerInternal(TaskContainer container, Class<T> type, String parent, @Nullable String suffix, Action<? super T> action) {
        var fullName = NameUtils.internal(parent, suffix);
        return container.register(fullName, type, t -> {
            action.execute(t);
            t.setGroup("crochet setup");
        });
    }

    public static <T extends Task> TaskProvider<T> registerInternal(Project project, Class<T> type, String parent, @Nullable String suffix, Action<? super T> action) {
        return registerInternal(project.getTasks(), type, parent, suffix, action);
    }

    public static <T extends Task> TaskProvider<T> registerInternal(ExtensionHolder holder, Class<T> type, String parent, @Nullable String suffix, Action<? super T> action) {
        return registerInternal(((ProjectHolder) holder.extension).project, type, parent, suffix, action);
    }
}
