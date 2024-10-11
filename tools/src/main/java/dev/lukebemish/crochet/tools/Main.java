package dev.lukebemish.crochet.tools;

import picocli.CommandLine;

@CommandLine.Command(name = "tools")
public class Main {
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main())
            .addSubcommand(new TransformAccessWideners())
            .addSubcommand(new TransformInterfaceInjection())
            .addSubcommand(new RemapMods())
            .addSubcommand(new CombineSources())
            .addSubcommand(new SeparateModifiedSources())
            .execute(args)
        );
    }
}
