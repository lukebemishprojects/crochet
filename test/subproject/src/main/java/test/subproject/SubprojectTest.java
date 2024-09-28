package test.subproject;

import net.fabricmc.api.ModInitializer;
import test.nocrochet.NoCrochetTest;

public class SubprojectTest implements ModInitializer {
    @Override
    public void onInitialize() {
        NoCrochetTest.setup();
        System.out.println("Crochet loaded the fabric test mod!");
    }

    public static void setup() {}
}
