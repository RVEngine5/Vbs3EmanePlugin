/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;

/**
 * Acts as a bi-directional bridge between 2 sockets.
 */
public class BridgeServer {

    private static final Logger LOGGER = Logger.getLogger(BridgeServer.class.getName());
    private static final Map<String, Socket> SOCKETS;

    /**
     * Static Constructor.
     */
    static {
        SOCKETS = new HashMap<>();
    }

    /**
     * Expects an argument of an IP address pair to bind.
     *
     * Starts a server, once a client connects to the server attempts to connect to the specified host.
     * Once the specified host is connected, the bridge thread is started.
     *
     * Allows for multiple bridges to be made to the same host port.  This will allow one instance to run where
     * EMANE is and allow routing to XCN IP addresses.
     *
     * The app will handle broken connections by resetting and starting again.
     *
     * @param args command line args
     * @throws IOException error creating/reading sockets
     */
    public static void main(String[] args) throws IOException {
        final List<Bridge> bridges = new ArrayList<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.log(Level.INFO, "Cleaning up for shutdown");
            Rebroadcaster.INSTANCE.halt();
            for(Bridge b : bridges) {
                b.halt();
            }
        }));

        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");

        int port = TcpServer.TCP_PORT;

        Options opts = new Options();
        opts.addOption(Option.builder("pair").numberOfArgs(1).desc("IP Pair.").build());
        opts.addOption(Option.builder("xcn").numberOfArgs(1).desc("XCN IP.").build());
        opts.addOption("port", true, "Bridge Server port to connect to. [Default: " + port + "]");
        opts.addOption("log", true,"Log output level. [Default: " + TcpClient.getLevel() + "]");
        opts.addOption("help","Print this message.");

        BridgeDemux bd = new BridgeDemux();
        CommandLineParser parser = new DefaultParser();
        AtomicBoolean paired = new AtomicBoolean(false);
        AtomicBoolean xcns = new AtomicBoolean(false);
        try {
            CommandLine line = parser.parse(opts, args);
            //print help
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("bridge-server", opts, true);
                System.exit(0);
            }

            //get all the pairs specified
            if(line.hasOption("pair")) {
                paired.set(true);
                String[] pairs = line.getOptionValues("pair");
                for (String arg : pairs) {
                    String[] sp = arg.split(":");
                    if (sp.length == 2) {
                        BridgePair pair = new BridgePair(sp[0], sp[1]);
                        bd.addPair(pair);
                    }
                }
            }

            //get all the xcn IPs specified
            if(line.hasOption("xcn")) {
                xcns.set(true);
                String[] pairs = line.getOptionValues("xcn");
                for (String arg : pairs) {
                    //set the 'left' value to be EMPTY
                    BridgePair pair = new BridgePair("", arg);
                    bd.addPair(pair);
                }
            }

            //set the logging level
            if (line.hasOption("log")) {
                String val = line.getOptionValue("log");
                Level level = Level.parse(val);
                TcpClient.setLevel(level);
                LOGGER.log(level, "Logging Level: {0}", level);
            }

            //set the non-default port value
            if (line.hasOption("port")) {
                port = Integer.parseInt(line.getOptionValue("port"));
            }
        } catch (ParseException pe) {
            System.out.println(pe.getMessage());
            //print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("bridge-server", opts, true);
        }

        //Only XOR of paired and xcns is valid.
        if(paired.get() && xcns.get()) {
            //print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("bridge-server", opts, true);
        }
        if (!paired.get() && !xcns.get()) {
            //print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("bridge-server", opts, true);
        } else if(paired.get() && !bd.getPairs().isEmpty()) {
            PairedServer(bd, bridges, port);
        } else if(xcns.get() && !bd.getPairs().isEmpty()) {
            //first-come-first-serve
            NonPairedServer(bd, bridges, port);
        } else {
            //print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("bridge-server", opts, true);
        }
    }

    /**
     * Configured to pair on first-come, first-serve basis.
     *
     * @param bd BridgeDemux
     * @param bridges List of pairs
     * @param port port to listen on
     * @throws IOException error on server
     */
    public static void NonPairedServer(BridgeDemux bd, List<Bridge> bridges, int port) throws IOException {
        //if there are pairs (should be true as at least one '-pairs' is required by CLI)
        LOGGER.log(Level.FINE, "Starting Server...");
        ServerSocket ss = new ServerSocket(port);

        //run forever
        while (!Rebroadcaster.INSTANCE.isHalted()) {
            Socket client = ss.accept();

            //check for existing match
            final String ip = client.getInetAddress().getHostAddress();
            LOGGER.log(Level.FINE, "Client Connected: {0}", ip);

            String pairedIp = null;
            BridgePair pair = null;
            for (BridgePair bp : bd.getPairs()) {
                //get paired IP
                if (bp.getLeft().equals(ip)) {
                    pairedIp = bp.getRight();
                    pair = bp;
                } else if (bp.getRight().equals(ip)) {
                    pairedIp = bp.getLeft();
                    pair = bp;
                }
            }

            if (pairedIp == null) {
                //will be null if the IP address is previously unknown
                //now that we know the IP address, set it to the left value
                //of the first available bridge pair.
                //once this value is set, it will always be assigned to this node
                for (BridgePair bp : bd.getPairs()) {
                    //check for an available EMPTY left slot
                    if (pairedIp == null && "".equals(bp.getLeft())) {
                        //update the paired ip to be the xcn ip address

                        bp.setLeft(ip);
                        pairedIp = bp.getRight();
                        pair = bp;

                        SOCKETS.put(bp.getLeft(), client);
                    }
                }
            }

            //if pairedIP is still null, then there are no more available slots, ignore
            if (pairedIp != null) {
                //check if there is already a waiting pairing
                if (SOCKETS.containsKey(pairedIp)) {
                    SOCKETS.put(ip, client);

                    final Socket sockLeft = SOCKETS.get(pair.left);
                    final Socket sockRight = SOCKETS.get(pair.right);
                    SOCKETS.remove(pair.left);
                    SOCKETS.remove(pair.right);

                    Thread t = new Thread(() -> {
                        Bridge b = new Bridge(sockLeft, sockRight);
                        b.run();
                        b.halt();
                        bridges.add(b);
                    });
                    t.setDaemon(true);
                    LOGGER.log(Level.FINE, "Starting Bridge: {0} to {1}", new Object[]{ip, pairedIp});
                    t.start();
                } else {
                    //paired connection not yet present; store and wait
                    LOGGER.log(Level.FINE, "Waiting for paired IP: {0} to {1}", new Object[]{ip, pairedIp});
                    SOCKETS.put(ip, client);
                }
            }
        }
    }

    /**
     * Configured to pair specific IP addresses from CNR to XCN
     *
     * @param bd BridgeDemux
     * @param bridges List of pairs
     * @param port port to listen on
     * @throws IOException error on server
     */
    public static void PairedServer(BridgeDemux bd, List<Bridge> bridges, int port) throws IOException {
        //if there are pairs (should be true as at least one '-pairs' is required by CLI)
        LOGGER.log(Level.FINE, "Starting Server...");
        ServerSocket ss = new ServerSocket(port);

        //run forever
        while (!Rebroadcaster.INSTANCE.isHalted()) {
            Socket client = ss.accept();

            //check for existing match
            final String ip = client.getInetAddress().getHostAddress();
            LOGGER.log(Level.FINE, "Client Connected: {0}", ip);

            boolean found = false;
            for (BridgePair bp : bd.getPairs()) {
                //if we already found a match in a previous bp, continue on and stop looking.
                if (found) {
                    continue;
                }

                //get paired IP
                String pairedIp = null;
                if (bp.getLeft().equals(ip)) {
                    pairedIp = bp.getRight();
                } else if (bp.getRight().equals(ip)) {
                    pairedIp = bp.getLeft();
                }

                final String pairedIpFinal = pairedIp;

                //check for existing...
                if (pairedIp != null) {
                    //found a match, stop all future searches.
                    found = true;

                    //check if there is already a waiting pairing
                    if (SOCKETS.containsKey(pairedIp)) {
                        SOCKETS.put(ip, client);

                        final Socket sockLeft = SOCKETS.get(bp.left);
                        final Socket sockRight = SOCKETS.get(bp.right);
                        SOCKETS.remove(bp.left);
                        SOCKETS.remove(bp.right);

                        Thread t = new Thread(() -> {
                            Bridge b = new Bridge(sockLeft, sockRight);
                            b.run();
                            b.halt();
                            bridges.add(b);
                        });
                        t.setDaemon(true);
                        LOGGER.log(Level.FINE, "Starting Bridge: {0} to {1}", new Object[]{ip, pairedIpFinal});
                        t.start();
                    } else {
                        //paired connection not yet present; store and wait
                        LOGGER.log(Level.FINE, "Waiting for paired IP: {0} to {1}", new Object[]{ip, pairedIpFinal});
                        SOCKETS.put(ip, client);
                    }
                }
            }

            //if no ip is found, then the current instance is not configured to look for pairs with the IP.
            if (!found) {
                LOGGER.log(Level.WARNING, "Not Configured: {0}", ip);
                client.close();
            }
        }
    }
}
