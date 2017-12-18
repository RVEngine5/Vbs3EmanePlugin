package com.artistech.cnr;

import edu.nps.moves.dis.OneByteChunk;
import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dismobile.TransmitterPdu;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Listens for data from a client that is receiving PDU data.
 * Unpack data from the client and play the audio.
 */
public class TcpServer {

    public static final int TCP_PORT = 6789;

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

            if(message != null) {
                byte[] data = message;
//                int pduType = 255 & data[2];
//                PduType pduTypeEnum = PduType.lookup[pduType];
//                ByteBuffer buf = ByteBuffer.wrap(data);

                try {
                    rebroadcaster.send(data);
                } catch(IOException ex) {
                    ex.printStackTrace(System.out);
                }

//                //examine packet data and play audio
//                System.out.println(pduTypeEnum);
//
//                switch (pduTypeEnum) {
//                    case SIGNAL:
//                        SignalPdu spdu = new SignalPdu();
//                        spdu.unmarshal(buf);
//
//                        Sniffer.printInfo(spdu);
//                        try (ByteArrayOutputStream baos = Sniffer.getData(spdu)) {
//                            //audio is: 16-bit Linear PCM 2's complement, Big Endian (4) <- ENCODING SCHEME 4
//                            rap.play(baos.toByteArray());
//                        }
//                        break;
//                    case TRANSMITTER:
//                        TransmitterPdu tpdu = new TransmitterPdu();
//                        tpdu.unmarshal(buf);
//                        Sniffer.printInfo(tpdu);
//
//                        break;
//                    default:
//                        System.out.println("Unknown Type:" + pduTypeEnum);
//                        break;
//                }
            }
        }
    }

    public static void main(String argv[]) throws IOException {
        ServerSocket socket = new ServerSocket(TCP_PORT);

        //final RawAudioPlay rap = new RawAudioPlay();
        final Rebroadcaster rebroadcaster = new Rebroadcaster();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            //rap.close();
            rebroadcaster.close();
            try {
                socket.close();
            } catch(IOException ex) {
                ex.printStackTrace(System.out);
            }
        }));


        System.out.println("waiting for client...");
        boolean cont = true;
        while(cont) {
            try {
                final Socket connectionSocket = socket.accept();
                System.out.println("receiving...");
                Thread t = new Thread(() -> {
                    try {
                        InetAddress group = InetAddress.getByName(Sniffer.MCAST_GRP);
                        final MulticastSocket ms = new MulticastSocket(Sniffer.MCAST_PORT);
                        //uncomment this if you want to listen on non-localhost IP
                        ms.setInterface(InetAddress.getByName("127.0.0.1"));
                        ms.joinGroup(group);

                        TcpClient.send(ms, connectionSocket);
                    } catch (IOException ex) {
                        ex.printStackTrace(System.out);
                    }
                });
                t.setDaemon(true);
                t.start();
                receive(connectionSocket, rebroadcaster);
            } catch (IOException ex) {
                ex.printStackTrace(System.out);
            }
        }
    }
}
