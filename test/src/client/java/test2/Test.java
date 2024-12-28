package test2;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public class Test implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Crochet loaded the fabric test mod on the client: " + Minecraft.class);
    }
}
