package dev.lukebemish.crochet.mappings;

import dev.lukebemish.taskgraphrunner.model.Input;
import dev.lukebemish.taskgraphrunner.model.InputValue;
import dev.lukebemish.taskgraphrunner.model.MappingsSource;
import dev.lukebemish.taskgraphrunner.model.Value;

public sealed interface MappingsStructure permits ChainedMappingsStructure, FileMappingsStructure, MergedMappingsStructure, MojangOfficialMappingsStructure, ReversedMappingsStructure {
    static MappingsSource toModel(MappingsStructure structure, Input officialMappingsTask) {
        return switch (structure) {
            case ChainedMappingsStructure chainedMappingsStructure -> new MappingsSource.Chained(chainedMappingsStructure.getInputMappings().get().stream().map(struct -> toModel(struct, officialMappingsTask)).toList());
            case FileMappingsStructure fileMappingsStructure -> new MappingsSource.File(new Input.DirectInput(Value.file(fileMappingsStructure.getMappingsFile().getSingleFile().toPath())));
            case MergedMappingsStructure mergedMappingsStructure -> new MappingsSource.Merged(mergedMappingsStructure.getInputMappings().get().stream().map(struct -> toModel(struct, officialMappingsTask)).toList());
            case MojangOfficialMappingsStructure ignored -> {
                var file = new MappingsSource.File(officialMappingsTask);
                file.extension = new InputValue.DirectInput(new Value.StringValue("txt"));
                yield new MappingsSource.Reversed(file);
            }
            case ReversedMappingsStructure reversedMappingsStructure -> new MappingsSource.Reversed(toModel(reversedMappingsStructure.getInputMappings().get(), officialMappingsTask));
        };
    }
}
