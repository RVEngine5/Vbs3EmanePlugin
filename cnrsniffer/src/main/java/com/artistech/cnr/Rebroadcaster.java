package com.artistech.cnr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
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

    public void resetSocket() throws IOException {
        resetSocket(multicast);
    }

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
            group = InetAddress.getByName("255.255.255.255");
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        }
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

    public boolean isMulticast() {
        return multicast;
    }

    /**
     * Send a packet of data
     *
     * @param buf
     * @throws IOException
     */
    public void send(byte[] buf) throws IOException {
        LOGGER.log(Level.FINEST, "Broadcasting on {0} channel: {1}", new Object[]{this.multicast ? "multicast" : "broadcast", socket.getInetAddress()});
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);
        socket.send(packet);
        LOGGER.log(Level.FINEST, "Sent on {0} channel: {1}", new Object[]{this.multicast ? "multicast" : "broadcast", socket.getInetAddress()});
    }

    /**
     * Get the socket
     *
     * @return
     */
    public DatagramSocket getSocket() {
        return socket;
    }
}
