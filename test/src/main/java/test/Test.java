package test;

import net.fabricmc.api.ModInitializer;
import test.nocrochet.NoCrochetTest;
import test.subproject.SubprojectTest;

public class Test implements ModInitializer {
    @Override
    public void onInitialize() {
        SubprojectTest.setup();
        NoCrochetTest.setup();
        System.out.println("Crochet loaded the fabric test mod!");
    }
}
