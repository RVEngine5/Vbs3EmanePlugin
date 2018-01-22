/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import edu.nps.moves.dis.Pdu;
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

    private static final List<Long> SENT = new ArrayList<>();
    public static final int TCP_PORT = 6789;

    public static void addSent(Pdu pdu) {
        SENT.add(pdu.getTimestamp());
    }

    public static boolean hasSent(Pdu pdu) {
        if(SENT.contains(pdu.getTimestamp())) {
            SENT.remove(pdu.getTimestamp());
            return true;
        }
        return false;
    }

    /**
     * Receive data from the socket and re-broadcast it on the local multicast channel.
     *
     * @param connectionSocket socket to receive data from the bridge
     * @throws IOException error on read or write
     */
    public static void receive(Socket connectionSocket) throws IOException {
        DataInputStream dIn = new DataInputStream(connectionSocket.getInputStream());

        while (!Rebroadcaster.INSTANCE.isHalted()) {
            // read length of incoming message
            int length = dIn.readInt();
            byte[] message = null;
            // read the message
            if (length > 0) {
                message = new byte[length];
                dIn.readFully(message, 0, message.length);
            }

            //UNICAST shouldn't have to worry about loopback issues
            //both broad-and multicast will loopback, so a broadcasted packet will be re-received
            //since this is the same code used both on CNR and XCN side of the bridge
            //we would have to add a flag to differentiate if we want to do broadcasting and
            //ignore anything from the current IP address.
            if(Rebroadcaster.INSTANCE.getCastType() != Rebroadcaster.CastingEnum.Uni) {
                //Unpack the PDU to get the timestamp
                //we don't want to flood the network with loopbacked packets
                //so save timestamps, if an identical timestamp comes through
                //block it from re-sending back through the bridge.
                //
                //TODO: I don't know if the timestamp is the best way to do this, perhaps there are better ways; this is a place for investigation.
                int pduType = 255 & message[2];
                PduType pduTypeEnum = PduType.lookup[pduType];
                ByteBuffer bb = ByteBuffer.wrap(message);

                switch (pduTypeEnum) {
                    case TRANSMITTER:
                        TransmitterPdu tpdu = new TransmitterPdu();
                        tpdu.unmarshal(bb);
                        addSent(tpdu);
                        break;
                    case SIGNAL:
                        SignalPdu spdu = new SignalPdu();
                        spdu.unmarshal(bb);
                        addSent(spdu);
                        break;
                }
            }

            //check if there was an unpacked message
            if (message != null) {
                byte[] data = message;

                try {
                    Rebroadcaster.INSTANCE.send(data);
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                }
            }
        }
    }
}
