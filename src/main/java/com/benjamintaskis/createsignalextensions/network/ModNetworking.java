package com.benjamintaskis.createsignalextensions.network;

import com.benjamintaskis.createsignalextensions.CreateSignallingAdditions;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import com.benjamintaskis.createsignalextensions.content.route.RouteInterfaceBlockEntity;
import com.benjamintaskis.createsignalextensions.content.signal.ConvergingSingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SignalAspectLinkManager;
import com.benjamintaskis.createsignalextensions.content.signal.TurnoutDirection;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedBoardBlockEntity;
import com.benjamintaskis.createsignalextensions.content.speed.TrainMovementModeManager;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ModNetworking {
    private ModNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Only the visual board payload uses v2 (added turnout flag). Operational payloads stay at v1.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientPayloads(registrar);
        } else {
            registrar.versioned("2").playToClient(SpeedZoneBoardsPayload.TYPE, SpeedZoneBoardsPayload.STREAM_CODEC, (payload, context) -> {});
            // The payload types still need to be known to a dedicated server, but their
            // GUI handlers must never load client-only Minecraft classes there.
            registrar.playToClient(OpenRouteConfigPayload.TYPE, OpenRouteConfigPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(OpenSmallTheatreConfigPayload.TYPE, OpenSmallTheatreConfigPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(OpenConvergingSignalConfigPayload.TYPE, OpenConvergingSignalConfigPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(OpenSpeedBoardConfigPayload.TYPE, OpenSpeedBoardConfigPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(TrainSpeedHudPayload.TYPE, TrainSpeedHudPayload.STREAM_CODEC,
                    (payload, context) -> {});
        }
        registrar.playToServer(ToggleManualShuntPayload.TYPE, ToggleManualShuntPayload.STREAM_CODEC, ModNetworking::handleToggleManualShunt);
        registrar.playToServer(SaveRouteLabelPayload.TYPE, SaveRouteLabelPayload.STREAM_CODEC,
                ModNetworking::handleSaveRouteLabel);
        registrar.playToServer(RequestRouteConfigPayload.TYPE, RequestRouteConfigPayload.STREAM_CODEC,
                ModNetworking::handleRequestRouteConfig);
        registrar.playToServer(SaveDestinationRulePayload.TYPE, SaveDestinationRulePayload.STREAM_CODEC,
                ModNetworking::handleSaveDestinationRule);
        registrar.playToServer(SaveSmallTheatreCellsPayload.TYPE, SaveSmallTheatreCellsPayload.STREAM_CODEC,
                ModNetworking::handleSaveSmallTheatreCells);
        registrar.playToServer(SetSmallTheatreRowsPayload.TYPE, SetSmallTheatreRowsPayload.STREAM_CODEC,
                ModNetworking::handleSetSmallTheatreRows);
        registrar.playToServer(ClearLoadedRoutesPayload.TYPE, ClearLoadedRoutesPayload.STREAM_CODEC,
                ModNetworking::handleClearLoadedRoutes);
        registrar.playToServer(DeleteLoadedRoutePayload.TYPE, DeleteLoadedRoutePayload.STREAM_CODEC,
                ModNetworking::handleDeleteLoadedRoute);
        registrar.playToServer(SetAutomaticRouteRangePayload.TYPE, SetAutomaticRouteRangePayload.STREAM_CODEC,
                ModNetworking::handleSetAutomaticRouteRange);
        registrar.playToServer(SetRouteLearningPayload.TYPE, SetRouteLearningPayload.STREAM_CODEC,
                ModNetworking::handleSetRouteLearning);
        registrar.playToServer(SetConvergingSignalDirectionPayload.TYPE, SetConvergingSignalDirectionPayload.STREAM_CODEC,
                ModNetworking::handleSetConvergingSignalDirection);
        registrar.playToServer(SetSpeedBoardConfigPayload.TYPE, SetSpeedBoardConfigPayload.STREAM_CODEC,
                ModNetworking::handleSetSpeedBoardConfig);
        registrar.playToServer(SetSignalPlateTextPayload.TYPE, SetSignalPlateTextPayload.STREAM_CODEC, ModNetworking::handleSetSignalPlateText);
        registrar.playToServer(AdjustSignalVerticalPayload.TYPE, AdjustSignalVerticalPayload.STREAM_CODEC, ModNetworking::handleAdjustSignalVertical);
    }

    private static void registerClientPayloads(PayloadRegistrar registrar) {
        registrar.versioned("2").playToClient(SpeedZoneBoardsPayload.TYPE, SpeedZoneBoardsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.benjamintaskis.createsignalextensions.client.SpeedZoneRenderer.accept(payload)));
        registrar.playToClient(OpenRouteConfigPayload.TYPE, OpenRouteConfigPayload.STREAM_CODEC,
                com.benjamintaskis.createsignalextensions.client.ClientPayloadHandlers::handleOpenRouteConfig);
        registrar.playToClient(OpenSmallTheatreConfigPayload.TYPE, OpenSmallTheatreConfigPayload.STREAM_CODEC,
                com.benjamintaskis.createsignalextensions.client.ClientPayloadHandlers::handleOpenSmallTheatreConfig);
        registrar.playToClient(OpenConvergingSignalConfigPayload.TYPE, OpenConvergingSignalConfigPayload.STREAM_CODEC,
                com.benjamintaskis.createsignalextensions.client.ClientPayloadHandlers::handleOpenConvergingSignalConfig);
        registrar.playToClient(OpenSpeedBoardConfigPayload.TYPE, OpenSpeedBoardConfigPayload.STREAM_CODEC,
                com.benjamintaskis.createsignalextensions.client.ClientPayloadHandlers::handleOpenSpeedBoardConfig);
        registrar.playToClient(TrainSpeedHudPayload.TYPE, TrainSpeedHudPayload.STREAM_CODEC,
                com.benjamintaskis.createsignalextensions.client.ClientPayloadHandlers::handleTrainSpeedHud);
    }




    private static void handleToggleManualShunt(ToggleManualShuntPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Train train = Create.RAILWAYS.trains.get(payload.trainId());
        if (train == null || train.carriages == null) return;
        boolean controls=false;
        for (var carriage: train.carriages) { var entity=carriage.anyAvailableEntity(); if(entity!=null && entity.getControllingPlayer().map(player.getUUID()::equals).orElse(false)){controls=true;break;} }
        if (!controls) return;
        boolean enabled=TrainMovementModeManager.toggleManualShunt(level, train, player.getUUID());
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(enabled ? "Shunt mode enabled — 25 km/h" : "Shunt mode disabled"), true);
    }

    private static void handleRequestRouteConfig(RequestRouteConfigPayload payload,
                                                 net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;

        if (player.distanceToSqr(
                payload.pos().getX() + 0.5,
                payload.pos().getY() + 0.5,
                payload.pos().getZ() + 0.5) > 64.0)
            return;

        BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
        if (blockEntity instanceof RouteInterfaceBlockEntity routeInterface)
            routeInterface.openConfigurationScreen(player, true, payload.sessionId());
    }


    private static void handleSaveDestinationRule(SaveDestinationRulePayload payload,
                                                   net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0)
            return;
        BlockEntity be = player.level().getBlockEntity(payload.pos());
        if (!(be instanceof RouteInterfaceBlockEntity routeInterface))
            return;

        if (payload.delete())
            routeInterface.removeDestinationRule(payload.oldPattern());
        else
            routeInterface.setDestinationRule(payload.oldPattern(), payload.pattern(), payload.theatreIndication(), payload.smallTheatreIndications());
    }

    private static void handleSaveRouteLabel(SaveRouteLabelPayload payload,
                                             net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;

        if (player.distanceToSqr(
                payload.pos().getX() + 0.5,
                payload.pos().getY() + 0.5,
                payload.pos().getZ() + 0.5) > 64.0)
            return;

        BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
        if (!(blockEntity instanceof RouteInterfaceBlockEntity routeInterface))
            return;

        String label = payload.label().trim();
        if (label.length() > 32)
            label = label.substring(0, 32);

        if (label.isEmpty())
            routeInterface.clearRouteLabel(payload.routeKey());
        else
            routeInterface.setRouteLabel(payload.routeKey(), label);

        routeInterface.setTheatreIndication(payload.routeKey(), payload.theatreIndication());
        routeInterface.setRouteType(payload.routeKey(), payload.routeType());
        routeInterface.setRouteIndication(payload.routeKey(), payload.routeIndication());

        if (CommonConfig.signalDebug())
            CreateSignallingAdditions.LOGGER.info(
                    "Route Interface {} GUI updated route '{}' to label '{}', theatre '{}', route type '{}', indication '{}'",
                    payload.pos(), payload.routeKey(), label, payload.theatreIndication(), payload.routeType(), payload.routeIndication());
    }

    private static void handleSaveSmallTheatreCells(SaveSmallTheatreCellsPayload payload,
                                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX()+0.5,payload.pos().getY()+0.5,payload.pos().getZ()+0.5)>64.0) return;
        BlockEntity be=player.level().getBlockEntity(payload.pos());
        if (be instanceof RouteInterfaceBlockEntity ri) ri.setSmallTheatreIndications(payload.routeKey(),payload.cells());
    }


    private static void handleClearLoadedRoutes(ClearLoadedRoutesPayload payload,
                                                net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;
        BlockEntity be = player.level().getBlockEntity(payload.pos());
        if (!(be instanceof RouteInterfaceBlockEntity routeInterface)) return;
        routeInterface.clearLoadedRoutes();
        routeInterface.openConfigurationScreen(player, true, payload.sessionId());
    }

    private static void handleDeleteLoadedRoute(DeleteLoadedRoutePayload payload,
                                                net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;
        BlockEntity be = player.level().getBlockEntity(payload.pos());
        if (!(be instanceof RouteInterfaceBlockEntity routeInterface)) return;
        routeInterface.deleteLoadedRoute(payload.routeKey());
        routeInterface.openConfigurationScreen(player, true, payload.sessionId());
    }


    private static void handleSetAutomaticRouteRange(SetAutomaticRouteRangePayload payload,
                                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;
        BlockEntity be = player.level().getBlockEntity(payload.pos());
        if (!(be instanceof RouteInterfaceBlockEntity routeInterface)) return;
        routeInterface.setAutomaticRouteRange(payload.range());
        routeInterface.openConfigurationScreen(player, true, payload.sessionId());
    }

    private static void handleSetSmallTheatreRows(SetSmallTheatreRowsPayload payload,
                                                   net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX()+0.5,payload.pos().getY()+0.5,payload.pos().getZ()+0.5)>64.0) return;
        BlockEntity be=player.level().getBlockEntity(payload.pos());
        if (be instanceof com.benjamintaskis.createsignalextensions.content.theatre.SmallTheatreBoxBlockEntity small)
            small.setRows(payload.rows());
    }
    private static void handleSetRouteLearning(SetRouteLearningPayload payload,
                                                net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX()+0.5,payload.pos().getY()+0.5,payload.pos().getZ()+0.5)>64.0) return;
        BlockEntity be=player.level().getBlockEntity(payload.pos());
        if (be instanceof RouteInterfaceBlockEntity ri) {
            ri.setAllowNewRoutes(payload.allowNewRoutes());
            // Return the authoritative server state immediately instead of waiting for
            // the Route Interface screen's next periodic refresh.
            ri.openConfigurationScreen(player, true, payload.sessionId());
        }
    }

    private static void handleSetConvergingSignalDirection(SetConvergingSignalDirectionPayload payload,
                                                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
        if (!(level.getBlockState(payload.pos()).getBlock() instanceof ConvergingSingleColourLightSignalBlock)) return;

        TurnoutDirection direction = "RIGHT".equalsIgnoreCase(payload.direction())
                ? TurnoutDirection.RIGHT : TurnoutDirection.LEFT;
        SignalAspectLinkManager.get(level).setConvergingDirection(payload.pos(), direction);
    }

    private static void handleSetSpeedBoardConfig(SetSpeedBoardConfigPayload payload,
                                                   net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX()+0.5,payload.pos().getY()+0.5,payload.pos().getZ()+0.5)>64.0) return;
        BlockEntity be=player.level().getBlockEntity(payload.pos());
        if (be instanceof SpeedBoardBlockEntity board)
            board.setConfiguration(payload.defaultKmh(), payload.muKmh(), payload.highKmh(), payload.muOverridden(), payload.highOverridden(), payload.turnoutMode());
    }

    private static void handleSetSignalPlateText(SetSignalPlateTextPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) { if (!(context.player() instanceof ServerPlayer player)) return; if(player.distanceToSqr(payload.pos().getX()+.5,payload.pos().getY()+.5,payload.pos().getZ()+.5)>64)return; BlockEntity be=player.level().getBlockEntity(payload.pos()); if(be instanceof com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlockEntity plate) plate.setText(payload.text()); }

    private static void handleAdjustSignalVertical(AdjustSignalVerticalPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (player.distanceToSqr(payload.pos().getX() + .5, payload.pos().getY() + .5, payload.pos().getZ() + .5) > 64) return;
        var level = player.level();
        var state = level.getBlockState(payload.pos());
        if (!state.is(com.benjamintaskis.createsignalextensions.ModTags.HEIGHTVARIABLE_BLOCKS)
                || !com.benjamintaskis.createsignalextensions.content.HeightVariable.supports(state)) return;
        var prop = com.benjamintaskis.createsignalextensions.content.HeightVariable.VERTICAL_OFFSET;
        int oldValue = state.getValue(prop);
        int value = Math.max(com.benjamintaskis.createsignalextensions.content.HeightVariable.MIN,
                Math.min(com.benjamintaskis.createsignalextensions.content.HeightVariable.MAX,
                        oldValue + (payload.delta() < 0 ? -1 : 1)));
        if (value != oldValue) level.setBlock(payload.pos(), state.setValue(prop, value), 3);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "Vertical offset: " + (value - com.benjamintaskis.createsignalextensions.content.HeightVariable.DEFAULT) + " px"), true);
    }

}
