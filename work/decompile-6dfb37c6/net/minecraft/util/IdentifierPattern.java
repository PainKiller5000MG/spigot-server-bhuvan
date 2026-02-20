package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

public class IdentifierPattern {

    public static final Codec<IdentifierPattern> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.PATTERN.optionalFieldOf("namespace").forGetter((identifierpattern) -> {
            return identifierpattern.namespacePattern;
        }), ExtraCodecs.PATTERN.optionalFieldOf("path").forGetter((identifierpattern) -> {
            return identifierpattern.pathPattern;
        })).apply(instance, IdentifierPattern::new);
    });
    private final Optional<Pattern> namespacePattern;
    private final Predicate<String> namespacePredicate;
    private final Optional<Pattern> pathPattern;
    private final Predicate<String> pathPredicate;
    private final Predicate<Identifier> locationPredicate;

    private IdentifierPattern(Optional<Pattern> namespacePattern, Optional<Pattern> pathPattern) {
        this.namespacePattern = namespacePattern;
        this.namespacePredicate = (Predicate) namespacePattern.map(Pattern::asPredicate).orElse((Predicate) (s) -> {
            return true;
        });
        this.pathPattern = pathPattern;
        this.pathPredicate = (Predicate) pathPattern.map(Pattern::asPredicate).orElse((Predicate) (s) -> {
            return true;
        });
        this.locationPredicate = (identifier) -> {
            return this.namespacePredicate.test(identifier.getNamespace()) && this.pathPredicate.test(identifier.getPath());
        };
    }

    public Predicate<String> namespacePredicate() {
        return this.namespacePredicate;
    }

    public Predicate<String> pathPredicate() {
        return this.pathPredicate;
    }

    public Predicate<Identifier> locationPredicate() {
        return this.locationPredicate;
    }
}
