package test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Test implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Crochet loaded the fabric test mod!");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("Hello from server start!");
        });
    }
}
