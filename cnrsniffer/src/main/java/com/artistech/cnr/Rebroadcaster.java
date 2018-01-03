package com.artistech.cnr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Rebroadcast data receivied via TCP as UDP
 */
public class Rebroadcaster {
    public static final int MCAST_PORT = 3000;
    public static final String MCAST_GRP = "226.0.1.1";

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
        resetSocket(true);
    }

    public void resetSocket(boolean multicast) throws IOException {
        if(socket != null) {
            socket.close();
        }
        if(multicast) {
            group = InetAddress.getByName(MCAST_GRP);
            MulticastSocket ms = new MulticastSocket(MCAST_PORT);

            //try this to force to localhost...
//            InetAddress local = InetAddress.getByName("127.0.0.1");
//            ms.setInterface(local);

            ms.setLoopbackMode(false);
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
        resetSocket(true);
    }

    /**
     * Send a packet of data
     *
     * @param buf
     * @throws IOException
     */
    public void send(byte[] buf) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);
        socket.send(packet);
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
