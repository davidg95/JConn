/*
 * JConn networking framewotk.
 * Created by David Grant
 */
package io.github.davidg95.jconn;

/**
 * Interface which is used for specifying the method to be executed when a reply
 * is sent back.
 *
 * @author David
 */
public interface JConnRunnable {

    /**
     * This method gets run on a reply from the server.
     *
     * @param reply the JConnData object from the server.
     */
    public void run(JConnData reply);
}
