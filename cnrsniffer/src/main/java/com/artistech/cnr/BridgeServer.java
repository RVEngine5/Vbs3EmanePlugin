/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
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
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");

        int port = TcpServer.TCP_PORT;

        Options opts = new Options();
        opts.addOption(Option.builder("pair").required().numberOfArgs(1).desc("IP Pair.").build());
        opts.addOption("port", true, "Bridge Server port to connect to. [Default: " + port + "]");
        opts.addOption("log", true,"Log output level. [Default: " + TcpClient.getLevel() + "]");
        opts.addOption("help","Print this message.");

        BridgeDemux bd = new BridgeDemux();
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(opts, args);
            //print help
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("bridge-server", opts, true);
                System.exit(0);
            }

            //get all the pairs specified
            String[] pairs = line.getOptionValues("pair");
            for(String arg : pairs) {
                String[] sp = arg.split(":");
                if(sp.length == 2) {
                    BridgePair pair = new BridgePair(sp[0], sp[1]);
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
        }

        //if there are pairs (should be true as at least one '-pairs' is required by CLI)
        if(!bd.getPairs().isEmpty()) {
            LOGGER.log(Level.FINE, "Starting Server...");
            ServerSocket ss = new ServerSocket(port);

            //run forever
            while (true) {
                Socket client = ss.accept();

                //check for existing match
                String ip = client.getInetAddress().getHostAddress();
                LOGGER.log(Level.FINE, "Client Connected: {0}", ip);

                boolean found = false;
                for(BridgePair bp : bd.getPairs()) {
                    //if we already found a match in a previous bp, continue on and stop looking.
                    if(found) {
                        continue;
                    }

                    //get paired IP
                    String pairedIp = null;
                    if(bp.getLeft().equals(ip)) {
                        pairedIp = bp.getRight();
                    } else if(bp.getRight().equals(ip)) {
                        pairedIp = bp.getLeft();
                    }

                    final String pairedIpFinal = pairedIp;

                    //check for existing...
                    if(pairedIp != null) {
                        //found a match, stop all future searches.
                        found = true;

                        //check if there is already a waiting pairing
                        if (SOCKETS.containsKey(pairedIp)) {
                            //get the current waiting socket
                            final Socket pairedSocket = SOCKETS.get(pairedIpFinal);
                            SOCKETS.remove(pairedIpFinal);

                            //create a new thread to handle bridging the data.
                            Thread t = new Thread(() -> {
                                LOGGER.log(Level.FINE, "Starting Bridge: {0} to {1}", new Object[]{ip, pairedIpFinal});
                                Bridge b = new Bridge(client, pairedSocket);
                                b.run();
                                b.halt();
                            });
                            t.setDaemon(true);
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
                    LOGGER.log(Level.WARNING,"Not Configured: {0}", ip);
                    client.close();
                }
            }
        } else {
            //print help
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("bridge-server", opts, true);
        }
    }
}
