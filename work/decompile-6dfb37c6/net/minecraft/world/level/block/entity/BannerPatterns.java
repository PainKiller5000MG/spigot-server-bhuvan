package net.minecraft.world.level.block.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class BannerPatterns {

    public static final ResourceKey<BannerPattern> BASE = create("base");
    public static final ResourceKey<BannerPattern> SQUARE_BOTTOM_LEFT = create("square_bottom_left");
    public static final ResourceKey<BannerPattern> SQUARE_BOTTOM_RIGHT = create("square_bottom_right");
    public static final ResourceKey<BannerPattern> SQUARE_TOP_LEFT = create("square_top_left");
    public static final ResourceKey<BannerPattern> SQUARE_TOP_RIGHT = create("square_top_right");
    public static final ResourceKey<BannerPattern> STRIPE_BOTTOM = create("stripe_bottom");
    public static final ResourceKey<BannerPattern> STRIPE_TOP = create("stripe_top");
    public static final ResourceKey<BannerPattern> STRIPE_LEFT = create("stripe_left");
    public static final ResourceKey<BannerPattern> STRIPE_RIGHT = create("stripe_right");
    public static final ResourceKey<BannerPattern> STRIPE_CENTER = create("stripe_center");
    public static final ResourceKey<BannerPattern> STRIPE_MIDDLE = create("stripe_middle");
    public static final ResourceKey<BannerPattern> STRIPE_DOWNRIGHT = create("stripe_downright");
    public static final ResourceKey<BannerPattern> STRIPE_DOWNLEFT = create("stripe_downleft");
    public static final ResourceKey<BannerPattern> STRIPE_SMALL = create("small_stripes");
    public static final ResourceKey<BannerPattern> CROSS = create("cross");
    public static final ResourceKey<BannerPattern> STRAIGHT_CROSS = create("straight_cross");
    public static final ResourceKey<BannerPattern> TRIANGLE_BOTTOM = create("triangle_bottom");
    public static final ResourceKey<BannerPattern> TRIANGLE_TOP = create("triangle_top");
    public static final ResourceKey<BannerPattern> TRIANGLES_BOTTOM = create("triangles_bottom");
    public static final ResourceKey<BannerPattern> TRIANGLES_TOP = create("triangles_top");
    public static final ResourceKey<BannerPattern> DIAGONAL_LEFT = create("diagonal_left");
    public static final ResourceKey<BannerPattern> DIAGONAL_RIGHT = create("diagonal_up_right");
    public static final ResourceKey<BannerPattern> DIAGONAL_LEFT_MIRROR = create("diagonal_up_left");
    public static final ResourceKey<BannerPattern> DIAGONAL_RIGHT_MIRROR = create("diagonal_right");
    public static final ResourceKey<BannerPattern> CIRCLE_MIDDLE = create("circle");
    public static final ResourceKey<BannerPattern> RHOMBUS_MIDDLE = create("rhombus");
    public static final ResourceKey<BannerPattern> HALF_VERTICAL = create("half_vertical");
    public static final ResourceKey<BannerPattern> HALF_HORIZONTAL = create("half_horizontal");
    public static final ResourceKey<BannerPattern> HALF_VERTICAL_MIRROR = create("half_vertical_right");
    public static final ResourceKey<BannerPattern> HALF_HORIZONTAL_MIRROR = create("half_horizontal_bottom");
    public static final ResourceKey<BannerPattern> BORDER = create("border");
    public static final ResourceKey<BannerPattern> CURLY_BORDER = create("curly_border");
    public static final ResourceKey<BannerPattern> GRADIENT = create("gradient");
    public static final ResourceKey<BannerPattern> GRADIENT_UP = create("gradient_up");
    public static final ResourceKey<BannerPattern> BRICKS = create("bricks");
    public static final ResourceKey<BannerPattern> GLOBE = create("globe");
    public static final ResourceKey<BannerPattern> CREEPER = create("creeper");
    public static final ResourceKey<BannerPattern> SKULL = create("skull");
    public static final ResourceKey<BannerPattern> FLOWER = create("flower");
    public static final ResourceKey<BannerPattern> MOJANG = create("mojang");
    public static final ResourceKey<BannerPattern> PIGLIN = create("piglin");
    public static final ResourceKey<BannerPattern> FLOW = create("flow");
    public static final ResourceKey<BannerPattern> GUSTER = create("guster");

    public BannerPatterns() {}

    private static ResourceKey<BannerPattern> create(String id) {
        return ResourceKey.create(Registries.BANNER_PATTERN, Identifier.withDefaultNamespace(id));
    }

    public static void bootstrap(BootstrapContext<BannerPattern> context) {
        register(context, BannerPatterns.BASE);
        register(context, BannerPatterns.SQUARE_BOTTOM_LEFT);
        register(context, BannerPatterns.SQUARE_BOTTOM_RIGHT);
        register(context, BannerPatterns.SQUARE_TOP_LEFT);
        register(context, BannerPatterns.SQUARE_TOP_RIGHT);
        register(context, BannerPatterns.STRIPE_BOTTOM);
        register(context, BannerPatterns.STRIPE_TOP);
        register(context, BannerPatterns.STRIPE_LEFT);
        register(context, BannerPatterns.STRIPE_RIGHT);
        register(context, BannerPatterns.STRIPE_CENTER);
        register(context, BannerPatterns.STRIPE_MIDDLE);
        register(context, BannerPatterns.STRIPE_DOWNRIGHT);
        register(context, BannerPatterns.STRIPE_DOWNLEFT);
        register(context, BannerPatterns.STRIPE_SMALL);
        register(context, BannerPatterns.CROSS);
        register(context, BannerPatterns.STRAIGHT_CROSS);
        register(context, BannerPatterns.TRIANGLE_BOTTOM);
        register(context, BannerPatterns.TRIANGLE_TOP);
        register(context, BannerPatterns.TRIANGLES_BOTTOM);
        register(context, BannerPatterns.TRIANGLES_TOP);
        register(context, BannerPatterns.DIAGONAL_LEFT);
        register(context, BannerPatterns.DIAGONAL_RIGHT);
        register(context, BannerPatterns.DIAGONAL_LEFT_MIRROR);
        register(context, BannerPatterns.DIAGONAL_RIGHT_MIRROR);
        register(context, BannerPatterns.CIRCLE_MIDDLE);
        register(context, BannerPatterns.RHOMBUS_MIDDLE);
        register(context, BannerPatterns.HALF_VERTICAL);
        register(context, BannerPatterns.HALF_HORIZONTAL);
        register(context, BannerPatterns.HALF_VERTICAL_MIRROR);
        register(context, BannerPatterns.HALF_HORIZONTAL_MIRROR);
        register(context, BannerPatterns.BORDER);
        register(context, BannerPatterns.GRADIENT);
        register(context, BannerPatterns.GRADIENT_UP);
        register(context, BannerPatterns.BRICKS);
        register(context, BannerPatterns.CURLY_BORDER);
        register(context, BannerPatterns.GLOBE);
        register(context, BannerPatterns.CREEPER);
        register(context, BannerPatterns.SKULL);
        register(context, BannerPatterns.FLOWER);
        register(context, BannerPatterns.MOJANG);
        register(context, BannerPatterns.PIGLIN);
        register(context, BannerPatterns.FLOW);
        register(context, BannerPatterns.GUSTER);
    }

    public static void register(BootstrapContext<BannerPattern> context, ResourceKey<BannerPattern> key) {
        context.register(key, new BannerPattern(key.identifier(), "block.minecraft.banner." + key.identifier().toShortLanguageKey()));
    }
}
