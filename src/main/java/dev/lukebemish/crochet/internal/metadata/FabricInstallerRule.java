package dev.lukebemish.crochet.internal.metadata;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.lukebemish.crochet.model.InstallationDistribution;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@CacheableRule
public abstract class FabricInstallerRule implements ComponentMetadataRule {
    @Inject
    public FabricInstallerRule() {}

    @Inject
    protected abstract RepositoryResourceAccessor getRepositoryResourceAccessor();

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        var id = details.getId();

        var jsonPath = id.getGroup().replace('.', '/') + "/" + id.getName() + "/" + id.getVersion() + "/" + id.getName() + "-" + id.getVersion() + ".json";

        JsonObject[] installerJson = new JsonObject[1];
        getRepositoryResourceAccessor().withResource(jsonPath, input -> {
            try {
                var contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                installerJson[0] = new Gson().fromJson(contents, JsonObject.class);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read "+jsonPath, e);
            }
        });

        if (!installerJson[0].has("libraries")) {
            return;
        }

        var libraries = installerJson[0].getAsJsonObject("libraries");

        details.withVariant("compile", variant -> {
            dependenciesFor("common", variant, libraries, id);
            InstallationDistribution.COMMON.apply(variant.getAttributes());
        });

        details.withVariant("runtime", variant -> {
            dependenciesFor("common", variant, libraries, id);
            InstallationDistribution.COMMON.apply(variant.getAttributes());
        });

        details.addVariant("installerJsonRuntimeClientElements", "runtime", variant -> {
            dependenciesFor("common", variant, libraries, id);
            dependenciesFor("client", variant, libraries, id);
            defaultFiles(variant, id);
            InstallationDistribution.CLIENT.apply(variant.getAttributes());
        });

        details.addVariant("installerJsonRuntimeServerElements", "runtime", variant -> {
            dependenciesFor("common", variant, libraries, id);
            dependenciesFor("server", variant, libraries, id);
            defaultFiles(variant, id);
            InstallationDistribution.SERVER.apply(variant.getAttributes());
        });

        details.addVariant("installerJsonApiClientElements", "compile", variant -> {
            dependenciesFor("common", variant, libraries, id);
            dependenciesFor("client", variant, libraries, id);
            dependenciesFor("development", variant, libraries, id);
            defaultFiles(variant, id);
            InstallationDistribution.CLIENT.apply(variant.getAttributes());
        });

        details.addVariant("installerJsonApiServerElements", "compile", variant -> {
            dependenciesFor("common", variant, libraries, id);
            dependenciesFor("server", variant, libraries, id);
            dependenciesFor("development", variant, libraries, id);
            defaultFiles(variant, id);
            InstallationDistribution.SERVER.apply(variant.getAttributes());
        });
    }

    private static void defaultFiles(VariantMetadata variant, ModuleVersionIdentifier id) {
        variant.withFiles(files -> {
            files.addFile(id.getName() + "-" + id.getVersion() + ".jar");
        });
    }

    private static void dependenciesFor(String distribution, VariantMetadata variant, JsonObject libraries, ModuleVersionIdentifier id) {
        variant.withDependencies(dependencies -> {
            if (libraries.has(distribution)) {
                libraries.getAsJsonArray(distribution).forEach(element -> {
                    var dependency = element.getAsJsonObject();
                    // TODO: this needs to be non-transitive!
                    dependencies.add(dependency.getAsJsonPrimitive("name").getAsString());
                });
            }
        });
    }
}
