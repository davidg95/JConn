/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.davidg95.jconn;

/**
 * Class for containing info about JConnEvents.
 *
 * @author David
 */
public class JConnEvent {

    private final String message; //The message.

    /**
     * Create a JConnEvent with a message.
     *
     * @param message the message.
     */
    public JConnEvent(String message) {
        this.message = message;
    }

    /**
     * Get the message.
     *
     * @return the message as a String.
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message;
    }
}
