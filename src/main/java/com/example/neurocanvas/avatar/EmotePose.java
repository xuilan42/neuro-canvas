package com.example.neurocanvas.avatar;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * An emote moves the player's own body. The bones of an emote model named head, body, right arm, left arm, right leg and left leg
 * are turned into the rotations of the matching parts of the player model; whatever the emote does not move keeps its normal
 * pose. Angles are the ones used in models: x swings a limb forward, y turns it to the left, z lifts the right arm outward.
 */
public final class EmotePose {
    public static final String[] PARTS = {"head", "body", "rightarm", "leftarm", "rightleg", "leftleg"};

    private EmotePose() { }

    /** The rotations of the six parts at this moment of an animation, with null for a part the emote has no bone for. */
    public static float[][] read(BbInstance emote, BbModel.Anim anim, double time) {
        emote.resetPose();
        emote.apply(anim, time);
        float[][] out = new float[PARTS.length][];
        for (int i = 0; i < PARTS.length; i++) {
            out[i] = emote.rotationOf(PARTS[i]);
        }
        return out;
    }

    private static void set(ModelPart part, float[] r) {
        if (r != null) {
            // The game's parts rotate in its own space (y down, front toward -z, mirrored): pitch and yaw change sign, roll does not.
            part.pitch = (float) -Math.toRadians(r[0]);
            part.yaw = (float) -Math.toRadians(r[1]);
            part.roll = (float) Math.toRadians(r[2]);
        }
    }

    public static void apply(PlayerEntityModel<?> model, float[][] rot) {
        set(model.head, rot[0]);
        set(model.body, rot[1]);
        set(model.rightArm, rot[2]);
        set(model.leftArm, rot[3]);
        set(model.rightLeg, rot[4]);
        set(model.leftLeg, rot[5]);
        // The second skin layer follows the part under it.
        model.hat.copyTransform(model.head);
        model.jacket.copyTransform(model.body);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
        model.rightPants.copyTransform(model.rightLeg);
        model.leftPants.copyTransform(model.leftLeg);
    }
}
