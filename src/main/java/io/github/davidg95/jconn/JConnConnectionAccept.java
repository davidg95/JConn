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

    private final ServerSocket socket;

    private final Object classToScan;

    private static final List<JConnThread> THREADS = new LinkedList<>();
    private static final StampedLock LOCK = new StampedLock();

    /**
     * All the detected method which have the @JConnMethod annotation.
     */
    private final LinkedList<Method> JCONNMETHODS;

    /**
     * Constructor which starts the ThreadPoolExcecutor.
     *
     * @param PORT the port number to listen on.
     * @param classToScan the class to be scanned for annotations.
     * @throws IOException if there was a network error.
     */
    public JConnConnectionAccept(int PORT, Object classToScan) throws IOException {
        super("ConnectionAcceptThread");
        this.socket = new ServerSocket(PORT);
        this.classToScan = classToScan;
        PORT_IN_USE = PORT;
        JCONNMETHODS = new LinkedList<>();
        scanClass();
    }

    /**
     * Returns a list of all the connection thread objects.
     *
     * @return a List of JConnThreads.
     */
    protected static List<JConnThread> getAllThreads() {
        return THREADS;
    }

    /**
     * Removed a thread from the list of threads.
     *
     * @param th the thread to remove.
     */
    protected static void removeThread(JConnThread th) {
        final long stamp = LOCK.writeLock();
        try {
            THREADS.remove(th);
        } finally {
            LOCK.unlockWrite(stamp);
        }
    }

    /**
     * Get a read lock on the threads list.
     *
     * @return the stamp for the lock.
     */
    protected static long readLock() {
        return LOCK.readLock();
    }

    /**
     * Unlock the read lock for the threads list.
     *
     * @param stamp the stamp for the lock.
     */
    protected static void unlockRead(final long stamp) {
        LOCK.unlockRead(stamp);
    }

    /**
     * Scans this class and finds all method with the JConnMethod annotation.
     */
    private void scanClass() {
        final Method[] methods = classToScan.getClass().getDeclaredMethods(); //Get all the methods in this class
        for (Method m : methods) { //Loop through each method
            if (m.isAnnotationPresent(JConnMethod.class)) { //Check if the annotation is a JConnMethod annotation
                JCONNMETHODS.add(m);
            }
        }
    }

    @Override
    public void run() {
        if (JConnServer.DEBUG) {
            LOG.log(Level.INFO, "Starting Thread Pool Excecutor");
        }
        final ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_CONN, MAX_QUEUE, 50000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(MAX_QUEUE));
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        try {
            if (JConnServer.DEBUG) {
                LOG.log(Level.INFO, "Local IP address is " + InetAddress.getLocalHost().getHostAddress());
                LOG.log(Level.INFO, "Server Socket running on port number " + PORT_IN_USE);
            }
        } catch (UnknownHostException ex) {
            if (JConnServer.DEBUG) {
                LOG.log(Level.WARNING, "For some reason, the ip address of the local server could not be retrieved");
            }
        }
        if (JConnServer.DEBUG) {
            LOG.log(Level.INFO, "Ready to accept connections");
        }
        for (;;) {
            try {
                final Socket incoming = socket.accept(); //Wait for a connection.
                if (JConnServer.DEBUG) {
                    LOG.log(Level.INFO, "Connection from " + incoming.getInetAddress().getHostAddress());
                }
                final JConnThread th = new JConnThread(socket.getInetAddress().getHostAddress(), incoming, JCONNMETHODS, classToScan);
                pool.submit(th); //Submit the socket to the excecutor.
                final long stamp = LOCK.writeLock();
                try {
                    THREADS.add(th);
                } finally {
                    LOCK.unlockWrite(stamp);
                }
            } catch (IOException ex) {
                if (JConnServer.DEBUG) {
                    LOG.log(Level.SEVERE, null, ex);
                    LOG.log(Level.SEVERE, "THREAD POOL EXECUTOR HAS STOPPED");
                }
            } catch (InstantiationException | IllegalAccessException ex) {
                if (JConnServer.DEBUG) {
                    LOG.log(Level.SEVERE, null, ex);
                    LOG.log(Level.SEVERE, "There was an error in the connection to the client");
                }
            }
        }
    }
}
