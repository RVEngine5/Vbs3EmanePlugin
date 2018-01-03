package com.artistech.cnr;

import java.util.Arrays;

/**
 * Represents a chunk of data read from the network.
 * Contains the buffer and the size of the data read from the network.
 */
public class ReadData {
    private final byte[] data;
    private final int len;

    /**
     * Constructor.
     *
     * @param data the data read
     * @param len the size of the data read
     */
    public ReadData(byte[] data, int len) {
        this.data = Arrays.copyOf(data, data.length);
        this.len = len;
    }

    /**
     * The data.
     * @return the data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * The length of the data.
     * @return the length
     */
    public int getLen() {
        return len;
    }
}
