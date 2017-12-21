package com.artistech.cnr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.dis.TransmitterPdu;

/**
 * Listens for multi-cast data from CNR to send/stream to a listening server.
 */
public class TcpClient {

    /**
     * Send data from the multicast socket to the tcp socket.
     *
     * @param ms the multicast socket
     * @param socket the tcp socket
     * @throws IOException any error from read/writing socket data
     */
    public static void send(final MulticastSocket ms, Socket socket) throws IOException {
        byte[] buffer = new byte[8192];
        System.out.println("TcpClient.send[socket, socket]: receiving");

        DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
        while (true) {
            if(socket.isClosed()) {
                return;
            }
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            byte[] data = dp.getData();
            int pduType = 255 & data[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer bb = ByteBuffer.wrap(data);

            System.out.println(pduTypeEnum);
            System.out.println(dp.getAddress().getHostName() + ":" + dp.getPort());

            boolean send = true;
            switch (pduTypeEnum) {
                case TRANSMITTER:
                    TransmitterPdu tpdu = new TransmitterPdu();
                    tpdu.unmarshal(bb);
                    if(TcpServer.SENT.contains(tpdu.getTimestamp())) {
                        TcpServer.SENT.remove(tpdu.getTimestamp());
                        send = false;
                    }
                    break;
                case SIGNAL:
                    SignalPdu spdu = new SignalPdu();
                    spdu.unmarshal(bb);
                    if(TcpServer.SENT.contains(spdu.getTimestamp())) {
                        TcpServer.SENT.remove(spdu.getTimestamp());
                        send = false;
                    }
                    break;
                default:
                    send = false;
                    break;
            }
            if(send) {
                System.out.println("Writing to socket...");
                socketOutputStream.writeInt(data.length);
                socketOutputStream.write(data);
                socketOutputStream.flush();
            } else {
                System.out.println("Found Sent Packet");
            }
        }
    }

    /**
     * Send data from the multicast socket to the specified host/port pair.
     *
     * @param host to send to
     * @param port port to connect to
     * @throws IOException any error reading/writing to socket
     */
    public static void send(String host, int port) throws IOException {
        System.out.println("waiting for server: " + host + ":" + port);

        //connect to waiting server...
        final Socket socket = new Socket(host, port);
        System.out.println("[socket, host, port] sending");

        Thread t = new Thread(() -> {
            System.out.println("Starting Server Thread...");
            try {
                TcpServer.receive(socket, Rebroadcaster.INSTANCE);
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
            try {
                socket.close();
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
            try {
                Rebroadcaster.INSTANCE.resetSocket();
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
        });
        t.setDaemon(true);
        t.start();

        send(Rebroadcaster.INSTANCE.getSocket(), socket);
    }

    /**
     * Entry point for a client
     *
     * @param args expects 1 argument that is the server to connect to.
     */
    public static void main(String[] args) {
        if(args.length > 0) {
            while(true) {
                try {
                    send(args[0], TcpServer.TCP_PORT);
                } catch(IOException ex) {
//                    ex.printStackTrace(System.out);
                }
            }
        }
    }
}
