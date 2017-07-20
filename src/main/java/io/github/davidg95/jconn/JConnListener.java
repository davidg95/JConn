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
package io.github.davidg95.jconn;

/**
 * Interface for class to listen for JConnEvents. Any class that implements this
 * interface must be registered using the JConn.registerListener(JConnListener)
 * method.
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
     * When the connection to the server is reestablished after being down.
     *
     * @param event the event data.
     */
    public void onConnectionReestablish(JConnEvent event);

    /**
     * When the server gracefully terminates the connection.
     */
    public void onServerGracefulEnd();
}
