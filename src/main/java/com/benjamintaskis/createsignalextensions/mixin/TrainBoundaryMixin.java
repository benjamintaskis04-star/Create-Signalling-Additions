package com.benjamintaskis.createsignalextensions.mixin;

import com.benjamintaskis.createsignalextensions.content.speed.SpeedRestrictionManager;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation only: Create retains movement, physics, navigation and signalling authority. */
@Mixin(Train.class)
public abstract class TrainBoundaryMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void csa$beforeMovement(Level level, CallbackInfo ci) {
        SpeedRestrictionManager.beforeTrainMovement((Train) (Object) this, level);
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target =
            "Lcom/simibubi/create/content/trains/entity/Carriage;travel(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint;Lcom/simibubi/create/content/trains/entity/TravellingPoint;I)D"))
    private void csa$movementCommand(Level level, CallbackInfo ci) {
        SpeedRestrictionManager.beforeCarriageTravel((Train) (Object) this, level);
    }
    @Inject(method = "tick", at = @At("RETURN"))
    private void csa$afterMovement(Level level, CallbackInfo ci) {
        SpeedRestrictionManager.afterTrainMovement((Train) (Object) this, level);
    }
}
