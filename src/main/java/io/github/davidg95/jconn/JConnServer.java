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
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class which starts the server.
 *
 * @author David
 */
public class JConnServer {

    /**
     * Indicates if log output should be included.
     */
    protected boolean debug = false;

    /**
     * The connection accept thread handler class.
     */
    private final JConnConnectionAccept acceptThread;

    /**
     * The JConnListeners.
     */
    private final List<JConnListener> listeners;
    /**
     * StampedLock for JConnListeners.
     */
    private final StampedLock listenersLock;

    /**
     * Constructor which creates a new server instance.
     *
     * @param port the port to use.
     * @param classToScan the class with the JConnMethod annotated methods.
     * @param debug if debug output should be shown.
     * @throws IOException if there was an error starting the server.
     */
    private JConnServer(int port, Class classToScan, boolean debug) throws IOException {
        listeners = new LinkedList<>();
        listenersLock = new StampedLock();
        this.debug = debug;
        acceptThread = new JConnConnectionAccept(port, classToScan, debug, listeners, listenersLock);
        init();
    }

    /**
     * Starts the thread.
     */
    private void init() {
        acceptThread.start();
    }

    /**
     * Start a new instance of JConnServer on the specified port.
     *
     * @param port the port to listen on.
     * @param classToScan the class to scan for annotations on methods.
     * @return the JConnServer instance.
     * @throws IOException if there was an error starting the server.
     */
    public static JConnServer start(int port, Class classToScan) throws IOException {
        return start(port, classToScan, false);
    }

    /**
     * Start a new instance of JConnServer on the specified port.
     *
     * @param port the port to listen on.
     * @param classToScan the class to scan for annotations on methods.
     * @param debug indicates if log output should be included.
     * @return the JConnServer instance.
     * @throws IOException if there was an error starting the server.
     */
    public static JConnServer start(int port, Class classToScan, boolean debug) throws IOException {
        final JConnServer server = new JConnServer(port, classToScan, debug);
        return server;
    }

    /**
     * Sends data to all the current connected clients.
     *
     * @param ip the address of the client to send data to, null for all
     * clients.
     * @param data the data to send.
     */
    public void sendData(String ip, JConnData data) {
        if (ip == null) {
            final long stamp = acceptThread.readLock();
            try {
                acceptThread.getAllThreads().forEach((thread) -> { //Send to all connections.
                    try {
                        thread.sendData(data);
                    } catch (IOException ex) {
                        Logger.getLogger(JConnServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } finally {
                acceptThread.unlockRead(stamp);
            }
        } else {
            final long stamp = acceptThread.readLock();
            try {
                for (JConnThread thread : acceptThread.getAllThreads()) {
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
                acceptThread.unlockRead(stamp);
            }
        }
    }

    /**
     * Register a listener.
     *
     * @param listener the JConnListener to register.
     */
    public void registerListener(JConnListener listener) {
        final long stamp = listenersLock.writeLock();
        try {
            listeners.add(listener);
        } finally {
            listenersLock.unlockWrite(stamp);
        }
    }

    /**
     * Return a list of all the client connections.
     *
     * @return a List of type JConnThread.
     */
    public List<JConnThread> getClientConnections() {
        return acceptThread.getAllThreads();
    }

    /**
     * Closes all connection and stops the server.
     */
    public void stopServer() {
        for (JConnThread th : acceptThread.getAllThreads()) {
            try {
                th.endConnection();
            } catch (IOException ex) {
                Logger.getLogger(JConnServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try {
            acceptThread.shutdown();
        } catch (IOException ex) {
            Logger.getLogger(JConnServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
