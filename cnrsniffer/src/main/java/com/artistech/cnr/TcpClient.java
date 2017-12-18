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

    public static void send(MulticastSocket ms, Socket socket) throws IOException {
        byte[] buffer = new byte[8192];
        System.out.println("[socket, socket] receiving");

        DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
        while (true) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            byte[] data = dp.getData();
            int pduType = 255 & data[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer buf = ByteBuffer.wrap(data);

            System.out.println(pduTypeEnum);
            System.out.println(dp.getAddress().getHostName()+ ":" + dp.getPort());

            switch(pduTypeEnum) {
                case SIGNAL:
                case TRANSMITTER:
                    socketOutputStream.writeInt(data.length);
                    socketOutputStream.write(data);
                    socketOutputStream.flush();
                    break;
            }
        }
    }

    public static void send(MulticastSocket ms, String host, int port) throws IOException {
        System.out.println("waiting for server: " + host + ":" + port);

        //connect to waiting server...
        Socket socket = new Socket(host, port);
        System.out.println("[socket, host, port] receiving");

        send(ms, socket);
    }

    public static void main(String[] args) throws Exception {
        InetAddress group = InetAddress.getByName(Sniffer.MCAST_GRP);
        final MulticastSocket ms = new MulticastSocket(Sniffer.MCAST_PORT);
        //uncomment this if you want to listen on non-localhost IP
        ms.setInterface(InetAddress.getByName("127.0.0.1"));
        ms.joinGroup(group);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            try {
                ms.leaveGroup(group);
                ms.close();
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
        }));

        if(args.length > 0) {
            while(true) {
                try {
                    send(ms, args[0], TcpServer.TCP_PORT);
                } catch(IOException ex) {
                    ex.printStackTrace(System.out);
                }
            }
        }
    }
}
