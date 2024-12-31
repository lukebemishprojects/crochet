# crochet

Crochet is a gradle plugin for setting up environments to write Minecraft mods. Documentation is WIP.

## Options

Some behavior may not effect crochet's behavior but could be useful to configure, especially depending on the machine
being used. These properties can be configured per-project, or shared across all projects in your user-level
`gradle.properties` file (by default stored at `~/.gradle/gradle.properties`):

| Property                                              | Description                                                                                 |
|-------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `dev.lukebemish.taskgraphrunner.decompile.maxHeap`    | How much memory should be made available to the decompiler; defaults to 3G.                 |
| `dev.lukebemish.taskgraphrunner.decompile.maxThreads` | How many threads the decompiler should use; defaults to the number of available processors. |
