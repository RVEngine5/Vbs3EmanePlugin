package com.artistech.cnr;

import com.artistech.utils.HaltMonitor;
import edu.nps.moves.dis.SignalPdu;
import edu.nps.moves.disenum.PduType;
import edu.nps.moves.dismobile.TransmitterPdu;
import org.zeromq.ZMQ;

import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Listens for data from a client that is receiving PDU data.
 * Unpack data from the client and play the audio.
 */
public class TcpServer {

    public static final int TCP_PORT = 6789;

    /**
     * Listen thread.
     */
    private static class ListenThread implements Runnable {

        private ZMQ.Socket listener;

        private ListenThread(ZMQ.Socket listener) {
            this.listener = listener;
        }

        private final HaltMonitor monitor = new HaltMonitor();

        /**
         * Run.
         */
        @Override
        public void run() {
            try {
                final RawAudioPlay rap = new RawAudioPlay();
                final Rebroadcaster rebroadcaster = new Rebroadcaster();

                while (!monitor.isHalted() && listener != null) {
                    byte[] message = listener.recv(0);
                    if (message != null) {
                        try {
                            byte[] data = message;
                            int pduType = 255 & data[2];
                            PduType pduTypeEnum = PduType.lookup[pduType];
                            ByteBuffer buf = ByteBuffer.wrap(data);

                            try {
                                rebroadcaster.send(data);
                            } catch (IOException ex) {
                                ex.printStackTrace(System.out);
                            }

                            //examine packet data and play audio
                            System.out.println(pduTypeEnum);

                            switch (pduTypeEnum) {
                                case SIGNAL:
                                    SignalPdu spdu = new SignalPdu();
                                    spdu.unmarshal(buf);

                                    Sniffer.printInfo(spdu);
                                    try (ByteArrayOutputStream baos = Sniffer.getData(spdu)) {
                                        //audio is: 16-bit Linear PCM 2's complement, Big Endian (4) <- ENCODING SCHEME 4
                                        rap.play(baos.toByteArray());
                                    }
                                    break;
                                case TRANSMITTER:
                                    TransmitterPdu tpdu = new TransmitterPdu();
                                    tpdu.unmarshal(buf);
                                    Sniffer.printInfo(tpdu);

                                    break;
                                default:
                                    System.out.println("Unknown Type:" + pduTypeEnum);
                                    break;
                            }
                        } catch (IOException ex) {
                        }
                    }
                    rap.close();
                    rebroadcaster.close();
                }
            } catch(LineUnavailableException | IOException ex){
            }
        }

        /**
         * Halt the listen thread.
         */
        public void halt() {
            monitor.halt();
        }
    }

    public static void main(String argv[]) throws Exception {
        ZMQ.Context context;
        ZMQ.Socket listener;

        context = ZMQ.context(1);
        listener = context.socket(ZMQ.SUB);
        listener.connect("tcp://" + argv[0] + ":5566");
        listener.subscribe("".getBytes());

        final ListenThread lt = new ListenThread(listener);
        Thread t = new Thread(lt);
        t.setDaemon(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("closing...");
            lt.halt();
        }));

        System.out.println("waiting...");

        while(!t.isAlive()) {
        }

    }
}
