package net.bettercombat.network;

import com.google.gson.Gson;
import net.bettercombat.BetterCombatMod;
import net.bettercombat.api.fx.ParticlePlacement;
import net.bettercombat.api.fx.TrailAppearance;
import net.bettercombat.config.ServerConfig;
import net.bettercombat.logic.AnimatedHand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Packets {
    public record C2S_AttackRequest(int comboCount, boolean isSneaking, int selectedSlot, int cursorTarget, int[] entityIds) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "c2s_request_attack");
        public static final CustomPacketPayload.Type<C2S_AttackRequest> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, C2S_AttackRequest> CODEC = StreamCodec.ofMember(C2S_AttackRequest::write, C2S_AttackRequest::read);

        public C2S_AttackRequest(int comboCount, boolean isSneaking, int selectedSlot, @Nullable Entity cursorTarget, List<Entity> entities) {
            this(comboCount, isSneaking, selectedSlot, convertEntity(cursorTarget), convertEntityList(entities));
        }

        private static int[] convertEntityList(List<Entity> entities) {
            int[] ids = new int[entities.size()];
            for(int i = 0; i < entities.size(); i++) {
                var entity = entities.get(i);
                ids[i] = entity.getId();
            }
            return ids;
        }
        private static int convertEntity(@Nullable Entity entity) {
            if (entity == null) { return -1; }
            return entity.getId();
        }

        public static boolean UseVanillaPacket = true;
        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(comboCount);
            buffer.writeBoolean(isSneaking);
            buffer.writeInt(selectedSlot);
            buffer.writeInt(cursorTarget);
            buffer.writeVarIntArray(entityIds);
        }

        public static C2S_AttackRequest read(FriendlyByteBuf buffer) {
            int comboCount = buffer.readInt();
            boolean isSneaking = buffer.readBoolean();
            int selectedSlot = buffer.readInt();
            int cursorTarget = buffer.readInt();
            int[] ids = buffer.readVarIntArray();
            return new C2S_AttackRequest(comboCount, isSneaking, selectedSlot, cursorTarget, ids);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record SwingParticles(List<ParticlePlacement> particles, TrailAppearance appearance) {
        public static final SwingParticles EMPTY = new SwingParticles(List.of(), new TrailAppearance());
    }
    public record AttackAnimation(int playerId, AnimatedHand animatedHand, String animationName, float length, float upswing, float weaponRange, int upswingTicks, SwingParticles particles) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "attack_animation");
        public static final CustomPacketPayload.Type<AttackAnimation> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackAnimation> CODEC = StreamCodec.ofMember(AttackAnimation::write, AttackAnimation::read);

        private static final Gson gson = new Gson();
        public static String StopSymbol = "!STOP!";
        public static AttackAnimation stop(int playerId, int length) { return new AttackAnimation(playerId, AnimatedHand.MAIN_HAND, StopSymbol, length, 0, 0, 0, SwingParticles.EMPTY); }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(playerId);
            buffer.writeInt(animatedHand.ordinal());
            buffer.writeUtf(animationName);
            buffer.writeFloat(length);
            buffer.writeFloat(upswing);
            buffer.writeFloat(weaponRange);
            buffer.writeInt(upswingTicks);
            // Write list of particles
            buffer.writeUtf(gson.toJson(particles));
        }

        public static AttackAnimation read(FriendlyByteBuf buffer) {
            int playerId = buffer.readInt();
            var animatedHand = AnimatedHand.values()[buffer.readInt()];
            String animationName = buffer.readUtf();
            float length = buffer.readFloat();
            float upswing = buffer.readFloat();
            float weaponRange = buffer.readFloat();
            int upswingTicks = buffer.readInt();
            var json = buffer.readUtf();
            var particles = gson.fromJson(json, SwingParticles.class);
            return new AttackAnimation(playerId, animatedHand, animationName, length, upswing, weaponRange, upswingTicks, particles);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record AttackSound(double x, double y, double z, String soundId, float volume, float pitch, long seed) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "attack_sound");
        public static final CustomPacketPayload.Type<AttackSound> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackSound> CODEC = StreamCodec.ofMember(AttackSound::write, AttackSound::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
            buffer.writeUtf(soundId);
            buffer.writeFloat(volume);
            buffer.writeFloat(pitch);
            buffer.writeLong(seed);
        }

        public static AttackSound read(FriendlyByteBuf buffer) {
            var x = buffer.readDouble();
            var y = buffer.readDouble();
            var z = buffer.readDouble();
            var soundId = buffer.readUtf();
            var volume = buffer.readFloat();
            var pitch = buffer.readFloat();
            var seed = buffer.readLong();
            return new AttackSound(x, y, z, soundId, volume, pitch, seed);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record WeaponRegistrySync(boolean compressed, List<String> chunks) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "weapon_registry");
        public static final CustomPacketPayload.Type<WeaponRegistrySync> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, WeaponRegistrySync> CODEC = StreamCodec.ofMember(WeaponRegistrySync::write, WeaponRegistrySync::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(compressed);
            buffer.writeInt(chunks.size());
            for (var chunk: chunks) {
                buffer.writeUtf(chunk);
            }
        }

        public static WeaponRegistrySync read(FriendlyByteBuf buffer) {
            var compressed = buffer.readBoolean();
            var chunkCount = buffer.readInt();
            var chunks = new ArrayList<String>();
            for (int i = 0; i < chunkCount; ++i) {
                chunks.add(buffer.readUtf());
            }
            return new WeaponRegistrySync(compressed, chunks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record C2S_BlockHit(BlockPos pos) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "block_hit");
        public static final CustomPacketPayload.Type<C2S_BlockHit> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, C2S_BlockHit> CODEC = BlockPos.STREAM_CODEC.map(C2S_BlockHit::new, C2S_BlockHit::pos).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record ConfigSync(String json) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "config_sync");
        public static final CustomPacketPayload.Type<ConfigSync> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, ConfigSync> CODEC = StreamCodec.ofMember(ConfigSync::write, ConfigSync::read);

        private static final Gson gson = new Gson();
        public static String serialize(ServerConfig config) {
            return gson.toJson(config);
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(json);
        }

        public static ConfigSync read(FriendlyByteBuf buffer) {
            var json = buffer.readUtf();
            return new ConfigSync(json);
        }

        public ServerConfig deserialized() {
            return gson.fromJson(json, ServerConfig.class);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * Server-driven animation playback.
     *
     * <p>Separate from {@link AttackAnimation} on purpose. That one is a relay of a swing the client
     * itself started, so the client deliberately ignores it for the local player to avoid playing the
     * animation twice. A server-driven animation - a skill, a scripted sequence - has no local
     * counterpart, and the player performing it is precisely who needs to see it.
     */
    /**
     * Tells one client whether it may attack, and for how long it may not.
     *
     * <p>The guards that read this have always been in {@code AttackInteractor}; what never existed
     * on a Paper server was anything to set the flag they read. Upstream carries it in a data
     * attachment synced by a modded server, so on Paper the client's flags were permanently zero and
     * those guards never fired. This packet is the missing half.
     *
     * <p>{@code durationTicks} is how long the server expects the block to last. The client counts it
     * down itself so that a lost release packet, or a server that stopped mid-stun, cannot leave
     * somebody unable to swing until they restart the game.
     */
    public record CombatState(boolean attacksDisabled, int durationTicks) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "s2c_combat_state");
        public static final CustomPacketPayload.Type<CombatState> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, CombatState> CODEC = StreamCodec.ofMember(CombatState::write, CombatState::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(attacksDisabled);
            buffer.writeInt(durationTicks);
        }

        public static CombatState read(FriendlyByteBuf buffer) {
            boolean attacksDisabled = buffer.readBoolean();
            int durationTicks = buffer.readInt();
            return new CombatState(attacksDisabled, durationTicks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record ForcedAnimation(int playerId, AnimatedHand animatedHand, String animationName,
                                  float length, float upswing) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "s2c_play_animation");
        public static final CustomPacketPayload.Type<ForcedAnimation> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, ForcedAnimation> CODEC = StreamCodec.ofMember(ForcedAnimation::write, ForcedAnimation::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(playerId);
            buffer.writeInt(animatedHand.ordinal());
            buffer.writeUtf(animationName);
            buffer.writeFloat(length);
            buffer.writeFloat(upswing);
        }

        public static ForcedAnimation read(FriendlyByteBuf buffer) {
            int playerId = buffer.readInt();
            var animatedHand = AnimatedHand.values()[buffer.readInt()];
            String animationName = buffer.readUtf();
            float length = buffer.readFloat();
            float upswing = buffer.readFloat();
            return new ForcedAnimation(playerId, animatedHand, animationName, length, upswing);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * Server-driven emote playback.
     *
     * <p>Deliberately not a reuse of {@link ForcedAnimation}. Emotes play on their own animation
     * layer with constant speed, so the two fields that packet carries for combat - the animated
     * hand and the upswing ratio - have no meaning here, and a shared packet would have to keep
     * sending values the receiver ignores. Keeping them apart is also what lets a server speak one
     * feature without the other: a client that never announces this channel simply gets no emotes.
     *
     * <p>{@code stop} is a flag rather than a sentinel animation name because there is exactly one
     * emote layer to stop; the name and length are then ignored.
     *
     * <p>{@code hidePose} suppresses the idle weapon pose for the duration. A katana grip is a
     * looping animation on its own layer, so without this it keeps holding the weapon out while the
     * emote moves the same arms - the two read as one broken pose. Emotes that should keep the
     * weapon visible leave it false.
     *
     * <p>{@code photoCamera} forces third person and stops the model turning to follow the view, so
     * the camera can be swung around a held pose without the subject rotating with it.
     *
     * <p>{@code keepOnAttack} stops a swing ending the emote on the client. The client cuts its own
     * emote the instant a swing starts, which is what makes that feel immediate - but an emote the
     * server intends to keep running has to survive it, or the two sides disagree about what is
     * playing.
     *
     * <p>{@code thirdPerson} pulls the view out without freezing the facing, which is what a line
     * wants: everyone should see the dance, but only the person steering holds a heading.
     *
     * <p>{@code hideItems} leaves the held items undrawn - for a pose whose hands are busy, where
     * even a well placed weapon reads as a bug.
     *
     * <p>{@code cameraHeightOffset} drops the eye the pose drops the head, in blocks. A seated model
     * whose camera stays at standing height reads as floating above your own body.
     *
     * <p>The item anchor, when present, moves the held item off the hand and onto a spot relative to
     * the torso - the back, the ground, a hip. Only written when set, because most emotes do not use
     * one and six floats per packet is not free.
     *
     * <p>{@code anchorOnly} updates the anchors of an emote already playing without restarting it.
     * That is what makes tuning an anchor live bearable: retriggering the animation on every nudge
     * would snap the pose back to its first frame each time.
     */
    public record PlayEmote(int playerId, String animationName, float length,
                            boolean stop, boolean hidePose, boolean photoCamera, boolean hideItems,
                            boolean thirdPerson, boolean keepOnAttack,
                            float cameraHeightOffset,
                            boolean hasItemAnchor, float itemX, float itemY, float itemZ,
                            float itemPitch, float itemYaw, float itemRoll,
                            boolean hasOffHandAnchor, float offX, float offY, float offZ,
                            float offPitch, float offYaw, float offRoll,
                            boolean anchorOnly,
                            boolean lockBody, float bodyYaw)
            implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "s2c_play_emote");
        public static final CustomPacketPayload.Type<PlayEmote> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, PlayEmote> CODEC = StreamCodec.ofMember(PlayEmote::write, PlayEmote::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(playerId);
            buffer.writeUtf(animationName);
            buffer.writeFloat(length);
            buffer.writeBoolean(stop);
            buffer.writeBoolean(hidePose);
            buffer.writeBoolean(photoCamera);
            buffer.writeBoolean(hideItems);
            buffer.writeBoolean(thirdPerson);
            buffer.writeBoolean(keepOnAttack);
            buffer.writeFloat(cameraHeightOffset);
            buffer.writeBoolean(hasItemAnchor);
            if (hasItemAnchor) {
                buffer.writeFloat(itemX);
                buffer.writeFloat(itemY);
                buffer.writeFloat(itemZ);
                buffer.writeFloat(itemPitch);
                buffer.writeFloat(itemYaw);
                buffer.writeFloat(itemRoll);
            }
            buffer.writeBoolean(anchorOnly);
            buffer.writeBoolean(hasOffHandAnchor);
            if (hasOffHandAnchor) {
                buffer.writeFloat(offX);
                buffer.writeFloat(offY);
                buffer.writeFloat(offZ);
                buffer.writeFloat(offPitch);
                buffer.writeFloat(offYaw);
                buffer.writeFloat(offRoll);
            }
            buffer.writeBoolean(lockBody);
            buffer.writeFloat(bodyYaw);
        }

        public static PlayEmote read(FriendlyByteBuf buffer) {
            int playerId = buffer.readInt();
            String animationName = buffer.readUtf();
            float length = buffer.readFloat();
            boolean stop = buffer.readBoolean();
            boolean hidePose = buffer.readBoolean();
            boolean photoCamera = buffer.readBoolean();
            boolean hideItems = buffer.readBoolean();
            boolean thirdPerson = buffer.readBoolean();
            boolean keepOnAttack = buffer.readBoolean();
            float cameraHeightOffset = buffer.readFloat();
            boolean hasItemAnchor = buffer.readBoolean();
            float x = 0F, y = 0F, z = 0F, pitch = 0F, yaw = 0F, roll = 0F;
            if (hasItemAnchor) {
                x = buffer.readFloat();
                y = buffer.readFloat();
                z = buffer.readFloat();
                pitch = buffer.readFloat();
                yaw = buffer.readFloat();
                roll = buffer.readFloat();
            }
            boolean anchorOnly = buffer.readBoolean();
            boolean hasOffHandAnchor = buffer.readBoolean();
            float ox = 0F, oy = 0F, oz = 0F, opitch = 0F, oyaw = 0F, oroll = 0F;
            if (hasOffHandAnchor) {
                ox = buffer.readFloat();
                oy = buffer.readFloat();
                oz = buffer.readFloat();
                opitch = buffer.readFloat();
                oyaw = buffer.readFloat();
                oroll = buffer.readFloat();
            }
            boolean lockBody = buffer.readBoolean();
            float bodyYaw = buffer.readFloat();
            return new PlayEmote(playerId, animationName, length, stop, hidePose, photoCamera, hideItems, thirdPerson, keepOnAttack,
                    cameraHeightOffset,
                    hasItemAnchor, x, y, z, pitch, yaw, roll,
                    hasOffHandAnchor, ox, oy, oz, opitch, oyaw, oroll, anchorOnly,
                    lockBody, bodyYaw);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * The anchor a player positioned in the emote studio, sent back so the server can record it.
     *
     * <p>Carries no emote id: the server already knows which emote the player is performing, and
     * trusting the client to name it would let a stray packet rewrite an unrelated entry.
     */
    public record C2S_EmoteAnchor(boolean offHand, float x, float y, float z,
                                  float pitch, float yaw, float roll) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "c2s_emote_anchor");
        public static final CustomPacketPayload.Type<C2S_EmoteAnchor> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, C2S_EmoteAnchor> CODEC =
                StreamCodec.ofMember(C2S_EmoteAnchor::write, C2S_EmoteAnchor::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(offHand);
            buffer.writeFloat(x);
            buffer.writeFloat(y);
            buffer.writeFloat(z);
            buffer.writeFloat(pitch);
            buffer.writeFloat(yaw);
            buffer.writeFloat(roll);
        }

        public static C2S_EmoteAnchor read(FriendlyByteBuf buffer) {
            return new C2S_EmoteAnchor(buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * Drives the emote studio from a server command, so it needs no key binding at all.
     *
     * <p>Key bindings were the first approach and were the wrong one: every free letter is already
     * taken by a shader or map mod, and a binding that silently loses a race with another mod looks
     * exactly like a broken feature.
     *
     * @param action 0 toggle, 1 switch hand, 2 save, 3 close
     */
    public record S2C_EmoteStudio(int action) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "s2c_emote_studio");
        public static final CustomPacketPayload.Type<S2C_EmoteStudio> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, S2C_EmoteStudio> CODEC =
                StreamCodec.ofMember(S2C_EmoteStudio::write, S2C_EmoteStudio::read);

        public static final int TOGGLE = 0;
        public static final int SWITCH_HAND = 1;
        public static final int SAVE = 2;
        public static final int CLOSE = 3;

        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(action);
        }

        public static S2C_EmoteStudio read(FriendlyByteBuf buffer) {
            return new S2C_EmoteStudio(buffer.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * Reports whether the anchor studio is open on this client.
     *
     * <p>The server needs to know because an emote being adjusted has to be uncancellable: every rule
     * that normally ends one - a click, a nudge of movement, a stray hit - would otherwise cut the
     * pose out from under the thing being positioned. Sent by the client rather than assumed from the
     * command, because the studio can also be closed with escape.
     */
    public record C2S_EmoteStudioState(boolean open) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "c2s_emote_studio_state");
        public static final CustomPacketPayload.Type<C2S_EmoteStudioState> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, C2S_EmoteStudioState> CODEC =
                StreamCodec.ofMember(C2S_EmoteStudioState::write, C2S_EmoteStudioState::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(open);
        }

        public static C2S_EmoteStudioState read(FriendlyByteBuf buffer) {
            return new C2S_EmoteStudioState(buffer.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * Reports that the player is looking around without meaning to change where they are going.
     *
     * <p>Only the client knows a mouse button is held, and a line steered by the view of whoever is
     * in front would otherwise swing around every time they turned to look at something.
     */
    public record C2S_EmoteFreeLook(boolean active) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "c2s_emote_free_look");
        public static final CustomPacketPayload.Type<C2S_EmoteFreeLook> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, C2S_EmoteFreeLook> CODEC =
                StreamCodec.ofMember(C2S_EmoteFreeLook::write, C2S_EmoteFreeLook::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(active);
        }

        public static C2S_EmoteFreeLook read(FriendlyByteBuf buffer) {
            return new C2S_EmoteFreeLook(buffer.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    /**
     * One press of the attack or use button, sent as the client sees it.
     *
     * <p>This mod owns the mouse: for any registered weapon it cancels {@code Minecraft.startAttack},
     * so no swing packet and no block action leave the client, and it cancels
     * {@code Minecraft.startUseItem} during an upswing, so a right click inside one leaves nothing
     * either. A server that wants to read clicks as <em>input</em> - NightFantasy's skill combos are
     * typed as sequences of clicks - cannot see them at all unless we say so.
     *
     * <p>Sent for the raw press, before any of this mod's rules have run: no weapon check, no attack
     * cooldown, no target. A press that starts no attack is still a press, and a combo gated on the
     * weapon's cooldown would drop the middle of a sequence typed quickly.
     *
     * <p>{@code button} is 0 for attack and 1 for use. {@code sequence} counts presses this session
     * so a gap in a server log separates a dropped packet from a player who did not click.
     */
    public record C2S_CombatInput(int button, int sequence) implements CustomPacketPayload {
        public static final int BUTTON_LEFT = 0;
        public static final int BUTTON_RIGHT = 1;

        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "c2s_combat_input");
        public static final CustomPacketPayload.Type<C2S_CombatInput> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, C2S_CombatInput> CODEC =
                StreamCodec.ofMember(C2S_CombatInput::write, C2S_CombatInput::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(button);
            buffer.writeVarInt(sequence);
        }

        public static C2S_CombatInput read(FriendlyByteBuf buffer) {
            int button = buffer.readVarInt();
            int sequence = buffer.readVarInt();
            return new C2S_CombatInput(button, sequence);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }

    public record Ack(String code) implements CustomPacketPayload {
        public static Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "ack");
        public static final CustomPacketPayload.Type<Ack> PACKET_ID = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<FriendlyByteBuf, Ack> CODEC = StreamCodec.ofMember(Ack::write, Ack::read);

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(code);
        }

        public static Ack read(FriendlyByteBuf buffer) {
            var code = buffer.readUtf();
            return new Ack(code);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_ID;
        }
    }
}
