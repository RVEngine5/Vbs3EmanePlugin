package com.artistech.cnr;

import java.util.Arrays;

public class ReadData {
    private final byte[] data;
    private final int len;

    public ReadData(byte[] data, int len) {
        this.data = Arrays.copyOf(data, data.length);
        this.len = len;
    }

    public byte[] getData() {
        return data;
    }

    public int getLen() {
        return len;
    }
}
