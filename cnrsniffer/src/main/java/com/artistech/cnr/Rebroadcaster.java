package com.artistech.cnr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Rebroadcast data receivied via TCP as UDP
 */
public class Rebroadcaster {
    private MulticastSocket socket;
    private final InetAddress group;
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

    public void resetSocket(boolean localOnly) throws IOException {
        if(socket != null) {
            socket.close();
        }
        socket = new MulticastSocket(Sniffer.MCAST_PORT);
        if(!localOnly) {
            socket.setInterface(InetAddress.getByName(InetAddress.getLocalHost().getHostName()));
        }
        socket.setLoopbackMode(false);
        socket.joinGroup(group);
    }

    /**
     * Utilize a singleton of rebroadcaster to reduce likelyhood of feedback.
     *
     * @throws IOException
     */
    private Rebroadcaster() throws IOException{
        group = InetAddress.getByName(Sniffer.MCAST_GRP);
        resetSocket(true);
    }

    /**
     * Send a packet of data
     *
     * @param buf
     * @throws IOException
     */
    public void send(byte[] buf) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, Sniffer.MCAST_PORT);
        socket.send(packet);
    }

    /**
     * Get the socket
     *
     * @return
     */
    public MulticastSocket getSocket() {
        return socket;
    }
}
