package com.artistech.cnr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Acts as a bi-directional bridge between 2 sockets.
 */
public class Bridge implements Runnable {

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
        public void run() {
            try {

                byte[] data = new byte[16384];

                while(true) {
                    int len = is.read(data, 0, data.length);
                    os.write(data, 0, len);
                    os.flush();
                }
            } catch (IOException ex) {
                ex.printStackTrace(System.out);
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
                Tx x = new Tx(sock1.getInputStream(), sock2.getOutputStream());
                x.run();
            } catch(IOException ex) {
            }
            closeSocket(sock1);
            closeSocket(sock2);
        });

        t2 = new Thread(() -> {
            try {
                Tx x = new Tx(sock2.getInputStream(), sock1.getOutputStream());
                x.run();
            } catch(IOException ex) {
            }
            closeSocket(sock1);
            closeSocket(sock2);
        });

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
    public void run() {
        t1.start();
        t2.start();
        System.out.println("Starting Server...");

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ex) {
        }
    }

    /**
     * Close all sockets.
     */
    public void halt() {
        closeSocket(sock1);
        closeSocket(sock2);
    }

    /**
     * Expects an argument of an IP address to connect to.
     * Starts a server, once a client connects to the server attempts to connect to the specified host.
     * Once the specified host is connected, the bridge thread is started.
     *
     * The app will handle broken connections by resetting and starting again.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        if(args.length > 0) {
            Bridge b = null;
            while (true) {
                try {
                    System.out.println("Starting Server...");
                    ServerSocket ss = new ServerSocket(TcpServer.TCP_PORT);
                    Socket client = ss.accept();
                    System.out.println("Client Connected...");

                    System.out.println("Connecting to " + args[0]);
                    final Socket server = new Socket(args[0], TcpServer.TCP_PORT);

                    System.out.println("Starting Bridge...");
                    b = new Bridge(client, server);
                    b.run();
                } catch (IOException ex) {
                    if (b != null) {
                        b.halt();
                    }
                }
            }
        }
    }
}
