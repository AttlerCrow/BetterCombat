package net.bettercombat.mixin.client;

import net.bettercombat.client.animation.EmoteItemAnchor;
import net.bettercombat.client.animation.EmoteItemAnchorHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Storage for {@link EmoteItemAnchorHolder} on the player's render state. */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements EmoteItemAnchorHolder {

    @Unique
    @Nullable
    private EmoteItemAnchor bettercombat$emoteItemAnchor;

    @Unique
    @Nullable
    private EmoteItemAnchor bettercombat$emoteOffHandAnchor;

    @Unique
    private boolean bettercombat$hidingEmoteItems;

    @Override
    public @Nullable EmoteItemAnchor bettercombat$getEmoteItemAnchor() {
        return bettercombat$emoteItemAnchor;
    }

    @Override
    public void bettercombat$setEmoteItemAnchor(@Nullable EmoteItemAnchor anchor) {
        this.bettercombat$emoteItemAnchor = anchor;
    }

    @Override
    public @Nullable EmoteItemAnchor bettercombat$getEmoteOffHandAnchor() {
        return bettercombat$emoteOffHandAnchor;
    }

    @Override
    public void bettercombat$setEmoteOffHandAnchor(@Nullable EmoteItemAnchor anchor) {
        this.bettercombat$emoteOffHandAnchor = anchor;
    }

    @Override
    public boolean bettercombat$isHidingEmoteItems() {
        return bettercombat$hidingEmoteItems;
    }

    @Override
    public void bettercombat$setHidingEmoteItems(boolean hiding) {
        this.bettercombat$hidingEmoteItems = hiding;
    }
}
