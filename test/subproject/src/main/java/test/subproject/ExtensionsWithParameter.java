package test.subproject;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public interface ExtensionsWithParameter<A extends Entity, B> {
    default EntityType.EntityFactory<A> test_subproject$getFactory() {
        throw new UnsupportedOperationException("Mixin failed to apply");
    }
}
