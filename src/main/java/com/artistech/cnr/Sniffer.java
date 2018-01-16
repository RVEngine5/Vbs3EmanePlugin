/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.nps.moves.dis.OneByteChunk;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dis.TransmitterPdu;
import edu.nps.moves.dis.SignalPdu;
import org.apache.commons.cli.*;

/**
 * Test class for receiving multicast data from CNR and playing the audio.
 */
public class Sniffer {

    private static final Logger LOGGER = Logger.getLogger(Sniffer.class.getName());

    /**
     * Print out data on the spdu.
     *
     * @param spdu the packet to print.
     */
    public static void printInfo(SignalPdu spdu) {
        //this is identical to data pulled form WireShark, so that's good.
        //signal data
        LOGGER.log(Level.INFO, "Data Length: {0}", spdu.getDataLength());         // Data Length: 2560
        LOGGER.log(Level.INFO, "Sample Rage: {0}", spdu.getSampleRate());         // 44100 [Hz]
        LOGGER.log(Level.INFO, "Encoding Scheme: {0}", spdu.getEncodingScheme()); // 4
        LOGGER.log(Level.INFO, "Num Samples: {0}", spdu.getSamples());            // 160

        LOGGER.log(Level.INFO, "Radio ID: {0}", spdu.getRadioId());               // ID: 11761
        LOGGER.log(Level.INFO, "Entity ID: {0}", spdu.getEntityId().getEntity()); // 0

        //header data
        LOGGER.log(Level.INFO, "Proto Family: {0}", spdu.getProtocolFamily());    // 4
        LOGGER.log(Level.INFO, "Proto Version: {0}", spdu.getProtocolVersion());  // 6
        LOGGER.log(Level.INFO, "Exercise ID: {0}", spdu.getExerciseID());         //0
        LOGGER.log(Level.INFO, "Time Stamp: {0}", spdu.getTimestamp());           //
    }

    /**
     * Print out data on the tpdu.
     *
     * @param tpdu the packet to print.
     */
    public static void printInfo(TransmitterPdu tpdu) {
        //this is identical to data pulled form WireShark, so that's good.
        //signal data
        LOGGER.log(Level.INFO, "Input Source: {0}", tpdu.getInputSource());
        LOGGER.log(Level.INFO, "Frequency: {0}", tpdu.getFrequency());
        LOGGER.log(Level.INFO, "Transmit Frequency Bandwidth: {0}", tpdu.getTransmitFrequencyBandwidth());

        LOGGER.log(Level.INFO, "Radio ID: {0}", tpdu.getRadioId());               // ID: 11761
        LOGGER.log(Level.INFO, "Entity ID: {0}", tpdu.getEntityId().getEntity()); // 0

        //header data
        LOGGER.log(Level.INFO, "Proto Family: {0}", tpdu.getProtocolFamily());    // 4
        LOGGER.log(Level.INFO, "Proto Version: {0}", tpdu.getProtocolVersion());  // 6
        LOGGER.log(Level.INFO, "Exercise ID: {0}", tpdu.getExerciseID());         //0
        LOGGER.log(Level.INFO, "Time Stamp: {0}", tpdu.getTimestamp());           //
    }

    /**
     * Get the byte data of the spdu.
     *
     * @param spdu the spdu to read.
     * @return the byte[] representing the spdu.
     */
    public static ByteArrayOutputStream getData(SignalPdu spdu) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        List<OneByteChunk> d = spdu.getData();
        LOGGER.log(Level.FINEST, "{0}", d.size());

        //HACK!!
        int size = 320; //<- this is the desired value, but not sure if it should be hard coded.
        size = spdu.getDataLength() / 8;// NOT SURE IF THIS IS A PROPER ALGORITHM
        for(int ii = 0; ii < size; ii++) {
            d.get(ii).marshal(dos);
        }
        return baos;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");

        InetAddress group = InetAddress.getByName(Rebroadcaster.MCAST_GRP);
        final MulticastSocket ms = new MulticastSocket(Rebroadcaster.MCAST_PORT);
        ms.setInterface(InetAddress.getLoopbackAddress());
//        ms.setInterface(InetAddress.getByName(InetAddress.getLocalHost().getHostName()));
        ms.joinGroup(group);

        Options opts = new Options();
        opts.addOption("log", true,"Log output level. [Default: " + TcpClient.getLevel() + "]");
        opts.addOption("help","Print this message.");

        byte[] buffer = new byte[8192];
        LOGGER.log(Level.FINE, "receiving...");
        final RawAudioPlay rap = new RawAudioPlay();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.log(Level.FINE, "closing...");
            rap.close();
            try {
                ms.leaveGroup(group);
                ms.close();
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
        }));

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(opts, args);
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("cnr-sniffer", opts, true);
                System.exit(0);
            }

            if(line.hasOption("log")) {
                String val = line.getOptionValue("log");
                Level level = Level.parse(val);
                TcpClient.setLevel(level);
                LOGGER.log(level, "Logging Level: {0}", level);
            }
        } catch(ParseException pe) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("cnr-sniffer", opts, true);
        }

        while (true) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            byte[] data = dp.getData();
            int pduType = 255 & data[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer buf = ByteBuffer.wrap(data);

            LOGGER.log(Level.FINER, "{0}", pduTypeEnum);
            LOGGER.log(Level.FINER, "{0}:{1}", new Object[]{dp.getAddress().getHostName(), dp.getPort()});

            switch(pduTypeEnum) {
                case SIGNAL:
                    SignalPdu spdu = new SignalPdu();
                    spdu.unmarshal(buf);
                    printInfo(spdu);

                    try (ByteArrayOutputStream baos = getData(spdu)) {
                        //audio is: 16-bit Linear PCM 2's complement, Big Endian (4) <- ENCODING SCHEME 4
                        rap.write(baos.toByteArray());
                    }
                    break;
                case TRANSMITTER:
                    TransmitterPdu tpdu = new TransmitterPdu();
                    tpdu.unmarshal(buf);
                    printInfo(tpdu);

                    break;
                default:
                    LOGGER.log(Level.INFO, "Unknown Type:{0}", pduTypeEnum);
                    break;
            }
        }
    }
}
