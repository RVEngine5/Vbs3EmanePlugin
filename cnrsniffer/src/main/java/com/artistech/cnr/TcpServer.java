package com.artistech.cnr;

import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dis.TransmitterPdu;

import java.io.*;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Listens for data from a client that is receiving PDU data.
 * Unpack data from the client and play the audio.
 */
public class TcpServer {

    public static final List<Long> SENT = new ArrayList<>();
    public static final int TCP_PORT = 6789;

    /**
     * Receive data from the socket and re-broadcast it on the local multicast channel.
     *
     * @param connectionSocket
     * @param rebroadcaster
     * @throws IOException
     */
    public static void receive(Socket connectionSocket, Rebroadcaster rebroadcaster) throws IOException {
        while (true) {
            DataInputStream dIn = new DataInputStream(connectionSocket.getInputStream());

            // read length of incoming message
            int length = dIn.readInt();
            byte[] message = null;
            // read the message
            if(length>0) {
                message = new byte[length];
                dIn.readFully(message, 0, message.length);
            }

            int pduType = 255 & message[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer bb = ByteBuffer.wrap(message);

            switch(pduTypeEnum) {
                case TRANSMITTER:
                    TransmitterPdu tpdu = new TransmitterPdu();
                    tpdu.unmarshal(bb);
                    SENT.add(tpdu.getTimestamp());
                    break;
                case SIGNAL:
                    SignalPdu spdu = new SignalPdu();
                    spdu.unmarshal(bb);
                    SENT.add(spdu.getTimestamp());
                    break;
            }

            if(message != null) {
                byte[] data = message;

                try {
                    System.out.println("Rebroadcasting on multicast channel");
                    rebroadcaster.send(data);

                } catch(IOException ex) {
                    ex.printStackTrace(System.out);
                }
            }
        }
    }

    /**
     * Start the server
     *
     * @param argv no arguments expected
     * @throws IOException Exception creating a server socket
     */
    private static void main(String argv[]) throws IOException {
        ServerSocket socket = new ServerSocket(TCP_PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            try {
                socket.close();
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
        }));

        System.out.println("waiting for client...");
        while(true) {
            try {
                final Socket connectionSocket = socket.accept();
                System.out.println("receiving...");
                Thread t = new Thread(() -> {
                    System.out.println("Starting client thread");
                    try {
                        TcpClient.send(Rebroadcaster.INSTANCE.getSocket(), connectionSocket);
                    } catch (IOException ex) {
                        ex.printStackTrace(System.out);
                    }
                });
                t.setDaemon(true);
                t.start();
                receive(connectionSocket, Rebroadcaster.INSTANCE);
            } catch (IOException ex) {
                ex.printStackTrace(System.out);
            }
        }
    }
}
