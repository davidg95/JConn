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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class which start the server.
 *
 * @author David
 */
public class JConnServer {

    /**
     * Indicates whether the server has been started or not.
     */
    private static boolean started = false;

    /**
     * Indicates if log output should be included.
     */
    protected static boolean DEBUG = false;

    private static JConnConnectionAccept acceptThread;

    protected static final List<JConnListener> LISTENERS;

    static {
        LISTENERS = new LinkedList<>();
    }

    /**
     * Start a new instance of JConnServer on the specified port.
     *
     * @param port the port to listen on.
     * @param classToScan the class to scan for annotations on methods.
     * @throws IOException if there was an error ins starting the server.
     */
    public static void start(int port, Class classToScan) throws IOException {
        JConnServer.start(port, classToScan, false);
    }

    /**
     * Start a new instance of JConnServer on the specified port.
     *
     * @param port the port to listen on.
     * @param classToScan the class to scan for annotations on methods.
     * @param debug indicates if log output should be included.
     * @throws IOException if there was an error ins starting the server.
     */
    public static void start(int port, Class classToScan, boolean debug) throws IOException {
        if (started) {
            throw new IOException("JConn has already been started");
        }
        DEBUG = debug;
        acceptThread = new JConnConnectionAccept(port, classToScan);
        acceptThread.start();
        started = true;
    }

    /**
     * Sends data to all the current connected clients.
     *
     * @param ip the address of the client to send data to, null for all
     * clients.
     * @param data the data to send.
     */
    public static void sendData(String ip, JConnData data) {
        if (ip == null) {
            final long stamp = JConnConnectionAccept.readLock();
            try {
                JConnConnectionAccept.getAllThreads().forEach((thread) -> { //Send to all connections.
                    try {
                        thread.sendData(data);
                    } catch (IOException ex) {
                        Logger.getLogger(JConnServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } finally {
                JConnConnectionAccept.unlockRead(stamp);
            }
        } else {
            final long stamp = JConnConnectionAccept.readLock();
            try {
                for (JConnThread thread : JConnConnectionAccept.getAllThreads()) {
                    if (thread.getAddress().equals(ip)) {
                        try {
                            thread.sendData(data);
                        } catch (IOException ex) {
                            Logger.getLogger(JConnServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return;
                    }
                }
            } finally {
                JConnConnectionAccept.unlockRead(stamp);
            }
        }
    }

    /**
     * Register a listener.
     *
     * @param listener the JConnListener to register.
     */
    public static void registerListener(JConnListener listener) {
        LISTENERS.add(listener);
    }

    /**
     * Return a list of all the client connections.
     *
     * @return a List of type JConnThread.
     */
    public static List<JConnThread> getClientConnections() {
        return JConnConnectionAccept.getAllThreads();
    }
}
