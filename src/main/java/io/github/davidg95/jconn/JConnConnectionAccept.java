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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread which accepts incoming connections from clients.
 *
 * @author David
 */
public class JConnConnectionAccept extends Thread {

    private static final Logger LOG = Logger.getGlobal();

    /**
     * The port which is being used by the server.
     */
    protected static int PORT_IN_USE;

    /**
     * The maximum number of connections that can be active at once. This must
     * be changed before starting the thread.
     */
    protected static int MAX_CONN = 10;

    /**
     * The maximum number of connections that can be queued, This must be
     * changed before starting the thread.
     */
    protected static int MAX_QUEUE = 10;

    private final ThreadPoolExecutor pool;

    private final ServerSocket socket;

    private final Class classToScan;

    private final List<JConnThread> threads;
    private final StampedLock lock;

    private final boolean debug;

    /**
     * All the detected method which have the @JConnMethod annotation.
     */
    private final LinkedList<Method> JCONNMETHODS;

    private final List<JConnListener> listeners;

    private final StampedLock listenersLock;

    /**
     * Constructor which starts the ThreadPoolExcecutor.
     *
     * @param PORT the port number to listen on.
     * @param classToScan the class to be scanned for annotations.
     * @param debug indicates if debug output should be shown.
     * @param listeners the JConnListeners.
     * @param listenersLock the lock for the listeners.
     * @throws IOException if there was a network error.
     */
    public JConnConnectionAccept(int PORT, Class classToScan, boolean debug, List<JConnListener> listeners, StampedLock listenersLock) throws IOException {
        super("ConnectionAcceptThread");
        threads = new LinkedList<>();
        lock = new StampedLock();
        pool = new ThreadPoolExecutor(MAX_CONN, MAX_QUEUE, 50000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(MAX_QUEUE));
        this.socket = new ServerSocket(PORT);
        this.classToScan = classToScan;
        this.debug = debug;
        this.listeners = listeners;
        this.listenersLock = listenersLock;
        PORT_IN_USE = PORT;
        JCONNMETHODS = new LinkedList<>();
        scanClass();
    }

    /**
     * Returns a list of all the connection thread objects.
     *
     * @return a List of JConnThreads.
     */
    protected List<JConnThread> getAllThreads() {
        return threads;
    }

    /**
     * Removed a thread from the list of threads.
     *
     * @param th the thread to remove.
     */
    protected void removeThread(JConnThread th) {
        final long stamp = lock.writeLock();
        try {
            threads.remove(th);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Get a read lock on the threads list.
     *
     * @return the stamp for the lock.
     */
    protected long readLock() {
        return lock.readLock();
    }

    /**
     * Unlock the read lock for the threads list.
     *
     * @param stamp the stamp for the lock.
     */
    protected void unlockRead(final long stamp) {
        lock.unlockRead(stamp);
    }

    /**
     * Scans this class and finds all method with the JConnMethod annotation.
     */
    private void scanClass() {
        final Method[] methods = classToScan.getDeclaredMethods(); //Get all the methods in this class
        for (Method m : methods) { //Loop through each method
            if (m.isAnnotationPresent(JConnMethod.class)) { //Check if the annotation is a JConnMethod annotation
                JCONNMETHODS.add(m);
            }
        }
    }

    @Override
    public void run() {
        if (debug) {
            LOG.log(Level.INFO, "Starting Thread Pool Excecutor");
        }
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            if (debug) {
                LOG.log(Level.INFO, "Local IP address is " + InetAddress.getLocalHost().getHostAddress());
                LOG.log(Level.INFO, "Server Socket running on port number " + PORT_IN_USE);
            }
        } catch (UnknownHostException ex) {
            if (debug) {
                LOG.log(Level.WARNING, "For some reason, the ip address of the local server could not be retrieved");
            }
        }
        if (debug) {
            LOG.log(Level.INFO, "Ready to accept connections");
        }
        for (;;) {
            try {
                final Socket incoming = socket.accept(); //Wait for a connection.
                if (debug) {
                    LOG.log(Level.INFO, "Connection from " + incoming.getInetAddress().getHostAddress());
                }
                final JConnEvent event = new JConnEvent(incoming.toString() + " has connected");
                {
                    final long stamp = listenersLock.readLock();
                    try {
                        listeners.forEach((l) -> {
                            l.onConnectionEstablish(event);
                        });
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Error passing JConnEvent to listener", e);
                    } finally {
                        listenersLock.unlockRead(stamp);
                    }
                }
                if (event.isCancelled()) {
                    LOG.log(Level.INFO, "Connection blocked");
                    continue;
                }
                final Constructor c = classToScan.getDeclaredConstructor(); //Get the blank constructor
                c.setAccessible(true);
                final JConnThread th = new JConnThread(socket.getInetAddress().getHostAddress(), incoming, JCONNMETHODS, c.newInstance(), debug, listeners, listenersLock, this);
                pool.submit(th); //Submit the socket to the excecutor.
                {
                    final long stamp = lock.writeLock();
                    try {
                        threads.add(th);
                    } finally {
                        lock.unlockWrite(stamp);
                    }
                }
            } catch (IOException ex) {
                if (debug) {
                    LOG.log(Level.SEVERE, null, ex);
                    LOG.log(Level.SEVERE, "THREAD POOL EXECUTOR HAS STOPPED");
                }
            } catch (InstantiationException | IllegalAccessException ex) {
                if (debug) {
                    LOG.log(Level.SEVERE, null, ex);
                    LOG.log(Level.SEVERE, "There was an error in the connection to the client");
                }
            } catch (IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
                LOG.log(Level.SEVERE, null, ex);
                LOG.log(Level.SEVERE, "There was an error in the connection to the client");
            }
        }
    }

    /**
     * Stop the ThreadPoolExcecutor.
     */
    protected void shutdown() throws IOException {
        pool.shutdown();
        socket.close();
    }
}
