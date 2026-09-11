package dev.typebridge.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a strong domain wrapper with exactly one unary constructor.
 * TypeBridge may enter this type implicitly inside an AdaptationScope.
 * Leaving the strong type is deliberately explicit Java (value()/getValue()/etc.).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface StrongType {
}
