# Installation-created Configurations

General rules:
- Configurations starting with `_crochet` are meant to be internal; `*` is a placeholder for the (capitalized if necessary) parent name.
- Configurations exposed through a `Dependencies` should not be internal, and should have a name corresponding to their `DependencyCollector`.
- Configurations should be created through `dependencyScope`, `consumable`, or `resolvable` unless there is a documented reason otherwise.
- Configurations should be created through `ConfigurationUtils`, using the relevant helper methods, to ensure consistency

Note: this document is used by `ConfigurationUtils` in development to validate that created configurations are documented.
Configurations must have the role they are documented as having here, and must be included in this document.

## CrochetProjectPlugin

### `*LocalRuntime`
`dependencyScope`

Local runtime dependencies that contribute to the runtime classpath but are not published.

### `*LocalImplementation`
`dependencyScope`

Local implementation dependencies that contribute to the runtime and compile classpaths but are not published.

### `_crochet*TaskGraphRunner`
`dependencyScope`

Dependencies for TaskGraphRunner.

### `_crochet*TaskGraphRunnerClasspath`
`resolvable` extends `_crochet*TaskGraphRunner`

Contains the classpath of TaskGraphRunner.

Attributes:
- `org.gradle.jvm.version` - 21
- `org.gradle.dependency.bundling` - `shadowed`

### `_crochet*TaskGraphRunnerTools`
`dependencyScope`

Contains tools TaskGraphRunner may invoke.

### `_crochet*TaskGraphRunnerToolsClasspath`
`resolvable` extends `_crochet*TaskGraphRunnerTools`

Contains the classpath of tools TaskGraphRunner may invoke.

Attributes:
- `org.gradle.jvm.version` - 21

### `_crochet*DevLaunch`
`dependencyScope`

Contains neo's DevLaunch tool, for runs.

### `_crochet*TerminalConsoleAppender`
`dependencyScope`

Contains terminalconsoleappender, for runs.

## MappingsConfigurationCounter

### `_crochet*CounterMappings`
`dependencyScope`

Depends on a single set of mappings within the mappings DSL.

### `_crochet*CounterMappingsClasspath`
`resolvable` extends `_crochet*CounterMappings`

Contains a single set of mappings within the mappings DSL.

## MinecraftInstallation

### `*MinecraftDependencies`
`dependencyScope`

Given the dependencies of Minecraft.

### `_crochet*MinecraftDependenciesVersioning`
`resolvable` extends `*MinecraftDependencies`

Produces the minecraft version of an installation by locating a
resolved variant with a given capability; thus, cannot depend on that version. Resolved _only_ to determine this version;
otherwise, consumers should use `*MinecraftDependencies`.

Attributes:
- `net.neoforged.distribution` - Installation default
- `org.gradle.usage` - `java-api`

### `_crochet*NonUpgradableDependencies`
`dependencyScope` extends `*MinecraftDependencies`

Collects a set of dependencies who, along with their transitive dependencies, should be impossible to upgrade at compile
or runtime. Includes things that may have to upgrade MC's dependencies, such as loader.

### `_crochet*NonUpgradableClientCompileVersioning`
`resolvable` extends `_crochet*NonUpgradableDependencies`

Pins versioning for client compile dependencies.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.usage` - `java-api`

### `_crochet*NonUpgradableServerCompileVersioning`
`resolvable` extends `_crochet*NonUpgradableDependencies`

Pins versioning for server compile dependencies.

Attributes:
- `net.neoforged.distribution` - `server`
- `org.gradle.usage` - `java-api`

### `_crochet*NonUpgradableClientRuntimeVersioning`
`resolvable` extends `_crochet*NonUpgradableDependencies`

Pins versioning for client runtime dependencies.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.usage` - `java-runtime`

### `_crochet*NonUpgradableServerRuntimeVersioning`
`resolvable` extends `_crochet*NonUpgradableDependencies`

Pins versioning for server runtime dependencies.

Attributes:
- `net.neoforged.distribution` - `server`
- `org.gradle.usage` - `java-runtime`

### `_crochet*MinecraftResources`
`dependencyScope`

Contains the resources jar of an installation.

### `_crochet*Minecraft`
`dependencyScope`

Contains the runtime classpath of minecraft, including dependencies and the resource jar.

### `_crochet*MinecraftLineMapped`
`dependencyScope`

Contains the runtime classpath of minecraft, including dependencies and the resource jar, but with binaries having fixed
line mappings.

## LocalMinecraftInstallation

### `*AccessTransformers`
`dependencyScope`

Access transformers an installation should consume.

### `*AccessTransformersApi`
`dependencyScope`

Access transformers an installation should consume, and expose to dependents.

### `_crochet*AccessTransformersPath`
`resolvable` extends `*AccessTransformers`, `*AccessTransformersApi`

Contains all access transformers to use for the installation.

Attributes:
- `org.gradle.category` - `accesstransformer`

### `*AccessTransformersElements`
`consumable` extends `*InjectedInterfacesApi`

Access transformers to expose`to dependents.

Attributes:
- `org.gradle.category` - `accesstransformer`

### `*InjectedInterfaces`
`dependencyScope`

Injected interfaces an installation should consume, as neo-format files.

### `*InjectedInterfacesApi`
`dependencyScope`

Injected interfaces an installation should consume, and expose to dependents, as neo-format files.

### `_crochet*InjectedInterfacesPath`
`resolvable` extends `*InjectedInterfaces`, `*InjectedInterfacesApi`

Contains all injected interfaces to use for the installation, as neo-format files.

Attributes:
- `org.gradle.category` - `interfaceinjection`

### `*InjectedInterfacesElements`
`consumable` extends `*InjectedInterfacesApi`

Injected interfaces to expose to dependents, as neo-format files.

Attributes:
- `org.gradle.category` - `interfaceinjection`

### `_crochet*MinecraftElements`
`consumable` extends `*Minecraft`

Contains the runtime classpath of minecraft, including dependencies and the resource jar, to
expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:minecraft-<share-tag>:1.0.0`

### `_crochet*MinecraftDependenciesElements`
`consumable` extends `*MinecraftDependencies`

Contains the dependencies of Minecraft, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:minecraft-dependencies-<share-tag>:1.0.0`

### `_crochet*MinecraftLineMappedElements`
`consumable` extends `*MinecraftLineMapped`

Contains the runtime classpath of minecraft, including dependencies and the resource jar, but with binaries having fixed
line mappings, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:minecraft-linemapped-<share-tag>:1.0.0`

### `_crochet*MinecraftResourcesElements`
`consumable` extends `*MinecraftResources`

Contains the resources jar of an installation, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:minecraft-resources-<share-tag>:1.0.0`

### `_crochet*NonUpgradableElements`
`consumable` extends `_crochet*NonUpgradableDependencies`

Contains non-upgradable dependencies, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:non-upgradable-<share-tag>:1.0.0`

### `_crochet*AssetsProperties`
`dependencyScope`

Contains the generated assets properties file of an installation.

### `_crochet*AssetsPropertiesElements`
`consumable` extends `_crochet*AssetsProperties`

Contains the generated assets properties file of an installation, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:assets-properties-<share-tag>:1.0.0`

### `_crochet*BinaryElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*BinaryLineMappedElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ResourcesElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

## AbstractVanillaInstallation

### `_crochet*PistonMetaDownloads`
`dependencyScope`

Contains stub dependencies capable of locating various things from piston-meta that TaskGraphRunner needs to produce a
remapped minecraft jar.

### `_crochet*ClientJarPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the original client jar of the target version.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.category` - `library`

Excludes:
- `dev.lukebemish.crochet.mojang-stubs:minecraft-dependencies` (not needed but brought in by the target stub variant)

### `_crochet*ServerJarPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the original server jar of the target version.

Attributes:
- `net.neoforged.distribution` - `server`
- `org.gradle.category` - `library`

Excludes:
- `dev.lukebemish.crochet.mojang-stubs:minecraft-dependencies` (not needed but brought in by the target stub variant)

### `_crochet*ClientMappingsPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the client mappings of the target version.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.category` - `mappings`

### `_crochet*VersionJsonPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the version json of the target version.

Attributes:
- `org.gradle.category` - `versionjson`

### `_crochet*RunnerCompileClasspath`
`resolvable` extends `*MinecraftDependencies`

Contains the compile resolution of the classpath used to decompile Minecraft by TaskGraphRunner. Resolves client
dependencies under the assumption that they are a superset of server, and since TaskGraphRunner is queried with `joined`.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.usage` - `java-api`

### `_crochet*RunnerRuntimeClasspath`
`resolvable` extends `*MinecraftDependencies`

Contains the runtime resolution of the classpath used to decompile Minecraft by TaskGraphRunner. Resolves client
dependencies under the assumption that they are a superset of server, and since TaskGraphRunner is queried with `joined`.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.usage` - `java-runtime`

## FabricInstallation

### `*Loader`
`dependencyScope`

Contains the version of fabric loader being used.

Extended by `_crochet*NonUpgradableDependencies`

### `_crochet*LoaderElements`
`consumable` extends `*Loader`

Contains the version of fabric loader being used, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:loader-<share-tag>:1.0.0`

### `*AccessWideners`
`dependencyScope`

Access wideners an installation should consume.

### `*AccessWidenersElements`
`consumable` extends `*AccessWideners`

Access wideners to expose to dependents.

### `_crochet*AccessWidenersPath`
`resolvable` extends `*AccessWideners`

Resolves standalone access widener files for the configuration.

Attributes:
- `org.gradle.category` - `accesswidener`

### `_crochet*SharedInjectedInterfacesElements`
`consumable`

Contains injected-interface files that should be bundled into the jar with this configuration (the artifacts of
`*InjectedInterfacesElements`).

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:injected-interfaces-<share-tag>:1.0.0`

### `_crochet*Intermediary`
`dependencyScope`

Contains the intermediary mappings of the target version.

### `_crochet*IntermediaryClasspath`
`resolvable` extends `_crochet*Intermediary`

Contains the classpath of intermediary mappings of the target version.

### `_crochet*Mappings`
`dependencyScope`

Contains to total intermediary-to-named mappings.

### `_crochet*MappingsClasspath`
`resolvable` extends `_crochet*Mappings`

Resolves `_crochet*Mappings`

### `_crochet*IntermediaryMinecraft`
`dependencyScope`

Contains the minecraft jar in intermediary, and its dependencies.

### `_crochet*IntermediaryMinecraftElements`
`consumable` extends `_crochet*IntermediaryMinecraft`

Contains the minecraft jar in intermediary, and its dependencies, to expose to externally-sourced installations when shared.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:intermediary-<share-tag>:1.0.0`

### `_crochet*IntermediaryToNamedMappings`
`consumable`

Exposes the intermediary-to-named mappings for externally-sourced installations.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:mappings-intermediary-named-<share-tag>:1.0.0`

### `_crochet*NamedToIntermediaryMappings`
`consumable`

Exposes the named-to-intermediary mappings for externally-sourced installations.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.shared-<type-tag>:mappings-named-intermediary-<share-tag>:1.0.0`

### `_crochet*CompileRemapped`
`consumable`

Exposes remapped version of bundle dependencies, for local and cross-project consumption.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.bundle-<type-tag>:<installation-name>-compile-remapped:1.0.0`

### `_crochet*RuntimeRemapped`
`consumable`

Exposes remapped version of bundle dependencies, for local and cross-project consumption.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.bundle-<type-tag>:<installation-name>-runtime-remapped:1.0.0`

### `_crochet*CompileExclude`
`consumable`

Exposes bundle dependencies directly, to exclude from local remapping.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.bundle-<type-tag>:<installation-name>-compile-exclude:1.0.0`

### `_crochet*RuntimeExclude`
`consumable`

Exposes bundle dependencies directly, to exclude from local remapping.

Attributes:
- `dev.lukebemish.crochet.local.distribution`: Installation default

Capabilities:
- `dev.lukebemish.crochet.local.bundle-<type-tag>:<installation-name>-runtime-exclude:1.0.0`

## NeoFormInstallation

### `_crochet*PistonMetaDownloads`
`dependencyScope`

Contains stub dependencies capable of locating various things from piston-meta that TaskGraphRunner needs to produce a
remapped minecraft jar.

### `_crochet*ClientJarPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the original client jar of the target version.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.category` - `library`

Excludes:
- `dev.lukebemish.crochet.mojang-stubs:minecraft-dependencies` (not needed but brought in by the target stub variant)

### `_crochet*ServerJarPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the original server jar of the target version.

Attributes:
- `net.neoforged.distribution` - `server`
- `org.gradle.category` - `library`

Excludes:
- `dev.lukebemish.crochet.mojang-stubs:minecraft-dependencies` (not needed but brought in by the target stub variant)

### `_crochet*ClientMappingsPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the client mappings of the target version.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.category` - `mappings`

### `_crochet*ServerMappingsPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the client mappings of the target version.

Attributes:
- `net.neoforged.distribution` - `server`
- `org.gradle.category` - `mappings`

### `_crochet*VersionJsonPistonMetaDownloads`
`resolvable` extends `_crochet*PistonMetaDownloads`

Produces the version json of the target version.

Attributes:
- `org.gradle.category` - `versionjson`

### `_crochet*NeoFormCompileClasspath`
`resolvable` extends `*MinecraftDependencies`

Contains the compile resolution of the classpath used to decompile Minecraft by TaskGraphRunner. Resolves client
dependencies under the assumption that they are a superset of server, and since TaskGraphRunner is queried with `joined`.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.usage` - `java-api`

### `_crochet*NeoFormRuntimeClasspath`
`resolvable` extends `*MinecraftDependencies`

Contains the runtime resolution of the classpath used to decompile Minecraft by TaskGraphRunner. Resolves client
dependencies under the assumption that they are a superset of server, and since TaskGraphRunner is queried with `joined`.

Attributes:
- `net.neoforged.distribution` - `client`
- `org.gradle.usage` - `java-runtime`

### `*NeoForm`
`dependencyScope`

Contains the neoform to use for this installation.

### `_crochet*NeoFormConfigDependencies`
`resolvable` extends `*NeoForm`

Contains dependencies added to MC by neoform.

Attributes:
- `net.neoforged.distribution` - Installation default

### `_crochet*NeoFormConfig`
`resolvable` extends `*NeoForm`

Contains the neoform configuration itself.

### `*Parchment`
`dependencyScope`

Depends on parchment to use for this installation

### `_crochet*ParchmentData`
`resolvable` extends `*Parchment`

Contains the parchment data to use for this installation.

### `_crochet*NeoForm`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RunImplementation`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

## ExternalMinecraftInstallation

### `_crochet*AssetsProperties`
`dependencyScope`

Depends on the generated assets properties file.

### `_crochet*AssetsPropertiesPath`
`resolvable` extends `_crochet*AssetsProperties`

Contains the generated assets properties file.

### `_crochet*BinaryPath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*Binary`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ResourcesPath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*BinaryLineMapped`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*Resources`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*BinaryLineMappedPath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

## ExternalFabricInstallation

### `_crochet*Loader`
`dependencyScope`

Loader and dependencies, pulled from the external installation.

### `_crochet*IntermediaryMinecraft`
`dependencyScope`

Minecraft in intermediary, and its dependencies, pulled from the external installation.

### `_crochet*MappingsIntermediaryNamed`
`dependencyScope`

Depends on intermediary-to-named mappings, pulled from the external installation.

### `_crochet*MappingsIntermediaryNamedPath`
`resolvable` extends `_crochet*MappingsIntermediaryNamed`

Contains intermediary-to-named mappings, pulled from the external installation.

### `_crochet*MappingsNamedIntermediary`
`dependencyScope`

Depends on named-to-intermediary mappings, pulled from the external installation.

### `_crochet*MappingsNamedIntermediaryPath`
`resolvable` extends `_crochet*MappingsNamedIntermediary`

Contains named-to-intermediary mappings, pulled from the external installation.

### `_crochet*InjectedInterfaces`
`dependencyScope`

Depends on interface injections that should be directly expressed in the produced jar.

### `_crochet*InjectedInterfacesPath`
`resolvable` extends `_crochet*InjectedInterfaces`

Contains interface injections that should be directly expressed in the produced jar.

### `_crochet*CompileRemapped`
`dependencyScope`

Collects remapped bundle dependencies.

### `_crochet*RuntimeRemapped`
`dependencyScope`

Collects remapped bundle dependencies.

### `_crochet*CompileExclude`
`dependencyScope`

Excluded dependencies from remapping for compile.

### `_crochet*RuntimeExclude`
`dependencyScope`

Excluded dependencies from remapping for runtime.

### `_crochet*MappingsClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*Mappings`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

## FabricInstallationLogic

### `mod*Implementation`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RemappingRuntimeClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*VersioningRuntimeClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `mod*CompileOnly`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*VersioningCompileClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `mod*CompileOnlyApi`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `mod*LocalRuntime`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ExcludedRuntimeClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `mod*RuntimeOnly`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `mod*Api`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `mod*LocalImplementation`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ModRuntimeClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RemappingCompileClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ModCompileClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ExcludedCompileClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*VersioningCompileDependencies`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*NonModRuntimeElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ModApiElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RemappedCompileClasspath`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ExcludedCompileDependencies`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RemappedSourcesElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ModRuntimeElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*NonModApiElements`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RunImplementation`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ExcludedRunClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RunModClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RunVersioningClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RunRemappingClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RemappedRunClasspath`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ArtifactsCreationDummy`
`consumable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*ArtifactDummy`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

## Run

### `_crochet*RunImplementation`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

### `_crochet*RunClasspath`
`resolvable` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

## VanillaInstallationLogic

### `_crochet*RunImplementation`
`dependencyScope` (TODO: document extendsFrom)

TODO: document purpose, attributes, etc.

