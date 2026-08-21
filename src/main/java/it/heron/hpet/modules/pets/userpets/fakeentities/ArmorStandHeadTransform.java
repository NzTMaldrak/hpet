package it.heron.hpet.modules.pets.userpets.fakeentities;

import org.bukkit.util.Vector;

/**
 * Reproduces the Minecraft 26.2 client transform for a player head rendered in
 * the main hand of this plugin's adult armor stand.
 *
 * <p>Every value below comes from the 26.2 client classes/resources named in
 * the comments. Keeping the operations in client order avoids empirical polar
 * offsets that become inaccurate after scaling.</p>
 */
public final class ArmorStandHeadTransform {

    private static final double MODEL_PIXEL = 1d / 16d;

    // ArmorStandModel.createBodyLayer(): right_arm PartPose.offset(-5, 2, 0).
    private static final Vector RIGHT_ARM_PIVOT = pixels(-5d, 2d, 0d);

    // Metadata1_21: the pose actually sent for DATA_RIGHT_ARM_POSE.
    public static final float RIGHT_ARM_X_DEGREES = -44f;
    public static final float RIGHT_ARM_Y_DEGREES = 34f;
    public static final float RIGHT_ARM_Z_DEGREES = 1f;

    // ItemInHandLayer.submitArmWithItem(), adult right hand.
    private static final double HAND_LAYER_X_DEGREES = -90d;
    private static final double HAND_LAYER_Y_DEGREES = 180d;
    private static final Vector RIGHT_HAND_ITEM_TRANSLATION = pixels(1d, 2d, -10d);

    // assets/minecraft/models/item/template_skull.json -> thirdperson_righthand.
    private static final Vector SKULL_ITEM_TRANSLATION = pixels(0d, 3d, 0d);
    private static final double SKULL_ITEM_X_DEGREES = 45d;
    private static final double SKULL_ITEM_Y_DEGREES = 45d;
    private static final double SKULL_ITEM_SCALE = 0.5d;

    // ItemTransform.apply() and assets/minecraft/items/player_head.json.
    private static final Vector ITEM_MODEL_ORIGIN_SHIFT = new Vector(-0.5d, -0.5d, -0.5d);
    private static final Vector PLAYER_HEAD_LOCAL_TRANSLATION = new Vector(0.5d, 0d, 0.5d);

    // SkullModel.createHeadModel(): head cube (-4, -8, -4), size (8, 8, 8).
    private static final Vector SKULL_GEOMETRIC_CENTER = pixels(0d, -4d, 0d);

    // SkullModel.createHumanoidHeadLayer(): outer layer deformation is 0.25 model pixels.
    private static final double OUTER_MIN_XZ_PIXELS = -4.25d;
    private static final double OUTER_MAX_XZ_PIXELS = 4.25d;
    private static final double OUTER_MIN_Y_PIXELS = -8.25d;
    private static final double OUTER_MAX_Y_PIXELS = 0.25d;

    // LivingEntityRenderer.submit(): model-space translation before rendering layers.
    private static final double LIVING_MODEL_Y_TRANSLATION = -1.501d;

    private static final double UNSCALED_HEAD_TOP_HEIGHT = calculateUnscaledHeadTopHeight();

    private ArmorStandHeadTransform() {
    }

    public static Vector centerOffset(float entityYaw, double entityScale) {
        return transformModelPoint(SKULL_GEOMETRIC_CENTER, entityYaw, entityScale);
    }

    public static double scaleTopCompensation(double entityScale) {
        validateScale(entityScale);
        return UNSCALED_HEAD_TOP_HEIGHT * (entityScale - 1d);
    }

    private static double calculateUnscaledHeadTopHeight() {
        double top = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{OUTER_MIN_XZ_PIXELS, OUTER_MAX_XZ_PIXELS}) {
            for (double y : new double[]{OUTER_MIN_Y_PIXELS, OUTER_MAX_Y_PIXELS}) {
                for (double z : new double[]{OUTER_MIN_XZ_PIXELS, OUTER_MAX_XZ_PIXELS}) {
                    top = Math.max(top, transformModelPoint(pixels(x, y, z), 0f, 1d).getY());
                }
            }
        }
        return top;
    }

    private static Vector transformModelPoint(Vector modelPoint, float entityYaw, double entityScale) {
        validateScale(entityScale);
        Vector point = modelPoint.clone();

        // SpecialModelWrapper local transform, then ItemTransform's final origin shift.
        point.add(PLAYER_HEAD_LOCAL_TRANSLATION);
        point.add(ITEM_MODEL_ORIGIN_SHIFT);

        // ItemTransform.apply(): scale, rotationXYZ (applied Z -> Y -> X), translation.
        point.multiply(SKULL_ITEM_SCALE);
        point.rotateAroundY(Math.toRadians(SKULL_ITEM_Y_DEGREES));
        point.rotateAroundX(Math.toRadians(SKULL_ITEM_X_DEGREES));
        point.add(SKULL_ITEM_TRANSLATION);

        // ItemInHandLayer: hand translation is inside its two fixed rotations.
        point.add(RIGHT_HAND_ITEM_TRANSLATION);
        point.rotateAroundY(Math.toRadians(HAND_LAYER_Y_DEGREES));
        point.rotateAroundX(Math.toRadians(HAND_LAYER_X_DEGREES));

        // ModelPart.translateAndRotate(): rotationZYX is applied X -> Y -> Z.
        point.rotateAroundX(Math.toRadians(RIGHT_ARM_X_DEGREES));
        point.rotateAroundY(Math.toRadians(RIGHT_ARM_Y_DEGREES));
        point.rotateAroundZ(Math.toRadians(RIGHT_ARM_Z_DEGREES));
        point.add(RIGHT_ARM_PIVOT);

        // LivingEntityRenderer: model origin, mirror, entity yaw, then SCALE attribute.
        point.add(new Vector(0d, LIVING_MODEL_Y_TRANSLATION, 0d));
        point.setX(-point.getX());
        point.setY(-point.getY());
        point.rotateAroundY(Math.toRadians(180d - entityYaw));
        return point.multiply(entityScale);
    }

    private static Vector pixels(double x, double y, double z) {
        return new Vector(x * MODEL_PIXEL, y * MODEL_PIXEL, z * MODEL_PIXEL);
    }

    private static void validateScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0d) {
            throw new IllegalArgumentException("Invalid armor stand scale: " + scale);
        }
    }
}
