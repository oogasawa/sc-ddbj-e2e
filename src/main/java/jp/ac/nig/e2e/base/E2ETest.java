package jp.ac.nig.e2e.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** テストメソッドをE2Eテストとしてマークするアノテーション。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface E2ETest {
    String description() default "";
}
