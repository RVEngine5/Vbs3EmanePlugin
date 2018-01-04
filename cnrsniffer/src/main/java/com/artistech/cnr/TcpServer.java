/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dis.TransmitterPdu;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens for data from a client that is receiving PDU data.
 * Unpack data from the client and play the audio.
 */
public class TcpServer {

    private static final Logger LOGGER = Logger.getLogger(TcpServer.class.getName());

    public static final List<Long> SENT = new ArrayList<>();
    public static final int TCP_PORT = 6789;

    /**
     * Receive data from the socket and re-broadcast it on the local multicast channel.
     *
     * @param connectionSocket socket to receive data from the bridge
     * @param rebroadcaster Datagram wrapper for rebroadcasting the packet
     * @throws IOException error on read or write
     */
    public static void receive(Socket connectionSocket, Rebroadcaster rebroadcaster) throws IOException {
        while (true) {
            DataInputStream dIn = new DataInputStream(connectionSocket.getInputStream());

            // read length of incoming message
            int length = dIn.readInt();
            byte[] message = null;
            // read the message
            if (length > 0) {
                message = new byte[length];
                dIn.readFully(message, 0, message.length);
            }

            //Unpack the PDU to get the timestamp
            //we don't want to flood the network with loopbacked packets
            //so save timestamps, if an identical timestamp comes through
            //block it from re-sending back through the bridge.
            int pduType = 255 & message[2];
            PduType pduTypeEnum = PduType.lookup[pduType];
            ByteBuffer bb = ByteBuffer.wrap(message);

            switch (pduTypeEnum) {
                case TRANSMITTER:
                    TransmitterPdu tpdu = new TransmitterPdu();
                    tpdu.unmarshal(bb);
                    SENT.add(tpdu.getTimestamp());
                    break;
                case SIGNAL:
                    SignalPdu spdu = new SignalPdu();
                    spdu.unmarshal(bb);
                    SENT.add(spdu.getTimestamp());
                    break;
            }

            //check if there was an unpacked message
            if (message != null) {
                byte[] data = message;

                try {
                    //push the data from the bridge over the datagram socket
                    Logger.getLogger(TcpServer.class.getName()).log(Level.FINEST, "Rebroadcasting on {0} channel", rebroadcaster.isMulticast() ? "multicast" : "broadcast ");
                    rebroadcaster.send(data);
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                }
            }
        }
    }
}
