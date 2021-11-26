package cn.xnatural.app;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <pre>
 * 对象注入
 * 匹配规则:
 *  1. 如果 {@link #name()} 为空
 *      1.1. 匹配: 字段类型 和 字段名
 *      1.2. 匹配: 字段类型
 *  2. 匹配: 字段类型 和 {@link #name()}
 *  </pre>
 */
@Target({ FIELD })
@Retention(RUNTIME)
@Documented
public @interface Inject {
    /**
     * 指定bean对象名
     */
    String name() default "";
}
