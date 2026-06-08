package com.sgd_hc.audit.annotation;

import com.sgd_hc.audit.entity.enums.ActionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String resourceType();
    ActionType actionType();
    String idParamName() default "id";
}
