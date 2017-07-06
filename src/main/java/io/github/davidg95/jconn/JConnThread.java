/*
 * DavidsCodeLibrary
 * Created by David Grant
 */
package io.github.davidg95.jconn;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread for handling incoming connections.
 *
 * @author David
 */
public class JConnThread extends Thread {

    private static final Logger LOG = Logger.getGlobal();

    private ObjectInputStream obIn; //InputStream for receiving data.
    private ObjectOutputStream obOut; //OutputStream for sending data

    private final StampedLock outLock;

    private final Socket socket; //The main socket

    private boolean conn_term = false;

    private final Semaphore sem; //Semaphore for the output stream.

    /**
     * All the detected method which have the @JConnMethod annotation.
     */
    private final LinkedList<Method> JCONNMETHODS;

    /**
     * Class which contained the annotated methods.
     */
    private final Object classToScan;

    /**
     * Constructor for Connection thread.
     *
     * @param name the name of the thread.
     * @param s the socket used for this connection.
     * @param o the class to scan.
     */
    public JConnThread(String name, Socket s, Object o) {
        super(name);
        this.socket = s;
        sem = new Semaphore(1);
        this.classToScan = o;
        JCONNMETHODS = new LinkedList<>();
        scanClass();
        outLock = new StampedLock();
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

    /**
     * Sends data to the client.
     *
     * @param data the data to send.
     * @throws IOException if there was a network error.
     */
    protected void sendData(JConnData data) throws IOException {
        final long stamp = outLock.writeLock();
        try {
            obOut.writeObject(data);
        } finally {
            outLock.unlockWrite(stamp);
        }
    }

    /**
     * Gets the IP address of the client.
     *
     * @return the IP address.
     */
    protected String getIP() {
        return socket.getInetAddress().getHostAddress();
    }

    /**
     * Main run method for the connection thread. This method initialises the
     * input and output streams and performs the client-server handshake. It
     * will check if the connection is allowed and block if it is not. It will
     * then enter a while loop where it will wait for data from the client. It
     * uses reflection to analyse the methods in this class and decides what
     * method to send the request to based on the annotation value and the flag.
     */
    @Override
    public void run() {
        try {
            obIn = new ObjectInputStream(socket.getInputStream());
            obOut = new ObjectOutputStream(socket.getOutputStream());
            obOut.flush();

            while (!conn_term) {
                final JConnData currentData = (JConnData) obIn.readObject();
                try {
                    sem.acquire();
                } catch (InterruptedException ex) {
                    if (JConnServer.DEBUG) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }

                final JConnData data = currentData.clone(); //Take a clone of the ConnectionData object

                if (JConnServer.DEBUG) {
                    LOG.log(Level.INFO, "Received " + data.getFlag() + " from client", data.getFlag());
                }

                for (Method m : JCONNMETHODS) { //Loop through every method in this class
                    final Annotation a = m.getAnnotation(JConnMethod.class); //Get the JConnMethod annotation
                    if (a.annotationType() == JConnMethod.class) { //Check if it has the JConnMethod annotation
                        final JConnMethod ja = (JConnMethod) a; //Get the JConnMethod annotation object to find out the flag name
                        final String flag = data.getFlag(); //Get the flag from the connection object
                        if (ja.value().equals(flag)) { //Check if the current flag matches the flag definted on the annotation
                            try {
                                final JConnData clone = data.clone(); //Take a clone of the connection data object
                                m.setAccessible(true); //Set the access to public
                                final Runnable run = () -> {
                                    try {
                                        final HashMap<String, Object> map = clone.getData(); //Get the parameters
                                        if (m.getParameterCount() != map.size()) { //Check the amount of paramters passed in matches the amount on the method.
                                            final long stamp = outLock.writeLock();
                                            try {
                                                obOut.writeObject(JConnData.create(flag).setType(JConnData.ILLEGAL_PARAM_LENGTH));
                                            } finally {
                                                outLock.unlockWrite(stamp);
                                            }
                                        } else {
                                            Iterator it = map.entrySet().iterator();
                                            Object[] params = new Object[clone.getData().size()];
                                            while (it.hasNext()) { //Iterate through the map of parameters
                                                Map.Entry pair = (Map.Entry) it.next();
                                                int currentPos = 0;
                                                for (Parameter p : m.getParameters()) {
                                                    final Annotation ap = p.getAnnotation(JConnParameter.class); //Get the JConnParameter annotation.
                                                    if (ap.annotationType() == JConnParameter.class) {
                                                        JConnParameter jp = (JConnParameter) ap;
                                                        if (jp.value().equals(pair.getKey())) { //Check if the annotation value matches the parameter.
                                                            params[currentPos] = pair.getValue(); //Add the parameter to the array.
                                                        }
                                                        currentPos++;
                                                    }
                                                }
                                                it.remove();
                                            }
                                            try {
                                                final Object ret = m.invoke(classToScan, params); //Invoke the method
                                                final long stamp = outLock.writeLock();
                                                try {
                                                    obOut.writeObject(JConnData.create(flag).setReturnValue(ret)); //Return the result
                                                } finally {
                                                    outLock.unlockWrite(stamp);
                                                }
                                            } catch (InvocationTargetException ex) {
                                                final long stamp = outLock.writeLock();
                                                try {
                                                    obOut.writeObject(JConnData.create(flag).setException(ex)); //Return the result
                                                } finally {
                                                    outLock.unlockWrite(stamp);
                                                }
                                            }
                                        }
                                    } catch (IllegalAccessException | IllegalArgumentException | IOException ex) {
                                        final long stamp = outLock.writeLock();
                                        try {
                                            obOut.writeObject(JConnData.create(flag).addParam("RETURN", ex));
                                        } catch (IOException ex1) {
                                            Logger.getLogger(JConnThread.class.getName()).log(Level.SEVERE, null, ex1);
                                        } finally {
                                            outLock.unlockWrite(stamp);
                                        }
                                    }
                                };
                                new Thread(run, flag).start(); //Run the thread which will invoke the method
                                break;
                            } catch (IllegalArgumentException ex) {
                                Logger.getLogger(JConnThread.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
                sem.release();
            }
            if (JConnServer.DEBUG) {
                LOG.log(Level.INFO, "Connection closing to client");
            }
        } catch (SocketException ex) {
            if (JConnServer.DEBUG) {
                LOG.log(Level.SEVERE, "The connection to the client was shut down forcefully");
            }
        } catch (IOException | ClassNotFoundException | CloneNotSupportedException | SecurityException ex) {
            if (JConnServer.DEBUG) {
                LOG.log(Level.SEVERE, null, ex);
            }
        } finally {
            JConnConnectionAccept.removeThread(this); //Remove the connection from the list.
            try {
                socket.close(); //Close the socket
                if (JConnServer.DEBUG) {
                    LOG.log(Level.INFO, "Connection terminated");
                }
            } catch (IOException ex) {
                if (JConnServer.DEBUG) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public void endConnection() {
        conn_term = true;
    }
}
