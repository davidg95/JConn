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
 * Annotation which marks a method parameter. JConn will inject the value from
 * the connection object which matches the value passed into the annotation.
 *
 * @author David
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface JConnParameter {

    /**
     * The name of the parameters in the HashMap that this parameter should get.
     *
     * @return the parameters this is associated with.
     */
    String value();
}
