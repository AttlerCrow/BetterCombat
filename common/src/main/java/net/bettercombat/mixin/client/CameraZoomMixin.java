package net.bettercombat.mixin.client;

import net.bettercombat.client.studio.EmotePhotoCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the camera further back while a pose is being framed.
 *
 * <p>Applied after the camera has finished positioning itself, so it stacks on top of the normal
 * third-person distance rather than replacing the collision handling that keeps the view out of
 * walls - {@code move} is the same call vanilla uses for that offset.
 */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {

    @Shadow
    protected abstract void move(float distance, float vertical, float horizontal);

    @Shadow
    public abstract boolean isDetached();

    @Inject(method = "update", at = @At("TAIL"))
    private void bettercombat$applyEmoteZoom(DeltaTracker deltaTracker, CallbackInfo ci) {
        float zoom = EmotePhotoCamera.zoom();
        if (zoom > 0F && isDetached()) {
            move(-zoom, 0F, 0F);
        }
    }
}
