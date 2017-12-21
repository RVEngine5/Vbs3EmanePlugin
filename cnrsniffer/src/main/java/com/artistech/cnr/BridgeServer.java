package com.artistech.cnr;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Acts as a bi-directional bridge between 2 sockets.
 */
public class BridgeServer {

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

        BridgeDemux bd = new BridgeDemux();
        for(String arg : args) {
            String[] sp = arg.split(":");
            if(sp.length == 2) {
                BridgePair pair = new BridgePair(sp[0], sp[1]);
                bd.addPair(pair);
            }
        }

        if(!bd.getPairs().isEmpty()) {
            System.out.println("Starting Server...");
            ServerSocket ss = new ServerSocket(TcpServer.TCP_PORT);
            while (true) {
                Socket client = ss.accept();

                //check for existing match
                String ip = client.getInetAddress().getHostAddress();
                System.out.println("Client Connected: " + ip);
                for(BridgePair bp : bd.getPairs()) {
                    //get paired IP
                    String pairedIp = null;
                    if(bp.getCnr().equals(ip)) {
                        pairedIp = bp.getEmane();
                    } else if(bp.getEmane().equals(ip)) {
                        pairedIp = bp.getCnr();
                    }

                    final String pairedIpFinal = pairedIp;

                    //check for existing...
                    if(pairedIp != null) {
                        if (SOCKETS.containsKey(pairedIp)) {
                            final Socket pairedSocket = SOCKETS.get(pairedIpFinal);
                            SOCKETS.remove(pairedIpFinal);

                            Thread t = new Thread(() -> {
                                System.out.println("Starting Bridge: " + ip + " to " + pairedIpFinal);
                                Bridge b = new Bridge(client, pairedSocket);
                                b.run();
                                b.halt();
                            });
                            t.setDaemon(true);
                            t.start();
                        } else {
                            //paired connection not yet present; store and wait
                            System.out.println("Waiting for paired IP: " + ip + " to " + pairedIpFinal);
                            SOCKETS.put(ip, client);
                        }
                    } else {
                        System.out.println("Not Configured: " + ip);
                        client.close();
                    }
                }
            }
        }
    }
}
