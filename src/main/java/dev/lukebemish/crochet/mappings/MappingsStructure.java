package dev.lukebemish.crochet.mappings;

import dev.lukebemish.taskgraphrunner.model.Input;
import dev.lukebemish.taskgraphrunner.model.MappingsSource;
import dev.lukebemish.taskgraphrunner.model.Output;
import dev.lukebemish.taskgraphrunner.model.Value;

public sealed interface MappingsStructure permits ChainedMappingsStructure, FileMappingsStructure, MergedMappingsStructure, MojangOfficialMappingsStructure, ReversedMappingsStructure {
    static MappingsSource toModel(MappingsStructure structure, Output officialMappingsTask) {
        return switch (structure) {
            case ChainedMappingsStructure chainedMappingsStructure -> new MappingsSource.Chained(chainedMappingsStructure.getInputMappings().get().stream().map(struct -> toModel(struct, officialMappingsTask)).toList());
            case FileMappingsStructure fileMappingsStructure -> new MappingsSource.File(new Input.DirectInput(Value.file(fileMappingsStructure.getMappingsFile().getSingleFile().toPath())));
            case MergedMappingsStructure mergedMappingsStructure -> new MappingsSource.Merged(mergedMappingsStructure.getInputMappings().get().stream().map(struct -> toModel(struct, officialMappingsTask)).toList());
            case MojangOfficialMappingsStructure ignored -> new MappingsSource.Reversed(new MappingsSource.File(new Input.TaskInput(officialMappingsTask)));
            case ReversedMappingsStructure reversedMappingsStructure -> new MappingsSource.Reversed(toModel(reversedMappingsStructure.getInputMappings().get(), officialMappingsTask));
        };
    }
}
