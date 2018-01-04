/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

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

    private static final Logger LOGGER = Logger.getLogger(Rebroadcaster.class.getName());
    public static final int MCAST_PORT = 3000;
    public static final String MCAST_GRP = "226.0.1.1";

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
    public boolean isMulticast() {
        return multicast;
    }

    /**
     * Reset the socket.
     *
     * @param multicast if true, use multicast; if false, use broadcast.
     * @throws IOException error if resetting.
     */
    public void resetSocket(boolean multicast) throws IOException {
        this.multicast = multicast;
        if(socket != null) {
            socket.close();
        }
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

    private List<InetAddress> listAllBroadcastAddresses() throws SocketException {
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
        LOGGER.log(Level.FINEST, "Broadcasting on {0} channel", new Object[]{this.multicast ? "multicast" : "broadcast"});
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);
        socket.send(packet);
        LOGGER.log(Level.FINEST, "Sent on {0} channel", new Object[]{this.multicast ? "multicast" : "broadcast"});
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
