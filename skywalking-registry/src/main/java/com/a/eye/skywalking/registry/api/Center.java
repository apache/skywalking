package com.a.eye.skywalking.registry.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Center {
    CenterType type() default CenterType.zookeeper;
}
