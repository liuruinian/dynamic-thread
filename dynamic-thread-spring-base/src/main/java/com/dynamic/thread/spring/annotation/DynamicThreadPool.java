package com.dynamic.thread.spring.annotation;

import java.lang.annotation.*;

/**
 * Annotation to mark a ThreadPoolExecutor bean as dynamically managed.
 * Thread pools marked with this annotation will be registered to the ThreadPoolRegistry
 * and can be dynamically configured via configuration center.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DynamicThreadPool {

    /**
     * Thread pool unique identifier.
     * If not specified, the bean name will be used.
     */
    String value() default "";

    /**
     * Thread pool unique identifier (alias for value).
     */
    String threadPoolId() default "";
}
