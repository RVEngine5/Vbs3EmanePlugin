package com.artistech.cnr;

import javax.sound.sampled.*;

/**
 * https://stackoverflow.com/questions/32873596/play-raw-pcm-audio-received-in-udp-packets
 */
public class RawAudioPlay {
    private AudioFormat af;
    private SourceDataLine line;

    public RawAudioPlay() throws LineUnavailableException {
        af = new AudioFormat(44100, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(af, 4096);
        line.start();
    }

    public void play(byte[] buffer) throws LineUnavailableException {
        // prepare audio output
        line.write(buffer, 0, buffer.length);
    }

    public void close() {
        // shut down audio
        line.drain();
        line.stop();
        line.close();
    }

}