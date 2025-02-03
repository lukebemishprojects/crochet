package test;

import net.fabricmc.api.ModInitializer;

public class Test implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Crochet loaded the fabric test mod!");
        ServerLifecyceEvents.SERVER_STARTED.register(server -> {
            System.out.println("Hello from server start!");
        });
    }
}
