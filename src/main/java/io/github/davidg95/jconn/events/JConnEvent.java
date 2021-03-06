/* 
 * JConn TCP networking framework.
 *
 * Copyright (C) 2017 David A. Grant
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * To get in touch with me, send an email to pirakaleader@googlemail.com.
 */
package io.github.davidg95.jconn.events;

/**
 * Class for containing info about JConnEvents.
 *
 * @author David
 */
public class JConnEvent {

    private final String message; //The message.
    private boolean cancelled;

    /**
     * Create a JConnEvent with a message.
     *
     * @param message the message.
     */
    public JConnEvent(String message) {
        this.message = message;
        this.cancelled = false;
    }

    /**
     * Get the message.
     *
     * @return the message as a String.
     */
    public String getMessage() {
        return message;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String toString() {
        return message;
    }
}
