package com.artistech.cnr;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Sniffer {
    public static void main(String[] args) throws Exception {
        int port = 3000;
        InetAddress group = InetAddress.getByName("226.0.1.1");

        MulticastSocket ms = new MulticastSocket(port);
        ms.joinGroup(group);

        byte[] buffer = new byte[8192];
        while (true) {
            System.out.println("receiving...");
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            String s = new String(dp.getData());
            System.out.println(s);
        }
        // ms.leaveGroup(group);
        // ms.close();

    }
}
