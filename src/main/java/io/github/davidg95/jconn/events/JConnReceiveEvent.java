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

import io.github.davidg95.jconn.*;

/**
 *
 * @author David
 */
public class JConnReceiveEvent {

    private JConnData data;
    private boolean cancelled;

    public JConnReceiveEvent(JConnData data) {
        this.data = data;
        this.cancelled = false;
    }

    public JConnData getData() {
        return data;
    }

    public void setData(JConnData data) {
        this.data = data;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String toString() {
        return "JConnReceiveEvent{" + "data=" + data + ", cancelled=" + cancelled + '}';
    }

}
