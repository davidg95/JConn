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
 * Class for modelling a JConn request status.
 *
 * @author David
 */
public class JConnStatus {

    private boolean sent;
    private boolean received;

    /**
     * Constructor which sets the values to false.
     */
    public JConnStatus() {
        sent = false;
        received = false;
    }

    /**
     * Check if the request has been sent.
     *
     * @return true if it has been sent, false if it has not.
     */
    public boolean isSent() {
        return sent;
    }

    /**
     * Set the sent status of the request.
     *
     * @param sent true if it has been sent, false if it has not.
     */
    protected void setSent(boolean sent) {
        this.sent = sent;
    }

    /**
     * Check if the reply has been received.
     *
     * @return true if it has been received, false if it has not.
     */
    public boolean isReceived() {
        return received;
    }

    /**
     * Set the received status of the reply.
     *
     * @param received true if it has been received, false if it has not.
     */
    protected void setReceived(boolean received) {
        this.received = received;
    }

    /**
     * ToString which displays information about the request.
     *
     * @return string value.
     */
    @Override
    public String toString() {
        return "Sent: " + (sent ? "YES" : "NO") + " RECEIVED: " + (received ? "YES" : "NO");
    }
}
