package com.benjamintaskis.createsignalextensions;

import com.tterrag.registrate.util.entry.ItemEntry;

import com.benjamintaskis.createsignalextensions.content.route.SignalLinkingToolItem;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import com.benjamintaskis.createsignalextensions.content.signal.SignalAspectLinkManager;
import com.benjamintaskis.createsignalextensions.content.signal.SignallingChunkManager;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedRestrictionManager;
import com.benjamintaskis.createsignalextensions.content.speed.TrainObserverSpeedMonitor;
import com.benjamintaskis.createsignalextensions.content.speed.ModScheduleInstructions;
import com.benjamintaskis.createsignalextensions.config.ClientConfig;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.benjamintaskis.createsignalextensions.network.ModNetworking;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CreateSignallingAdditions.ID)
public class CreateSignallingAdditions {
    public static final String ID = "create_signal_extensions";
    public static final Logger LOGGER = LogManager.getLogger(ID);
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public static final ItemEntry<SignalLinkingToolItem> SIGNAL_LINKING_TOOL =
            REGISTRATE.item("signal_linking_tool", SignalLinkingToolItem::new).register();


    public CreateSignallingAdditions(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        REGISTRATE.registerEventListeners(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModNetworking::register);
        modBus.addListener(SignallingChunkManager::registerTicketController);
        ModDisplaySources.register();
        ModScheduleInstructions.register();
        ModItems.register();
        ModBlocks.register();
        ModBlockEntityTypes.register();
        NeoForge.EVENT_BUS.addListener(SignalAspectLinkManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(SpeedRestrictionManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(SpeedRestrictionManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.benjamintaskis.createsignalextensions.content.speed.SpeedZoneSync::tick);
        NeoForge.EVENT_BUS.addListener(com.benjamintaskis.createsignalextensions.content.speed.SpeedZoneSync::login);
        NeoForge.EVENT_BUS.addListener(TrainObserverSpeedMonitor::onServerTick);
        NeoForge.EVENT_BUS.addListener(TrainObserverSpeedMonitor::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.benjamintaskis.createsignalextensions.diagnostic.GraphExportCommand::register);
        NeoForge.EVENT_BUS.addListener(com.benjamintaskis.createsignalextensions.diagnostic.GraphExportCommand::stopped);
        LOGGER.info("Create: Signalling Additions 1.5.0 Alpha 1 loaded");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
