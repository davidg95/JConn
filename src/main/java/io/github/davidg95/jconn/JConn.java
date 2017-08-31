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
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for sending JConn requests to a JConn server. This will send requests
 * to the server. When data is received from the server, it is first checked to
 * see if it is a reply from any request. If not, then it is passed to the
 * default runner specified by the programmer.
 *
 * @author David
 */
public class JConn {

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private final HashMap<UUID, JConnRunnable> incomingQueue; //The queue for all incoming packets.
    private final StampedLock queueLock; //Lock for the queue.
    private IncomingThread inc; //The thread which handles the incoming packets.

    private boolean connected;

    private String ip;
    private int port;

    private final List<JConnListener> listeners;
    private final StampedLock listenerLock;

    /**
     * The duration of time in milliseconds between reconnection attempts.
     */
    public static int RECONNECT_INTERVAL = 1000;

    private boolean retry;

    private boolean run;

    /**
     * Creates a new JConn object.
     */
    public JConn() {
        incomingQueue = new HashMap<>();
        connected = false;
        queueLock = new StampedLock();
        listeners = new LinkedList<>();
        listenerLock = new StampedLock();
        retry = true;
    }

    private void keepAlive() {
        final Runnable keepAliveRun = new Runnable() {
            @Override
            public void run() {
                while (run) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(JConn.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                        out.writeObject(JConnData.create("KEEP_ALIVE").setType(JConnData.KEEP_ALIVE));
                    } catch (IOException ex) {
                        final long stamp = listenerLock.readLock();
                        try {
                            listeners.forEach((l) -> {
                                l.onConnectionDrop(new JConnEvent("The connection has been lost"));
                            });
                        } finally {
                            listenerLock.unlockRead(stamp);
                        }
                    }
                }
            }
        };
        final Thread keepAliveThread = new Thread(keepAliveRun, "KEEP_ALIVE");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }

    /**
     * This thread is the entry point for all incoming data.
     */
    private class IncomingThread extends Thread {

        private final ObjectInputStream in;

        /**
         * Constructor which creates the IncomingThread.
         *
         * @param in the input stream where the data comes from. recognised.
         */
        private IncomingThread(ObjectInputStream in) {
            super("Incoming_Thread");
            this.in = in;
        }

        /**
         * Main logic for incoming thread. When data is received, it is checked
         * to see if it is a reply or an exception. If it is a reply, or an
         * exception, then the queue if checked for the thread which made the
         * request. The reply is then returned to the thread. If it was no a
         * reply, then it is passed into the runnable specified by the user
         * which will contain the users own implementation.
         */
        @Override
        public void run() {
            try {
                while (run) {
                    try {
                        final JConnData data = (JConnData) in.readObject(); //Get the data
                        final UUID uuid = data.getUuid(); //Get the UUID
                        switch (data.getType()) {
                            case JConnData.RETURN:
                            case JConnData.EXCEPTION:
                                //Check if this was a reply to a request.
                                new Thread() {
                                    @Override
                                    public void run() {
                                        boolean found = false;
                                        Map.Entry remove = null;
                                        while (!found) {
                                            final long stamp = queueLock.readLock();
                                            try {
                                                for (Map.Entry me : incomingQueue.entrySet()) { //Loop through the waiting threads
                                                    if (me.getKey().equals(uuid)) { //Check if the flag equals the flag on the blocked thread
                                                        ((JConnRunnable) me.getValue()).run(data); //Unblock the thread.
                                                        found = true;
                                                        remove = me; //Keep a reference to the entry, so it can be removed.
                                                        break;
                                                    }
                                                }
                                            } finally {
                                                queueLock.unlockRead(stamp);
                                            }
                                        }
                                        final long writeStamp = queueLock.writeLock();
                                        try {
                                            incomingQueue.entrySet().remove(remove); //Remove the entry as it is no longer needed.
                                        } finally {
                                            queueLock.unlockWrite(writeStamp);
                                        }
                                    }
                                }.start(); //Search the queue for the reqeust source.
                                break;
                            case JConnData.TERMINATE_CONNECTION: //If it was a request to terminate the connection.
                            {
                                final long stamp = listenerLock.readLock();
                                try {
                                    listeners.forEach((l) -> {
                                        try {
                                            l.onServerGracefulEnd();
                                        } catch (Exception e) {

                                        }
                                    });
                                    endConnection();
                                } finally {
                                    listenerLock.unlockRead(stamp);
                                }
                                break;
                            }
                            default: //If it is not known.
                            {
                                final long stamp = listenerLock.readLock();
                                try {
                                    listeners.forEach((l) -> { //Alert the listeners of the data.
                                        try {
                                            l.onReceive(data);
                                        } catch (Exception e) {

                                        }
                                    });
                                } finally {
                                    listenerLock.unlockRead(stamp);
                                }
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        if (ex instanceof SocketException) {
                            throw (SocketException) ex;
                        }
                    }
                }
            } catch (SocketException ex) {
                if (connected) {
                    connected = false;
                    {
                        final long stamp = listenerLock.readLock();
                        try {
                            listeners.forEach((l) -> { //Alert the listeners of the connection loss
                                new Thread() {
                                    @Override
                                    public void run() {
                                        try {
                                            l.onConnectionDrop(new JConnEvent("The connection to " + ip + ":" + port + " has been lost, attempting reconnection"));
                                        } catch (Exception e) { //Any exception which comes from the onConnectionDrop().

                                        }
                                    }
                                }.start();
                            });
                        } finally {
                            listenerLock.unlockRead(stamp);
                        }
                    }
                    //Attempt a reconnection.
                    try {
                        retry = true;
                        while (retry) {
                            try {
                                connect(ip, port); //Attempt a reconnect.
                                final long stamp = listenerLock.readLock();
                                try {
                                    listeners.forEach((l) -> { //Alert the listeners that the connection has been reestablished.
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                try {
                                                    l.onConnectionEstablish(new JConnEvent("The connection to " + ip + ":" + port + " has been reestablished"));
                                                } catch (Exception e) { //Any exception which comes from the onConnectionReestablish().

                                                }
                                            }
                                        }.start();
                                    });
                                } finally {
                                    listenerLock.unlockRead(stamp);
                                }
                                retry = false;
                            } catch (IOException ex2) {
                                Thread.sleep(RECONNECT_INTERVAL); //Wait and try again
                            }
                        }
                    } catch (InterruptedException ex1) {
                        Logger.getLogger(JConn.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            }
        }
    }

    /**
     * Method to open the connection to the server.
     *
     * @param ip the IP address to connect to.
     * @param port the port number to connect to.
     * @param keepAlive Specifies if keep-alive should be enabled.
     * @throws IOException if there was an error connecting.
     */
    public void connect(String ip, int port, boolean keepAlive) throws IOException {
        if (connected) {
            throw new IOException("There is already an active connection on this JConn object. Close this connection or create a new instance of the JConn class");
        }
        socket = new Socket(ip, port);
        this.ip = ip;
        this.port = port;
        retry = true;
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        run = true;
        inc = new IncomingThread(in);
        inc.start();
        if (keepAlive) {
            keepAlive();
        }
        connected = true;
    }

    /**
     * Method to open the connection to the server.
     *
     * Keep-alive will be disabled.
     *
     * @param ip the IP address to connect to.
     * @param port the port number to connect to.
     * @throws IOException if there was an error connecting.
     */
    public void connect(String ip, int port) throws IOException {
        connect(ip, port, false);
    }

    /**
     * Method to send data to the server. This method will execute the runnable
     * that is passed in on a successful reply from the server. The calling
     * thread will continue with its execution regardless. If the connection to
     * the server has not yet been opened, an IOException will be thrown.
     *
     * @param data the data to send.
     * @param run the runnable to execute on a successful response.
     * @return JConnStatus so the status of the request can be checked.
     * @throws IOException if there was an error sending the data.
     */
    public JConnStatus sendData(JConnData data, JConnRunnable run) throws IOException {
        if (!connected) {
            throw new IOException("No connection to server!");
        }
        final JConnStatus status = new JConnStatus();
        out.writeObject(data);
        status.setSent(true);
        final ReturnData returnData = new ReturnData();
        //Create the runnable that will execute on a reply
        final JConnRunnable runnable = (tempReply) -> {
            returnData.object = tempReply;
            returnData.cont = true;
        };
        final long stamp = queueLock.writeLock();
        try {
            incomingQueue.put(data.getUuid(), runnable); //Add the runnable to the queue
        } finally {
            queueLock.unlockWrite(stamp);
        }
        final Runnable runReturn = () -> {
            waitHere(returnData); //Wait for the return;
            status.setReceived(true);
            JConnData reply = (JConnData) returnData.object; //Get the reply
            if (reply.getFlag().equals("ILLEGAL_PARAM_LENGTH")) { //Check if there was an illegal paramter length
                //throw new IOException("Illegal parameter length, the correct number of parameters was not supplied");
                return;
            }
            run.run(reply); //Run the runnable that was passed in by the user, passing in the reply.
        };
        final Thread thread = new Thread(runReturn, "REQUEST-" + data.getUuid());
        thread.start(); //Start the thread to wait for the reply.
        return status;
    }

    /**
     * Method to send data to the server. This will block the calling thread
     * until there has been a successful reply from the server. If the
     * connection to the server has not yet been opened, an IOException will be
     * thrown.
     *
     * @param data the data to send.
     * @return the reply from the server as an Object.
     * @throws IOException if there was an error sending the data.
     */
    public Object sendData(JConnData data) throws IOException, Throwable {
        if (!connected) {
            throw new IOException("No connection to server!");
        }
        out.writeObject(data);
        JConnData reply;
        final ReturnData returnData = new ReturnData();
        //Create the runnable that will be executed on a reply
        final JConnRunnable runnable = (tempReply) -> {
            returnData.object = tempReply;
            returnData.cont = true;
        };
        final long stamp = queueLock.writeLock();
        try {
            incomingQueue.put(data.getUuid(), runnable); //Add the runnable to the queue
        } finally {
            queueLock.unlockWrite(stamp);
        }
        waitHere(returnData); //Wait here until a reply is received
        reply = (JConnData) returnData.object; //Get the reply
        if (reply.getType() == JConnData.ILLEGAL_PARAM_LENGTH) { //Check if it is an illegal parameter length
            throw new IOException("Illegal parameter length, the correct number of parameters was not supplied");
        } else if (reply.getType() == JConnData.EXCEPTION) {
            throw reply.getException();
        }
        return reply.getReturnValue(); //Return the reply
    }

    /**
     * Method to wait until a reply is received.
     *
     * @param ret the object to wait on.
     */
    private void waitHere(ReturnData ret) {
        try {
            while (!ret.cont) {
                Thread.sleep(50); //Wait until a reply has been received
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JConn.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Stops the connection to the server.
     *
     * @throws IOException if there was an error ending the connection.
     */
    public void endConnection() throws IOException {
        connected = false;
        socket.close();
        run = false;
        in.close();
        out.flush();
        out.close();
    }

    /**
     * Register a JConnListener to receive JConnEvents.
     *
     * @param listener the JConnListener.
     */
    public void registerListener(JConnListener listener) {
        final long stamp = listenerLock.writeLock();
        try {
            listeners.add(listener);
        } finally {
            listenerLock.unlockWrite(stamp);
        }
    }

    /**
     * Stop retrying the connection when the connection to the server is
     * dropped.
     */
    public void cancelRetry() {
        retry = false;
    }

    /**
     * Check the state of this connection.
     *
     * @return true if the connection is up, false if it is not.
     */
    public boolean isUp() {
        return connected;
    }

    /**
     * Returns information about the connection.
     *
     * @return connection info as a String.
     */
    @Override
    public String toString() {
        return (this.connected ? "Connected to " + ip + ":" + port : "No connection");
    }

    /**
     * Class which holds the return data and the flag.
     */
    private class ReturnData {

        private Object object; //The reply.
        private boolean cont = false; //This flag indicates when the reply has been received.
    }
}
