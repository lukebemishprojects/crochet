package test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import test.nocrochet.NoCrochetTest;
import test.subproject.SubprojectTest;

public class Test implements ModInitializer {
    @Override
    public void onInitialize() {
        SubprojectTest.setup();
        NoCrochetTest.setup();
        System.out.println("Crochet loaded the fabric test mod!");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("Hello from server start!");
        });
    }
}
