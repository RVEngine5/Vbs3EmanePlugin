/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import com.sun.security.ntlm.Server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rebroadcast data receivied via TCP as UDP
 */
public class Rebroadcaster {

    public enum CastingEnum {
        None,
        Uni,
        Multi,
        Broad
    }

    private static final Logger LOGGER = Logger.getLogger(Rebroadcaster.class.getName());
    public static final int MCAST_PORT = 3000;
    public static final String MCAST_GRP = "226.0.1.1";

    private ServerSocket server;
    private final List<Socket> clients = new ArrayList<>();
    private final List<DataOutputStream> clientStreams = new ArrayList<>();

    private CastingEnum castType;

    private boolean multicast;
    private DatagramSocket socket;
    private InetAddress group;
    public static final Rebroadcaster INSTANCE;

    /**
     * Static Constructor
     */
    static {
        Rebroadcaster inst = null;
        try {
            inst = new Rebroadcaster();
        } catch(IOException ex) {}
        INSTANCE = inst;
    }

    /**
     * Reset the socket.  Will reset to use multicast.
     *
     * @throws IOException if error resetting.
     */
    public void resetSocket() throws IOException {
        resetSocket(multicast);
    }

    /**
     * Access if currently setup for multicast.
     *
     * @return if multicast is set.
     */
    public CastingEnum getCastType() {
        return castType;
    }

    /**
     * Close all resources
     *
     * @throws IOException error closing
     */
    private void reset() throws IOException {
        castType = CastingEnum.None;

        if(server != null) {
            server.close();
        }
        //this should fire when server closes...
        //close all open client connections.
        for(Socket client : Rebroadcaster.this.clients) {
            try {
                client.close();
            } catch (IOException ex1) {}
        }
        Rebroadcaster.this.clients.clear();
        Rebroadcaster.this.clientStreams.clear();

        if(socket != null) {
            socket.close();
        }
    }

    public void resetSocket(String[] clients) throws IOException {
        reset();

        castType = CastingEnum.Uni;
        server = new ServerSocket(MCAST_PORT);

        Thread t = new Thread(() -> {
            LOGGER.log(Level.FINER, "Starting Socket Server...");
            while(castType == CastingEnum.Uni) {
                try {
                    //create a client connection
                    Socket client = server.accept();
                    LOGGER.log(Level.FINER, "Received Connectin: {0}", client.getInetAddress().getHostAddress());

                    final DataOutputStream socketOutputStream = new DataOutputStream(client.getOutputStream());
                    clientStreams.add(socketOutputStream);
                    Rebroadcaster.this.clients.add(client);
                } catch(IOException ex)
                {
                    //this should fire when server closes...
                    //close all open client connections.
                    for(Socket client : Rebroadcaster.this.clients) {
                        try {
                            client.close();
                        } catch (IOException ex1) {}
                    }
                    Rebroadcaster.this.clients.clear();
                    Rebroadcaster.this.clientStreams.clear();
                }
            }
        });

        t.setDaemon(true);
        t.start();
    }

    /**
     * Reset the socket.
     *
     * @param multicast if true, use multicast; if false, use broadcast.
     * @throws IOException error if resetting.
     */
    public void resetSocket(boolean multicast) throws IOException {
        reset();
        castType = multicast ? CastingEnum.Multi : CastingEnum.Broad;

        if(multicast) {
            group = InetAddress.getByName(MCAST_GRP);
            MulticastSocket ms = new MulticastSocket(MCAST_PORT);

            //only listen to multicast from localhost
            //CNR should be setup to only multicast to localhost as well
            ms.setInterface(InetAddress.getLoopbackAddress());
            ms.joinGroup(group);
            socket = ms;
        } else {
            group = listAllBroadcastAddresses().get(0);
            //group = InetAddress.getByName("255.255.255.255");
            LOGGER.log(Level.INFO, "Broadcast Address: {0}", new Object[]{group.getHostAddress()});

            socket = new DatagramSocket();
            socket.setBroadcast(true);
        }
    }

    public static List<InetAddress> listAllBroadcastAddresses() throws SocketException {
        List<InetAddress> broadcastList = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces
                = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            networkInterface.getInterfaceAddresses().stream()
                    .map(a -> a.getBroadcast())
                    .filter(Objects::nonNull)
                    .forEach(broadcastList::add);
        }
        return broadcastList;
    }

    public static List<String> listAllAddresses() throws SocketException {
        List<String> broadcastList = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces
                = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            networkInterface.getInterfaceAddresses().stream()
                    .map(a -> a.getAddress().getHostAddress())
                    .filter(Objects::nonNull)
                    .forEach(broadcastList::add);
        }
        return broadcastList;
    }

    /**
     * Utilize a singleton of rebroadcaster to reduce likelyhood of feedback.
     *
     * @throws IOException
     */
    private Rebroadcaster() throws IOException{
        this.multicast = true;
        resetSocket(multicast);
    }

    /**
     * Send a packet of data
     *
     * @param buf the buffer to send.
     * @throws IOException error sending.
     */
    public void send(byte[] buf) throws IOException {
        switch(castType) {
            case Uni:
                LOGGER.log(Level.FINEST, "Unicasting to clients");
                //for each attached client, send the data to the client.
                for(DataOutputStream clientStream : clientStreams) {
                    //wrap in a try so that if one client fails, it still goes to the rest.
                    try {
                        clientStream.writeInt(buf.length);
                        clientStream.write(buf);
                        clientStream.flush();
                    } catch (IOException ex) {}
                }
                break;
            case Broad: //same logic as multi...
            case Multi:
                LOGGER.log(Level.FINEST, "Broadcasting on {0} channel", new Object[]{this.multicast ? "multicast" : "broadcast"});
                DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);
                socket.send(packet);
                LOGGER.log(Level.FINEST, "Sent on {0} channel", new Object[]{this.multicast ? "multicast" : "broadcast"});
                break;
            default:
                //not currently initialized...
                LOGGER.log(Level.WARNING, "Not currently initialized");
                break;
        }
    }

    /**
     * Get the socket.
     *
     * @return the current Datagram socket.
     */
    public DatagramSocket getSocket() {
        return socket;
    }
}
