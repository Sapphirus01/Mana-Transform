package by.sapphirus.manatransform.client;

import by.sapphirus.manatransform.ManaTransform;
import by.sapphirus.manatransform.client.screen.ManaTransformConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = ManaTransform.MOD_ID, value = Dist.CLIENT)
public final class ManaTransformClient {
    private ManaTransformClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        IConfigScreenFactory screenFactory =
                (container, parent) -> new ManaTransformConfigScreen(parent);
        ModList.get()
                .getModContainerById(ManaTransform.MOD_ID)
                .orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class, screenFactory);
    }
}
