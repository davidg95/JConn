/*
 * JConn networking framewotk.
 * Created by David Grant
 */
package io.github.davidg95.jconn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which marks a method which handles data from a client. The value
 * indicates what the flag it handles should match.
 *
 * @author David
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JConnMethod {

    /**
     * Returns the FLAG this method is associated with.
     *
     * @return the FLAG this method is associated with.
     */
    String value();
}
