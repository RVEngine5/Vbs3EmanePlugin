/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * https://stackoverflow.com/questions/32873596/play-raw-pcm-audio-received-in-udp-packets
 *
 * Play audio packets received to ensure data is coming through properly.
 */
public class RawAudioPlay implements AutoCloseable, WritableByteChannel {
    private AudioFormat af;
    private SourceDataLine line;

    /**
     * Constructor.
     *
     * @throws LineUnavailableException if access to audio is no available
     */
    public RawAudioPlay() throws LineUnavailableException {
        af = new AudioFormat(44100, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(af, 4096);
        line.start();
    }

    /**
     * Write a byte[] to the audio device.
     *
     * @param buffer buffer to write
     * @throws IOException error writing to the audio line
     */
    public void write(byte[] buffer) throws IOException {
        // prepare audio output
        line.write(buffer, 0, buffer.length);
    }

    /**
     * Write a ByteBuffer to the audio device.
     *
     * @param buffer the buffer to write
     * @return amount written
     * @throws IOException error writing to the audio line
     */
    @Override
    public int write(ByteBuffer buffer) throws IOException {
        byte[] arr = buffer.array();
        // prepare audio output
        return line.write(arr, 0, arr.length);
    }

    /**
     * Check if the channel is open.
     *
     * @return true if audio line is open
     */
    @Override
    public boolean isOpen() {
        return line.isOpen();
    }

    /**
     * Close the audio line and resources.
     */
    @Override
    public void close() {
        // shut down audio
        line.drain();
        line.stop();
        line.close();
    }
}