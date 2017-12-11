package com.artistech.cnr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import edu.nps.moves.disenum.PduType;

public class TcpClient {
    public static final int MCAST_PORT = 3000;
    public static final String MCAST_GRP = "226.0.1.1";

    public static void main(String[] args) throws Exception {
        InetAddress group = InetAddress.getByName(MCAST_GRP);
        final MulticastSocket ms = new MulticastSocket(MCAST_PORT);
        //uncomment this if you want to listen on non-localhost IP
        ms.setInterface(InetAddress.getByName("127.0.0.1"));
        ms.joinGroup(group);

        byte[] buffer = new byte[8192];
        System.out.println("receiving...");
        final RawAudioPlay rap = new RawAudioPlay();
        boolean cont = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            rap.close();
            try {
                ms.leaveGroup(group);
                ms.close();
            } catch(IOException ex) {
            }
        }));

        //audio is: 16-bit Linear PCM 2's complement, Big Endian (4) <- ENCODING SCHEME 4

        Socket socket = new Socket("192.168.0.100", 6789);
        DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());
        while (cont) {
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            ms.receive(dp);
            byte[] data = dp.getData();
            int pduType = 255 & data[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer buf = ByteBuffer.wrap(data);

            System.out.println(pduTypeEnum);
            System.out.println(dp.getAddress().getHostName()+ ":" + dp.getPort());

            switch(pduTypeEnum) {
                case SIGNAL:
                    socketOutputStream.writeInt(data.length);
                    socketOutputStream.write(data);
                    socketOutputStream.flush();
                    break;
            }
        }
    }
}
