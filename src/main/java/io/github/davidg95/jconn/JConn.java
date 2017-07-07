/*
 * JConn networking framewotk.
 * Created by David Grant
 */
package io.github.davidg95.jconn;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
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

    private final HashMap<String, JConnRunnable> incomingQueue;
    private final StampedLock queueLock;
    private IncomingThread inc;
    private final JConnRunnable run;

    private boolean connected;

    /**
     * Creates a new JConn object.
     *
     * @param run the runnable to execute when an unknown flag is received from
     * the server.
     */
    public JConn(JConnRunnable run) {
        incomingQueue = new HashMap<>();
        this.run = run;
        connected = false;
        queueLock = new StampedLock();
    }

    /**
     * This thread is the entry point for all incoming data.
     */
    private class IncomingThread extends Thread {

        private final ObjectInputStream in;
        private boolean run;
        private final JConnRunnable runner;

        private IncomingThread(ObjectInputStream in, JConnRunnable runner) {
            super("Incoming_Thread");
            this.in = in;
            run = true;
            this.runner = runner;
        }

        @Override
        public void run() {
            try {
                while (run) {
                    final JConnData data = (JConnData) in.readObject(); //Get the data
                    final String flag = data.getFlag(); //Get the flag
                    if (data.getType() == JConnData.RETURN || data.getType() == JConnData.EXCEPTION) { //Check if this was a reply to a request.
                        new Thread() {
                            @Override
                            public void run() {
                                boolean found = false;
                                Map.Entry remove = null;
                                while (!found) {
                                    final long stamp = queueLock.readLock();
                                    try {
                                        for (Map.Entry me : incomingQueue.entrySet()) { //Loop through the waiting threads
                                            if (me.getKey().equals(flag)) { //Check if the flag equals the flag on the blocked thread
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
                    } else {
                        runner.run(data); //If the flag was not recognised, then the deafult runned is executed.
                    }
                }
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(JConn.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        private void stopRun() {
            run = false;
        }
    }

    /**
     * Method to open the connection to the server.
     *
     * @param ip the IP address to connect to.
     * @param port the port number to connect to.
     * @throws IOException if there was an error connecting.
     */
    public void connect(String ip, int port) throws IOException {
        socket = new Socket(ip, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        inc = new IncomingThread(in, run);
        inc.start();
        connected = true;
    }

    /**
     * Method to send data to the server. This method will execute the runnable
     * that is passed in on a successful reply from the server. The calling
     * thread will continue with its execution regardless. If the connection to
     * the server has not yet been opened, an IOException will be thrown.
     *
     * @param data the data to send.
     * @param run the runnable to execute on a successful response.
     * @throws IOException if there was an error sending the data.
     */
    public void sendData(JConnData data, JConnRunnable run) throws IOException {
        if (!connected) {
            throw new IOException("No connection to server!");
        }
        out.writeObject(data);
        final ReturnData returnData = new ReturnData();
        //Create the runnable that will execute on a reply
        final JConnRunnable runnable = (tempReply) -> {
            returnData.object = tempReply;
            returnData.cont = true;
        };
        final long stamp = queueLock.writeLock();
        try {
            incomingQueue.put(data.getFlag(), runnable); //Add the runnable to the queue
        } finally {
            queueLock.unlockWrite(stamp);
        }
        new Thread() {
            @Override
            public void run() {
                waitHere(returnData); //Wait for the return;
                JConnData reply = (JConnData) returnData.object; //Get the reply
                if (reply.getFlag().equals("ILLEGAL_PARAM_LENGTH")) { //Check if there was an illegal paramter length
                    //throw new IOException("Illegal parameter length, the correct number of parameters was not supplied");
                    return;
                }
                run.run(reply); //Run the runnable that was passed in by the user, passing in the reply.
            }
        }.start();
    }

    /**
     * Method to send data to the server. This will block the calling thread
     * until there has been a successful reply from the server. If the
     * connection to the server has not yet been opened, an IOException will be
     * thrown.
     *
     * @param data the data to send.
     * @return the data that was returned.
     * @throws IOException if there was an error sending the data.
     */
    public JConnData sendData(JConnData data) throws Exception, IOException {
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
            incomingQueue.put(data.getFlag(), runnable); //Add the runnable to the queue
        } finally {
            queueLock.unlockWrite(stamp);
        }
        waitHere(returnData); //Wait here until a reply is received
        reply = (JConnData) returnData.object; //Get the reply
        if (reply.getType() == JConnData.ILLEGAL_PARAM_LENGTH) { //Check if it is an illegal parameter length
            throw new IOException("Illegal parameter length, the correct number of parameters was not supplied");
        } else if (reply.getType() == JConnData.EXCEPTION) {
            throw (Exception) reply.getException();
        }
        return reply; //Return the reply
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
        inc.stopRun();
        in.close();
        out.flush();
        out.close();
        socket.close();
        connected = false;
    }

    private class ReturnData {

        private Object object;
        private boolean cont = false;
    }
}
