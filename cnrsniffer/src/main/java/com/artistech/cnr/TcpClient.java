/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.dis.TransmitterPdu;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;

/**
 * Listens for multi-cast data from CNR to send/stream to a listening server.
 */
public class TcpClient {
    private static final Logger LOGGER = Logger.getLogger(TcpClient.class.getName());

    public static int BUFFER_SIZE = 8192;

    /**
     * Forward data from the multicast socket to the tcp socket.
     *
     * @param ms the multicast socket
     * @param socket the tcp socket
     * @throws IOException any error from read/writing socket data
     */
    private static void forward(final DatagramSocket ms, Socket socket) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        LOGGER.log(Level.FINE, "Starting fowarding service...");

        DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
        while (true) {
            //if the bridge socket is closed, then return.
            if(socket.isClosed()) {
                return;
            }

            //receive data from the datagram socket.
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            byte[] data = dp.getData();

            int pduType = 255 & data[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer bb = ByteBuffer.wrap(data);

            //log debug data
            LOGGER.log(Level.FINEST, "PDU Type: {0}", new Object[]{pduTypeEnum});
            LOGGER.log(Level.FINEST, "Receive From: {0}:{1}", new Object[]{dp.getAddress().getHostName(), dp.getPort()});

            //we must deserialie the PDU to get the timestamp.
            //this is so that we don't end up with a feedback loop.
            //if we can come up with a better solution to this, that would be great.
            boolean send = true;

            //deserialize and get the timestamp.
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

            //if we are safe to send, forward the packet to the bridge server.
            if(send) {
                LOGGER.log(Level.FINEST, "Writing to socket...");
                socketOutputStream.writeInt(data.length);
                socketOutputStream.write(data);
                socketOutputStream.flush();
            } else {
                LOGGER.log(Level.FINEST, "Found Sent Packet");
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
    private static Socket connect(String host, int port) throws IOException {
        LOGGER.log(Level.FINE, "waiting for server: {0}:{1}", new Object[]{host, port});

        //connect to waiting server...
        final Socket socket = new Socket(host, port);
        LOGGER.log(Level.FINE, "connected to {0}:{1}", new Object[]{host, port});

        Thread t = new Thread(() -> {
            LOGGER.log(Level.FINEST,"Starting Server Thread...");
            try {
                //blocking call that will receive data until error.
                //data is received from the bridge server.
                TcpServer.receive(socket, Rebroadcaster.INSTANCE);
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }

            //once the blocking call exits, close down the socket.
            try {
                socket.close();
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }

            //reset the socket (just in case).
            try {
                Rebroadcaster.INSTANCE.resetSocket();
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
        });

        //start receiving data from bridge server.
        t.setDaemon(true);
        t.start();

        return socket;
    }

    /**
     * Set the logging level
     *
     * @param targetLevel logging level
     */
    public static void setLevel(Level targetLevel) {
        Logger root = Logger.getLogger("");
        root.setLevel(targetLevel);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(targetLevel);
        }
    }

    /**
     * Get the logging level
     *
     * @return logging level
     */
    public static Level getLevel() {
        return Logger.getLogger("").getLevel();
    }

    /**
     * Entry point for a client
     *
     * @param args expects 1 argument that is the server to connect to.
     */
    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");

//        setLevel(Level.ALL);
//        LOGGER.log(Level.ALL, "hello");
//        LOGGER.log(Level.FINEST, "hello");
//        LOGGER.log(Level.FINER, "hello");
//        LOGGER.log(Level.FINE, "hello");
//        LOGGER.log(Level.CONFIG, "hello");
//        LOGGER.log(Level.INFO, "hello");
//        LOGGER.log(Level.WARNING, "hello");
//        LOGGER.log(Level.SEVERE, "hello");
//        LOGGER.log(Level.OFF, "hello");

        int port = TcpServer.TCP_PORT;

        Options opts = new Options();
        opts.addOption(Option.builder("server").required().numberOfArgs(1).desc("Server to connect to.").build());
        opts.addOption("port", true, "Bridge Server port to connect to. [Default: " + port + "]");
        opts.addOption("broadcast","If broadcasting instead of multicasting.");
        opts.addOption("log", true,"Log output level. [Default: " + getLevel() + "]");
        opts.addOption("help","Print this message.");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(opts, args);
            //print help
            if(line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("cnr-client", opts, true);
                System.exit(0);
            }

            //set the logging level
            if(line.hasOption("log")) {
                String val = line.getOptionValue("log");
                Level level = Level.parse(val);
                setLevel(level);
                LOGGER.log(level, "Logging Level: {0}", level);
            }

            //set if app should use broadcast instead of the default multicast
            if(line.hasOption("broadcast")) {
                try {
                    Rebroadcaster.INSTANCE.resetSocket(false);
                } catch(IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            //set the non-default port value
            if(line.hasOption("port")) {
                port = Integer.parseInt(line.getOptionValue("port"));
            }

            //read the server to connect to for pairing.
            //this should always be present as it is required by the CLI.
            while(true) {
                //connect to the bridge server and return the socket.
                //also sets up a thread for receiving data from the server.
                try {
                    Socket socket = connect(line.getOptionValue("server"), port);
                    //blocking call to forward data from the datagram socket to the bridge server.
                    forward(Rebroadcaster.INSTANCE.getSocket(), socket);
                } catch(IOException ex) {
                    LOGGER.log(Level.FINEST, null, ex);
                }
            }
        } catch (ParseException pe) {
            //print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("cnr-client", opts, true);
        }
    }
}
