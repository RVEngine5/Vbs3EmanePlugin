package com.artistech.cnr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.List;

import edu.nps.moves.dis.OneByteChunk;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dismobile.TransmitterPdu;
import edu.nps.moves.dis.SignalPdu;

public class Sniffer {
    public static final int MCAST_PORT = 3000;
    public static final String MCAST_GRP = "226.0.1.1";

    public static void main(String[] args) throws Exception {
        InetAddress group = InetAddress.getByName(MCAST_GRP);
        final MulticastSocket ms = new MulticastSocket(MCAST_PORT);
//        ms.setInterface(InetAddress.getByName(InetAddress.getLocalHost().getHostName()));
        ms.joinGroup(group);

        byte[] buffer = new byte[8192];
        System.out.println("receiving...");
        final RawAudioPlay rap = new RawAudioPlay();
        boolean cont = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            rap.close();
            try {
                ms.leaveGroup(group);
                ms.close();
            } catch(IOException ex) {
            }
        }));

        //audio is: 16-bit Linear PCM 2's complement, Big Endian (4) <- ENCODING SCHEME 4

        while (cont) {
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
                    for(int ii = 0; ii < size; ii++) {
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
