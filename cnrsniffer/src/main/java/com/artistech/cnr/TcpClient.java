package com.artistech.cnr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.dis.TransmitterPdu;
import org.apache.commons.cli.*;

/**
 * Listens for multi-cast data from CNR to send/stream to a listening server.
 */
public class TcpClient {
    private static final Logger LOGGER = Logger.getLogger(TcpClient.class.getName());

    public static int BUFFER_SIZE = 8192;

    /**
     * Send data from the multicast socket to the tcp socket.
     *
     * @param ms the multicast socket
     * @param socket the tcp socket
     * @throws IOException any error from read/writing socket data
     */
    public static void send(final DatagramSocket ms, Socket socket) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        LOGGER.log(Level.FINE, "TcpClient.send[socket, socket]: receiving");

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

            LOGGER.log(Level.FINEST, "PDU Type: {0}", new Object[]{pduTypeEnum});
            LOGGER.log(Level.FINEST, "Receive From: {0}:{1}", new Object[]{dp.getAddress().getHostName(), dp.getPort()});

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
    public static void send(String host, int port) throws IOException {
        LOGGER.log(Level.FINER, "waiting for server: {0}:{1}", new Object[]{host, port});

        //connect to waiting server...
        final Socket socket = new Socket(host, port);

        Thread t = new Thread(() -> {
            LOGGER.log(Level.FINE,"Starting Server Thread...");
            try {
                TcpServer.receive(socket, Rebroadcaster.INSTANCE);
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
            try {
                socket.close();
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
            try {
                Rebroadcaster.INSTANCE.resetSocket();
            } catch(IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
        });
        t.setDaemon(true);
        t.start();

        send(Rebroadcaster.INSTANCE.getSocket(), socket);
    }


    public static void setLevel(Level targetLevel) {
        Logger root = Logger.getLogger("");
        root.setLevel(targetLevel);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(targetLevel);
        }
    }

    public static Set<Level> getAllLevels() throws IllegalAccessException {
        Class<Level> levelClass = Level.class;

        Set<Level> allLevels = new TreeSet<>(
                Comparator.comparingInt(Level::intValue));

        for (Field field : levelClass.getDeclaredFields()) {
            if (field.getType() == Level.class) {
                allLevels.add((Level) field.get(null));
            }
        }
        return allLevels;
    }

    /**
     * Entry point for a client
     *
     * @param args expects 1 argument that is the server to connect to.
     */
    public static void main(String[] args) throws IllegalAccessException{
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tT %4$s [%3$s] %5$s %6$s%n");
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

        Options opts = new Options();
        Option opt = Option.builder("server").required().numberOfArgs(1).desc("Server to connect to.").build();
        opts.addOption(opt);
        opts.addOption("port", true, "Bridge Server port to connect to.");
        opts.addOption("broadcast","If broadcasting instead of multicasting.");
        opts.addOption("log", true,"Verbose output.");
        opts.addOption("help","Help");

        CommandLineParser parser = new DefaultParser();
        try {
            int port = TcpServer.TCP_PORT;
            CommandLine line = parser.parse(opts, args);
            if(line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("cnr-sniffer", opts, true);
                System.exit(0);
            }

            if(line.hasOption("log")) {
                String val = line.getOptionValue("log");
                Level level = Level.parse(val);
                setLevel(level);
                LOGGER.log(level, "Logging Level: {0}", level);
            }

            if(line.hasOption("broadcast")) {
                try {
                    Rebroadcaster.INSTANCE.resetSocket(false);
                } catch(IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            if(line.hasOption("port")) {
                port = Integer.parseInt(line.getOptionValue("port"));
            }

            if(!line.hasOption("server")) {
                System.err.println("Server value must be specified.");
            } else {
                if(args.length > 0) {
                    while(true) {
                        try {
                            send(line.getOptionValue("server"), port);
                        } catch(IOException ex) {
                            LOGGER.log(Level.FINEST, null, ex);
                        }
                    }
                }
            }
        } catch (ParseException pe) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("cnr-sniffer", opts, true);
        }
    }
}
