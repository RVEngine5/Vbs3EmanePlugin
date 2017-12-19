package com.artistech.cnr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Bridge implements Runnable {

    private final Thread t1;
    private final Thread t2;
    private final Socket sock1;
    private final Socket sock2;

    private class Tx implements Runnable {

        private final InputStream is;
        private final OutputStream os;

        public Tx(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }

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

    private static void closeSocket(Socket socket) {
        try {
            if(!socket.isClosed()) {
                socket.close();
            }
        } catch(IOException ex) {}
    }

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


    public void halt() {
        closeSocket(sock1);
        closeSocket(sock2);
    }

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
