package com.artistech.cnr;

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
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");

        Options opts = new Options();
        Option opt = Option.builder("pair").required().numberOfArgs(1).desc("IP Pair.").build();
        opts.addOption(opt);
        opts.addOption("port", true, "Bridge Server port to connect to.");
        opts.addOption("log", true,"Verbose output.");
        opts.addOption("help","Help");

        BridgeDemux bd = new BridgeDemux();
        CommandLineParser parser = new DefaultParser();
        int port = TcpServer.TCP_PORT;
        try {
            CommandLine line = parser.parse(opts, args);
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("bridge-server", opts, true);
                System.exit(0);
            }

            String[] pairs = line.getOptionValues("pair");
            for(String arg : pairs) {
                String[] sp = arg.split(":");
                if(sp.length == 2) {
                    BridgePair pair = new BridgePair(sp[0], sp[1]);
                    bd.addPair(pair);
                }
            }

            if (line.hasOption("log")) {
                String val = line.getOptionValue("log");
                Level level = Level.parse(val);
                TcpClient.setLevel(level);
                LOGGER.log(level, "Logging Level: {0}", level);
            }

            if (line.hasOption("port")) {
                port = Integer.parseInt(line.getOptionValue("port"));
            }
        } catch (ParseException pe) {
        }

        if(!bd.getPairs().isEmpty()) {
            LOGGER.log(Level.FINE, "Starting Server...");
            ServerSocket ss = new ServerSocket(port);
            while (true) {
                Socket client = ss.accept();

                //check for existing match
                final String ip = client.getInetAddress().getHostAddress();
                LOGGER.log(Level.FINE, "Client Connected: {0}", ip);

                boolean found = false;
                for(BridgePair bp : bd.getPairs()) {
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
                        found = true;
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
                if (!found) {
                    LOGGER.log(Level.WARNING,"Not Configured: {0}", ip);
                    client.close();
                }
            }
        } else {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("bridge-server", opts, true);
        }
    }
}
