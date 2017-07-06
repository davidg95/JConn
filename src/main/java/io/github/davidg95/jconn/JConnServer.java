/*
 * DavidsCodeLibrary
 * Created by David Grant
 */
package io.github.davidg95.jconn;

import java.io.IOException;

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

    /**
     * Start a new instance of JConnServer on the specified port.
     *
     * @param port the port to listen on.
     * @param classToScan the class to scan for annotations on methods.
     * @throws IOException if there was an error ins starting the server.
     */
    public static void start(int port, Object classToScan) throws IOException {
        start(port, classToScan, false);
    }

    /**
     * Start a new instance of JConnServer on the specified port.
     *
     * @param port the port to listen on.
     * @param classToScan the class to scan for annotations on methods.
     * @param debug indicates if log output should be included.
     * @throws IOException if there was an error ins starting the server.
     */
    public static void start(int port, Object classToScan, boolean debug) throws IOException {
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
     * @throws IOException if there was an error.
     */
    public static void sendData(String ip, JConnData data) throws IOException {
        if (ip == null) {
            for (JConnThread thread : JConnConnectionAccept.getAllThreads()) {
                thread.sendData(data);
            }
        } else {
            for (JConnThread thread : JConnConnectionAccept.getAllThreads()) {
                if (thread.getIP().equals(ip)) {
                    thread.sendData(data);
                    return;
                }
            }
        }
    }
}
