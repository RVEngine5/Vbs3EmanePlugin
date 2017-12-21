package com.artistech.cnr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Acts as a bi-directional bridge between 2 sockets.
 */
public class Bridge implements Runnable {

    public enum Behavior {
        NONE,
        DELAY,
        DROP,
        DELAY_AND_DROP
    }
    double delay = 0.025;
    double drop = 0.25;

    private final List<ReadData> delayed = new ArrayList<>();

    private final Thread t1;
    private final Thread t2;
    private final Socket sock1;
    private final Socket sock2;

    Behavior behavior = Behavior.NONE;

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

        private void delaySend(final ReadData rd, final Random rand) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep((long) rand.nextDouble() * 1000);
                    os.write(rd.getData(), 0, rd.getLen());
                    os.flush();
                } catch (InterruptedException | IOException ex) {
                }
            });
            t.setDaemon(true);
            t.start();
        }

        /**
         * Run Method.  Read data from one stream and write
         * the same data to the other stream.
         */
        public void run() {
            try {

                byte[] data = new byte[16384];
                Random rand = new Random(42);

                while(true) {
                    int len = is.read(data, 0, data.length);

                    //todo: add in some logic for randomly dropping/delaying some packets.
                    boolean write = true;

                    switch(behavior) {
                        case DELAY:
                            if(rand.nextDouble() < delay) {
                                ReadData rd = new ReadData(data, len);
                                delaySend(rd, rand);
                                write = false;
                            }
                            break;
                        case DELAY_AND_DROP:
                            if(rand.nextDouble() < delay) {
                                ReadData rd = new ReadData(data, len);
                                delaySend(rd, rand);
                                write = false;
                            } else if(rand.nextDouble() < drop) {
                                write = false;
                            }
                            break;
                        case DROP:
                            if(rand.nextDouble() < drop) {
                                write = false;
                            }
                            break;
                        case NONE:
                            break;
                        default:
                    }
                    if(write) {
                        os.write(data, 0, len);
                        os.flush();
                    }
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

        halt();
    }

    /**
     * Close all sockets.
     */
    public void halt() {
        closeSocket(sock1);
        closeSocket(sock2);
    }

    /**
     * Expects an argument of an IP address pair to bind.
     *
     * Starts a server, once a client connects to the server attempts to connect to the specified host.
     * Once the specified host is connected, the bridge thread is started.
     *
     * Allows for multiple bridges to be made to the same host port.  This will allow one instance to run where
     * EMANE is and allow routing to XCN IP addresses.
     *
     * The app will handle broken connections by resetting and starting again.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        BridgeDemux bd = new BridgeDemux();
        for(String arg : args) {
            String[] sp = arg.split(":");
            if(sp.length == 2) {
                BridgePair pair = new BridgePair(sp[0], sp[1]);
                bd.addPair(pair);
            }
        }

        if(!bd.getPairs().isEmpty()) {
            ServerSocket ss = new ServerSocket(TcpServer.TCP_PORT);
            while (true) {
                System.out.println("Starting Server...");
                Socket client = ss.accept();
                System.out.println("Client Connected...");

                if(bd.hasCnr(client.getInetAddress().getHostAddress())) {
                    String emane = bd.getEmane(client.getInetAddress().getHostAddress());
                    Thread t =  new Thread(() -> {
                        Bridge b = null;
                        try {
                            System.out.println("Connecting to " + emane);
                            final Socket server = new Socket(emane, TcpServer.TCP_PORT);

                            System.out.println("Starting Bridge...");
                            b = new Bridge(client, server);
                            b.run();
                        } catch (IOException ex) {
                            if (b != null) {
                                b.halt();
                            }
                        }
                    });
                    t.setDaemon(true);
                    t.start();
                }
            }
        }
    }
}
