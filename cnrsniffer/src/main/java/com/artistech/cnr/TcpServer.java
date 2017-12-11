package com.artistech.cnr;

import edu.nps.moves.dis.OneByteChunk;
import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dismobile.TransmitterPdu;

import java.io.*;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;

public class TcpServer {
    public static void main(String argv[]) throws Exception {
        String clientSentence;
        String capitalizedSentence;
        ServerSocket welcomeSocket = new ServerSocket(6789);

        final RawAudioPlay rap = new RawAudioPlay();
        boolean cont = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            rap.close();
            try {
                welcomeSocket.close();
            } catch(IOException ex) {
            }
        }));

        System.out.println("waiting...");
        Socket connectionSocket = welcomeSocket.accept();
        System.out.println("receiving...");
        while (true) {
            //audio is: 16-bit Linear PCM 2's complement, Big Endian (4) <- ENCODING SCHEME 4
            DataInputStream dIn = new DataInputStream(connectionSocket.getInputStream());

            int length = dIn.readInt();                    // read length of incoming message
            System.out.println(length);
            byte[] message = null;
            if(length>0) {
                message = new byte[length];
                dIn.readFully(message, 0, message.length); // read the message
            }
            if(message != null) {
                byte[] data = message;
                int pduType = 255 & data[2];
                PduType pduTypeEnum = PduType.lookup[pduType];
                ByteBuffer buf = ByteBuffer.wrap(data);

                System.out.println(pduTypeEnum);
//                System.out.println(dp.getAddress().getHostName() + ":" + dp.getPort());
                switch (pduTypeEnum) {
                    case SIGNAL:
                        SignalPdu spdu = new SignalPdu();
                        spdu.unmarshal(buf);

                        //this is identical to data pulled form WireShark, so that's good.
                        //signal data
                        System.out.println("Data Length: " + spdu.getDataLength());         // Data Length: 2560
                        System.out.println("Sample Rage: " + spdu.getSampleRate());         // 44100 [Hz]
                        System.out.println("Encoding Scheme: " + spdu.getEncodingScheme()); // 4
                        System.out.println("Num Samples: " + spdu.getSamples());            // 160
                        List<OneByteChunk> d = spdu.getData();
                        System.out.println(d.size());
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);

                        //HACK!!
                        int size = 320; //<- this is the desired value, but not sure if it should be hard coded.
                        size = spdu.getDataLength() / 8;// NOT SURE IF THIS IS A PROPER ALGORITHM
                        for (int ii = 0; ii < size; ii++) {
                            d.get(ii).marshal(dos);
                        }
                        rap.play(baos.toByteArray());

                        System.out.println("Radio ID: " + spdu.getRadioId());               // ID: 11761
                        System.out.println("Entity ID: " + spdu.getEntityId().getEntity()); // 0

                        //header data
//                    System.out.println("Padding: " + spdu.getPadding());                // 0
                        System.out.println("Proto Family: " + spdu.getProtocolFamily());    // 4
                        System.out.println("Proto Version: " + spdu.getProtocolVersion());  // 6
//                    System.out.println("PDU Length: " + spdu.getPduLength());           //352
                        System.out.println("Exercise ID: " + spdu.getExerciseID());         //0
                        System.out.println("Time Stamp: " + spdu.getTimestamp());           //

//                    byte[] b = new byte[d.size()];
//                    for(int ii = 0; ii < b.length; ii++) {
//                        System.out.println(d.get(ii).getOtherParameters()[0]);
//                        b[ii] = (d.get(ii).getOtherParameters()[0]);
//                    }
//                    rap.play(b);
                        break;
                    case TRANSMITTER:
                        TransmitterPdu tpdu = new TransmitterPdu();
                        tpdu.unmarshal(buf);
                        System.out.println("Input Source: " + tpdu.getInputSource());
                        System.out.println("Frequency: " + tpdu.getFrequency());
                        System.out.println("Transmit Frequency Bandwidth: " + tpdu.getTransmitFrequencyBandwidth());

                        System.out.println("Radio ID:" + tpdu.getRadioId());
                        System.out.println("Entity ID:" + tpdu.getEntityId().getEntity());

                        System.out.println("Protocol Family:" + tpdu.getProtocolFamily());
                        System.out.println("Protocol Version:" + tpdu.getProtocolVersion());
                        System.out.println("Exercise ID:" + tpdu.getExerciseID());
                        System.out.println("Time Stamp:" + tpdu.getTimestamp());

                        break;
                    default:
                        System.out.println("Unknown Type:" + pduTypeEnum);
                        break;
                }
            }
        }
    }
}
