package test.subproject;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import test.nocrochet.NoCrochetTest;

public class SubprojectTest implements ModInitializer {
    @Override
    public void onInitialize() {
        NoCrochetTest.setup();
        System.out.println("Crochet loaded the fabric test mod!");
        System.out.printf("Redstone has ID %s%n", BuiltInRegistries.ITEM.getKey(Items.REDSTONE));
    }

    public static void setup() {}
}
