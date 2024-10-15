package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

final class ClasspathGroupUtilities {
    private ClasspathGroupUtilities() {}

    public static Provider<List<SequencedSet<File>>> modGroupsFromDependencies(Configuration configuration, Provider<? extends Set<File>> excludes) {
        return configuration.getIncoming().getArtifacts().getResolvedArtifacts().zip(excludes, (artifacts, excludedFiles) -> {
            SequencedMap<ResolvedVariantResult, SequencedSet<File>> map = new LinkedHashMap<>();
            for (var artifact : artifacts) {
                if (excludedFiles.contains(artifact.getFile())) {
                    continue;
                }
                System.out.println(artifact.getFile());
                System.out.println(artifact.getVariant());
                var key = artifact.getVariant();
                map.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(artifact.getFile());
            }
            map.sequencedValues().removeIf(set -> set.size() <= 1);
            var outSet = new ArrayList<>(map.values());
            if (excludedFiles.size() >= 1) {
                outSet.add((excludedFiles instanceof SequencedSet<File> sequencedExcluded) ? sequencedExcluded : new LinkedHashSet<>(excludedFiles));
            }
            return outSet;
        });
    }

    public static Provider<List<SequencedSet<File>>> combineGroups(Provider<List<SequencedSet<File>>> initialProvider, Provider<List<SequencedSet<File>>> additionalProvider) {
        return initialProvider.zip(additionalProvider, (initial, additional) -> {
            var list = new ArrayList<>(initial);
            for (var additionalSet : additional) {
                int foundIn = -1;
                int count = 0;
                for (var entry : list) {
                    boolean overlaps = false;
                    for (var file : additionalSet) {
                        if (entry.contains(file)) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (overlaps) {
                        if (foundIn != -1) {
                            throw new IllegalStateException("Mod or dependency with paths "+additionalSet.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator))+" overlaps with multiple existing path groups");
                        }
                        foundIn = count;
                    }
                    count++;
                }
                if (foundIn == -1) {
                    list.add(new LinkedHashSet<>(additionalSet));
                } else {
                    SequencedSet<File> existing = list.get(foundIn);
                    if (!(existing instanceof LinkedHashSet<File>)) {
                        existing = new LinkedHashSet<>(existing);
                        existing.addAll(additionalSet);
                        list.set(foundIn, existing);
                    } else {
                        existing.addAll(additionalSet);
                    }
                }
            }
            return list;
        });
    }

    public static Provider<List<SequencedSet<File>>> addMods(Provider<List<SequencedSet<File>>> initialProvider, Provider<Set<Mod>> modsProvider) {
        return combineGroups(initialProvider, modsProvider.map(mods -> new ArrayList<>(mods.stream().map(mod -> new LinkedHashSet<>(mod.components.getFiles())).toList())));
    }
}
