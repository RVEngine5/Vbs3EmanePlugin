/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acts as a bi-directional bridge between 2 sockets.
 */
public class Bridge implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(Bridge.class.getName());

    private final Thread t1;
    private final Thread t2;
    private final Socket sock1;
    private final Socket sock2;

    /**
     * Threadable function for copying data from one socket
     * and writing it to the other.
     *
     * This was originally written to use IOUtils copy, but
     * switched to manually copying data to be able to flush.
     *
     * IOUtils could work, but this works now.
     */
    private class Tx implements Runnable {

        private final InputStream is;
        private final OutputStream os;

        /**
         * Constructor
         *
         * @param is input stream
         * @param os output stream
         */
        public Tx(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }

        /**
         * Run Method.  Read data from one stream and write
         * the same data to the other stream.
         */
        @Override
        public void run() {
            try {

                byte[] data = new byte[TcpClient.BUFFER_SIZE * 2];

                while(!Rebroadcaster.INSTANCE.isHalted()) {
                    int len = is.read(data, 0, data.length);
                    os.write(data, 0, len);
                    os.flush();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Constructor.  Initializes 2 threads, one thread for each direction for socket comms.
     *
     * @param sock1 socket 1
     * @param sock2 socket 2
     */
    public Bridge(Socket sock1, Socket sock2) {
        this.sock1 = sock1;
        this.sock2 = sock2;
        t1 = new Thread(() -> {
            try {
                //the t1 thread will create a direction from sock1 to sock2
                Tx x = new Tx(sock1.getInputStream(), sock2.getOutputStream());
                //this is a blocking call
                x.run();
            } catch(IOException ex) {
            }
            //once x.run exits, close all sockets.
            //closing all sockets will ensure that all directions are closed
            //in the event of an exception in one direction.
            closeSocket(sock1);
            closeSocket(sock2);
        });

        t2 = new Thread(() -> {
            try {
                //the t2 thread will create a direction from sock2 to sock1
                Tx x = new Tx(sock2.getInputStream(), sock1.getOutputStream());
                //this is a blocking call
                x.run();
            } catch(IOException ex) {
            }
            //once x.run exits, close all sockets.
            //closing all sockets will ensure that all directions are closed
            //in the event of an exception in one direction.
            closeSocket(sock1);
            closeSocket(sock2);
        });

        //run t1 and t2 as daemon so that they don't stop the application from exiting.
        t1.setDaemon(true);
        t2.setDaemon(true);
    }

    /**
     * Close the socket and catch any IOException.
     *
     * @param socket the socket to close.
     */
    private static void closeSocket(Socket socket) {
        try {
            if(!socket.isClosed()) {
                socket.close();
            }
        } catch(IOException ex) {}
    }

    /**
     * Start each thread and wait for each thread to finish.
     */
    @Override
    public void run() {
        //start the threads
        t1.start();
        t2.start();

        LOGGER.log(Level.FINE, "Starting Server...");

        //wait for the threads to exit.
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ex) {
        }

        //halt (clean up resources).
        halt();
    }

    /**
     * Close all sockets.
     *
     * This will cause IO exceptions to fire, the run method of each Tx instance will exit and the t1 and t2 threds will
     * terminate.
     */
    public void halt() {
        closeSocket(sock1);
        closeSocket(sock2);
    }
}
