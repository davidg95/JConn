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
import java.util.UUID;
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

    private final StampedLock outLock; //The StampedLock for protecting the output stream.

    private final Socket socket; //The main socket

    private boolean conn_term = false;

    private final Semaphore sem; //Semaphore for the output stream.

    /**
     * All the detected method which have the @JConnMethod annotation.
     */
    private final LinkedList<Method> JCONNMETHODS;

    private final Object methodClass;

    private final String address;

    /**
     * Constructor for Connection thread.
     *
     * @param name the name of the thread.
     * @param s the socket used for this connection
     * @param methods the JConn annotated method.
     * @param cls the class containing JConnMethods.
     * @throws java.lang.InstantiationException if there was an error creating
     * an instance of the method class.
     * @throws java.lang.IllegalAccessException if the method class is not
     * accessible.
     */
    public JConnThread(String name, Socket s, LinkedList<Method> methods, Object cls) throws InstantiationException, IllegalAccessException {
        super(name);
        this.socket = s;
        this.address = s.getInetAddress().getHostAddress();
        sem = new Semaphore(1);
        this.JCONNMETHODS = methods;
        outLock = new StampedLock();
        methodClass = cls;
    }

    /**
     * Calling this method will send the JConnData to the client. This method
     * will aquire a writeLock on the outLock StampedLock.
     *
     * @param data the data to send.
     * @throws IOException if there was a network error.
     */
    protected void sendData(JConnData data) throws IOException {
        if (conn_term) {
            return;
        }
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
                        final UUID uuid = data.getUuid();
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
                                                obOut.writeObject(JConnData.create(flag, uuid).setType(JConnData.ILLEGAL_PARAM_LENGTH));
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
                                                final Object ret = m.invoke(methodClass, params); //Invoke the method
                                                final long stamp = outLock.writeLock();
                                                try {
                                                    obOut.writeObject(JConnData.create(flag, uuid).setReturnValue(ret)); //Return the result
                                                } finally {
                                                    outLock.unlockWrite(stamp);
                                                }
                                            } catch (InvocationTargetException ex) {
                                                final long stamp = outLock.writeLock();
                                                try {
                                                    obOut.writeObject(JConnData.create(flag, uuid).setException(ex)); //Return the result
                                                } finally {
                                                    outLock.unlockWrite(stamp);
                                                }
                                            }
                                        }
                                    } catch (IllegalAccessException | IllegalArgumentException | IOException ex) {
                                        final long stamp = outLock.writeLock();
                                        try {
                                            obOut.writeObject(JConnData.create(flag, uuid).addParam("RETURN", ex));
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
            JConnServer.LISTENERS.forEach((l) -> { //Alert the listeners of the end of the connection.
                l.onConnectionDrop(new JConnEvent("The connection to " + address + " has been closed"));
            });
        } catch (SocketException ex) {
            if (JConnServer.DEBUG) {
                LOG.log(Level.SEVERE, "The connection to the client was shut down forcefully");
            }
            JConnServer.LISTENERS.forEach((l) -> { //Alert the listeners of the end of the connection.
                l.onConnectionDrop(new JConnEvent("The connection to " + address + " has been closed"));
            });
        } catch (IOException | ClassNotFoundException | CloneNotSupportedException | SecurityException ex) {
            if (JConnServer.DEBUG) {
                LOG.log(Level.SEVERE, null, ex);
            }
            JConnServer.LISTENERS.forEach((l) -> { //Alert the listeners of the end of the connection.
                l.onConnectionDrop(new JConnEvent("There was an error in the connection to " + address + ". The connection has been closed."));
            });
        } finally {
            JConnConnectionAccept.removeThread(this); //Remove the connection from the list.
            conn_term = false;
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
