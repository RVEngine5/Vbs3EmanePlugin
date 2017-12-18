package com.artistech.cnr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import edu.nps.moves.disenum.PduType;

/**
 * Listens for multi-cast data from CNR to send/stream to a listening server.
 */
public class TcpClient {

    public static void send(final MulticastSocket ms, Socket socket) throws IOException {
        byte[] buffer = new byte[8192];
        System.out.println("TcpClient.send[socket, socket]: receiving");

        DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
        while (true) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            byte[] data = dp.getData();
            int pduType = 255 & data[2];
            PduType pduTypeEnum = PduType.lookup[pduType];

            System.out.println(pduTypeEnum);
            System.out.println(dp.getAddress().getHostName()+ ":" + dp.getPort());

            switch(pduTypeEnum) {
                case SIGNAL:
                case TRANSMITTER:
                    System.out.println("Writing to socket...");
                    socketOutputStream.writeInt(data.length);
                    socketOutputStream.write(data);
                    socketOutputStream.flush();
                    break;
            }
        }
    }

    public static void send(String host, int port) throws IOException {
        System.out.println("waiting for server: " + host + ":" + port);

        //connect to waiting server...
        final Socket socket = new Socket(host, port);
        System.out.println("[socket, host, port] sending");

        Thread t = new Thread(() -> {
            System.out.println("Starting Server Thread...");
            try {
                TcpServer.receive(socket, Rebroadcaster.INSTANCE);
                socket.close();
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
        });
        t.setDaemon(true);
        t.start();

        send(Rebroadcaster.INSTANCE.getSocket(), socket);
    }

    public static void main(String[] args) throws Exception {
        if(args.length > 0) {
            while(true) {
                try {
                    send(args[0], TcpServer.TCP_PORT);
                } catch(IOException ex) {
                    ex.printStackTrace(System.out);
                }
            }
        }
    }
}
