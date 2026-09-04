package net.bettercombat.client.compat;

import net.bettercombat.BetterCombatMod;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/**
 * Keeps Entity Model Features in charge of the <em>armor</em> model while the player model is handed
 * back to vanilla.
 *
 * <p>NightFantasy's 3D armor has two render paths. One is a packed-geometry trick in the resource
 * pack's own {@code entity.vsh}, which rebuilds each piece from the four corners of the vanilla armor
 * cube it rides on and indexes faces by vertex id
 * ({@code (gl_VertexID - gl_BaseVertexARB) % (nboxes * 24)}). That indexing only survives while the
 * armor is drawn exactly as vanilla draws it; under a player animation it runs past {@code nboxes},
 * every carrier quad takes the hidden branch, and the armor vanishes outright rather than merely
 * looking wrong. The other path is EMF's: a real CEM model per slot that EMF poses like any other
 * part, which measurement showed survives emotes and weapon poses intact.
 *
 * <p>Three things push a player off the good path onto the fragile one. {@link EmfEmotePause} does
 * it so an emote can animate vanilla model parts. {@code emf_compat_better_combat} does it for the
 * first-person hands only - it returns false unless {@code EMFCompatCore.isLocalPlayerInFirstPerson}.
 * And EMF does it to itself: it registers {@code PALCompat}, "any active player animation", as a
 * vanilla-model condition as soon as it sees {@code bendable_cuboids} installed, which caught every
 * Better Combat grip and attack too - see {@code EmfPalCompatMixin}, which narrows it back to
 * emotes.
 *
 * <p>This class is the guard used by {@code EmfVanillaModelLayerMixin} to undo that push for the
 * armor only. Wired reflectively so EMF stays optional, matching {@link EmfEmotePause}.
 */
public final class EmfArmorModelBridge {

    private static final String EMF_API = "traben.entity_model_features.EMFAnimationApi";
    private static final String EMF_CONTEXT =
            "traben.entity_model_features.models.animation.EMFAnimationEntityContext";

    /**
     * Name the generated armor .jem expressions test. Registered as a plain identifier, the way EMF's
     * own built-ins ({@code is_paused}, {@code is_gliding}) are spelled - the registry keys singleton
     * variables by exactly the string given, with no namespace prefix added.
     */
    public static final String ANIMATING_VARIABLE = "nf_animating";

    /**
     * Name the generated armor .jem tests to know it is being drawn once rather than once per packed
     * equipment layer. The latch and the rig's pass gate both branch on it, so the same pack works
     * on a client that collapses the layers and on one that does not.
     */
    public static final String SINGLE_PASS_VARIABLE = "nf_single_pass";

    private static Method isEntityForcedToVanillaModel;
    private static boolean available;
    private static java.lang.reflect.Field modelNameField;
    private static java.lang.reflect.Method uuidMethod;

    /**
     * Cached answer for {@link #ANIMATING_VARIABLE}, valid for one entity's animation pass.
     *
     * <p>The generated armor gates every bone on this name, and an armor piece is drawn once per
     * packed equipment layer - 27 of them for the chestplate. That multiplies out to thousands of
     * reads per frame, and each one lands in EMF's {@code isEntityForcedToVanillaModel}, which builds
     * a string from the entity type before it even reaches the registered conditions. The value
     * cannot change between the passes of a single entity, so it is computed once per entity and
     * reused. Render-thread only, hence no synchronisation.
     */
    private static boolean animatingCached;
    private static boolean animatingValid;
    private static long animatingAt;

    /** Safety net in case the invalidation hook ever stops firing: never serve a stale frame. */
    private static final long ANIMATING_MAX_AGE_MS = 50L;

    private EmfArmorModelBridge() {
    }

    public static void register() {
        try {
            isEntityForcedToVanillaModel =
                    Class.forName(EMF_CONTEXT).getMethod("isEntityForcedToVanillaModel");
            available = true;
            BetterCombatMod.LOGGER.info(
                    "[bc] EMF armor-model bridge active: 3D armor keeps its CEM model during animations.");
            registerAnimatingVariable();
        } catch (ReflectiveOperationException | LinkageError absent) {
            available = false;
            BetterCombatMod.LOGGER.info("[bc] EMF not present, 3D armor keeps the vanilla render path.");
        }
    }

    /**
     * Expose {@link #ANIMATING_VARIABLE} to EMF's animation expressions.
     *
     * <p>The generated armor models gate Fresh Animations' procedural motion on it. FA's rig already
     * ships the branch we want - every bone reads
     * {@code if(varb.21_2_plus, <FA procedural>, <vanilla bone>)} - so making the condition false
     * while an animation plays drops each bone onto the plain vanilla value and the armor tracks the
     * body exactly instead of adding an idle sway of its own on top of the pose.
     *
     * <p>Singleton rather than per-entity because EMF evaluates a model's expressions with that
     * entity's context already installed, so the shared supplier answers for whoever is being drawn.
     */
    private static void registerAnimatingVariable() {
        try {
            Class<?> api = Class.forName(EMF_API);
            // The three-string overload is the one EMF documents; the shorter one registers fine but
            // logs "Invalid registration" every start, which is noise in a log we read for real bugs.
            Method register = api.getMethod("registerSingletonAnimationVariable",
                    String.class, String.class, String.class, BooleanSupplier.class);
            BooleanSupplier animating = EmfArmorModelBridge::animating;
            register.invoke(null, "bettercombat", ANIMATING_VARIABLE, ANIMATING_VARIABLE, animating);
            BooleanSupplier singlePass = EmfArmorModelBridge::collapsesArmorLayers;
            register.invoke(null, "bettercombat", SINGLE_PASS_VARIABLE, SINGLE_PASS_VARIABLE, singlePass);
            BetterCombatMod.LOGGER.info("[bc] EMF animation variables '{}' and '{}' registered.",
                    ANIMATING_VARIABLE, SINGLE_PASS_VARIABLE);
            EmfBendVariables.register(api);
            EmfPoseVariables.register(api);
        } catch (ReflectiveOperationException failure) {
            // The armor .jem references this name. Without it EMF cannot resolve the expression, so say
            // so loudly rather than leaving a model that silently fails to parse.
            BetterCombatMod.LOGGER.error(
                    "[bc] Could not register EMF animation variable '{}': 3D armor will keep Fresh Animations'"
                            + " own motion during emotes and weapon poses. {}",
                    ANIMATING_VARIABLE, failure.toString());
        }
    }

    /**
     * Name of the CEM model a root part carries, or null when it cannot be read.
     *
     * <p>Used to tell NightFantasy's armor roots apart from the ones a pack like Fresh Animations
     * brings along, so the vanilla-model exemption only covers ours.
     */
    public static String modelNameOf(Object root) {
        try {
            java.lang.reflect.Field field = modelNameField;
            if (field == null || !field.getDeclaringClass().isInstance(root)) {
                field = root.getClass().getField("modelName");
                modelNameField = field;
            }
            Object id = field.get(root);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    /**
     * @return true when {@code EquipmentLayerRendererMixin} is cutting the packed armor layers down
     *         to one, which is exactly when EMF is present to draw the CEM model instead
     */
    public static boolean collapsesArmorLayers() {
        return available;
    }

    /**
     * Called when EMF moves on to another entity, which is the moment the cached answer stops
     * applying. {@code EmfAnimationPauseMixin} drives this from EMF's own single choke point.
     */
    public static void invalidateAnimating() {
        animatingValid = false;
    }

    /** Value behind {@link #ANIMATING_VARIABLE}: {@link #entityForcedToVanilla()}, computed once per entity. */
    private static boolean animating() {
        long now = System.currentTimeMillis();
        if (animatingValid && now - animatingAt <= ANIMATING_MAX_AGE_MS) {
            return animatingCached;
        }
        animatingCached = entityForcedToVanilla();
        animatingValid = true;
        animatingAt = now;
        return animatingCached;
    }

    /**
     * Uuid of an EMF entity handed to us as a bare {@code Object}, for mixins that cannot name EMF's
     * types at compile time.
     *
     * @return the uuid, or null when it could not be read
     */
    public static java.util.UUID uuidOf(Object emfEntity) {
        // Straight through Entity#getUUID whenever it is a real entity, which it is for every player.
        // This sits on the hottest path there is: EMF resolves nf_animating once per bone, per armor
        // piece, per frame, and each of those walks the vanilla-model conditions - so a reflective
        // lookup here costs frames rather than microseconds. Reflection stays only as the fallback
        // for whatever else EMF may hand over, and even then the method is cached.
        if (emfEntity instanceof net.minecraft.world.entity.Entity entity) {
            return entity.getUUID();
        }
        if (emfEntity == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = uuidMethod;
            if (method == null || !method.getDeclaringClass().isInstance(emfEntity)) {
                method = emfEntity.getClass().getMethod("etf$getUuid");
                uuidMethod = method;
            }
            return (java.util.UUID) method.invoke(emfEntity);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            return null;
        }
    }

    /**
     * @return true when EMF is currently pushing the rendered entity onto its vanilla model, which is
     *         the only situation the armor exemption should apply to
     */
    public static boolean entityForcedToVanilla() {
        if (!available) {
            return false;
        }
        try {
            return (boolean) isEntityForcedToVanillaModel.invoke(null);
        } catch (ReflectiveOperationException failure) {
            // Answering yes on an error would strand every layer on a custom model; stay out of the way.
            available = false;
            BetterCombatMod.LOGGER.warn("[bc] EMF armor-model bridge disabled: {}", failure.toString());
            return false;
        }
    }
}
