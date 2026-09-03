package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;

import java.util.List;
import java.util.Locale;

/** Canonical, brand-independent characteristics of one market paint product. */
public record PaintProductProfile(
        List<Role> roles,
        List<ApplicationMethod> applicationMethods,
        ApplicationSystem applicationSystem,
        Coverage coverage,
        Finish finish,
        List<Effect> effects,
        Undercoat undercoat,
        Medium medium) {

    public PaintProductProfile {
        roles = immutableNonEmpty(roles, "profile.roles");
        applicationMethods = immutableNonEmpty(applicationMethods, "profile.applicationMethods");
        if (applicationSystem == null) throw invalid("profile.applicationSystem is required.");
        if (coverage == null) throw invalid("profile.coverage is required.");
        if (finish == null) throw invalid("profile.finish is required.");
        effects = effects == null ? List.of() : List.copyOf(effects);
        if (undercoat == null) throw invalid("profile.undercoat is required.");
        if (medium == null) throw invalid("profile.medium is required.");
    }

    public boolean requiresUsageInstructions() {
        return roles.stream().anyMatch(role -> switch (role) {
            case PRIMER, WASH, INK, VARNISH, MEDIUM, AUXILIARY, TECHNICAL_EFFECT, PIGMENT -> true;
            case COLOR_PAINT -> false;
        });
    }

    public boolean behavioral() {
        return applicationSystem == ApplicationSystem.ONE_COAT_SHADING
                || roles.stream().anyMatch(role -> role == Role.WASH || role == Role.INK
                || role == Role.TECHNICAL_EFFECT);
    }

    public List<String> roleIds() { return roles.stream().map(Role::id).toList(); }
    public List<String> applicationMethodIds() { return applicationMethods.stream().map(ApplicationMethod::id).toList(); }
    public List<String> effectIds() { return effects.stream().map(Effect::id).toList(); }

    private static <T> List<T> immutableNonEmpty(List<T> values, String field) {
        if (values == null || values.isEmpty()) throw invalid(field + " must not be empty.");
        if (values.stream().anyMatch(java.util.Objects::isNull)) throw invalid(field + " cannot contain null values.");
        return List.copyOf(values);
    }

    public record Undercoat(UndercoatTone tone, boolean preHighlightedSurfaceRecommended) {
        public Undercoat {
            if (tone == null) throw invalid("profile.undercoat.tone is required.");
        }
    }

    public enum Role implements Identified {
        COLOR_PAINT("color_paint"), PRIMER("primer"), WASH("wash"), INK("ink"),
        VARNISH("varnish"), MEDIUM("medium"), AUXILIARY("auxiliary"),
        TECHNICAL_EFFECT("technical_effect"), PIGMENT("pigment");
        private final String id;
        Role(String id) { this.id = id; }
        public String id() { return id; }
        public static Role fromId(String id) { return identified(values(), id, "paint role"); }
    }

    public enum ApplicationMethod implements Identified {
        BRUSH("brush"), AIRBRUSH("airbrush"), SPRAY("spray"), MARKER("marker");
        private final String id;
        ApplicationMethod(String id) { this.id = id; }
        public String id() { return id; }
        public static ApplicationMethod fromId(String id) { return identified(values(), id, "application method"); }
    }

    public enum ApplicationSystem implements Identified {
        CONVENTIONAL_LAYERING("conventional_layering"), ONE_COAT_SHADING("one_coat_shading"),
        WASHING("washing"), PRIMING("priming"), EFFECT_APPLICATION("effect_application"), UNKNOWN("unknown");
        private final String id;
        ApplicationSystem(String id) { this.id = id; }
        public String id() { return id; }
        public static ApplicationSystem fromId(String id) { return identified(values(), id, "application system"); }
    }

    public enum Coverage implements Identified {
        OPAQUE("opaque"), SEMI_OPAQUE("semi_opaque"), TRANSLUCENT("translucent"),
        TRANSPARENT("transparent"), UNKNOWN("unknown");
        private final String id;
        Coverage(String id) { this.id = id; }
        public String id() { return id; }
        public static Coverage fromId(String id) { return identified(values(), id, "coverage"); }
    }

    public enum Finish implements Identified {
        MATTE("matte"), SATIN("satin"), GLOSS("gloss"), UNKNOWN("unknown");
        private final String id;
        Finish(String id) { this.id = id; }
        public String id() { return id; }
        public static Finish fromId(String id) { return identified(values(), id, "finish"); }
    }

    public enum Effect implements Identified {
        METALLIC("metallic"), FLUORESCENT("fluorescent"), PEARLESCENT("pearlescent");
        private final String id;
        Effect(String id) { this.id = id; }
        public String id() { return id; }
        public static Effect fromId(String id) { return identified(values(), id, "effect"); }
    }

    public enum UndercoatTone implements Identified {
        LIGHT("light"), DARK("dark"), ANY("any"), UNKNOWN("unknown");
        private final String id;
        UndercoatTone(String id) { this.id = id; }
        public String id() { return id; }
        public static UndercoatTone fromId(String id) { return identified(values(), id, "undercoat tone"); }
    }

    public enum Medium implements Identified {
        WATER_BASED_ACRYLIC("water_based_acrylic"), ACRYLIC("acrylic"),
        ALCOHOL_BASED("alcohol_based"), OIL("oil"), ENAMEL("enamel"), UNKNOWN("unknown");
        private final String id;
        Medium(String id) { this.id = id; }
        public String id() { return id; }
        public static Medium fromId(String id) { return identified(values(), id, "medium"); }
    }

    private interface Identified { String id(); }

    private static <T extends Enum<T> & Identified> T identified(T[] values, String id, String kind) {
        var normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (var value : values) if (value.id().equals(normalized)) return value;
        throw invalid("Unknown " + kind + ": " + id);
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_market_paint", message);
    }
}
