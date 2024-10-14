package test.subproject.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import test.subproject.ExtensionsWithParameter;

@Mixin(EntityType.class)
public interface EntityTypeMixin<T extends Entity> extends ExtensionsWithParameter<T, Entity> {
    @Override
    default EntityType.EntityFactory<T> test_subproject$getFactory() {
        return test_subproject$factoryAccessor();
    }

    @Accessor(value = "factory")
    EntityType.EntityFactory<T> test_subproject$factoryAccessor();
}
