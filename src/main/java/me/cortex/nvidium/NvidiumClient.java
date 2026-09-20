package me.cortex.nvidium;

import me.cortex.nvidium.persist.PersistCommands;
import me.cortex.nvidium.persist.PersistImport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class NvidiumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PersistCommands.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> PersistImport.tick());
    }
}
