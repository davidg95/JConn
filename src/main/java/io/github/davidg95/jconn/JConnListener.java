/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.davidg95.jconn;

/**
 * Interface for class to listen for JConnEvents.
 *
 * @author David
 */
public interface JConnListener {

    /**
     * When non-request data is received.
     *
     * @param data the data.
     */
    public void onReceive(JConnData data);

    /**
     * When the connection to the server is lost.
     *
     * @param event the event data.
     */
    public void onConnectionDrop(JConnEvent event);

    /**
     * When the connection to the server is reestablished
     *
     * @param event the event data.
     */
    public void onConnectionReestablish(JConnEvent event);
}
