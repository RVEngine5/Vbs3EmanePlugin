package com.artistech.cnr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * Rebroadcast data receivied via TCP as UDP
 */
public class Rebroadcaster {
    private MulticastSocket socket;
    private InetAddress group;

    public Rebroadcaster() throws UnknownHostException, IOException{
        group = InetAddress.getByName(Sniffer.MCAST_GRP);
        socket = new MulticastSocket(Sniffer.MCAST_PORT);
//        socket.joinGroup(group);
    }

    public void send(byte[] buf) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, Sniffer.MCAST_PORT);
        socket.send(packet);
    }

    public void close() {
        socket.close();
    }

}
