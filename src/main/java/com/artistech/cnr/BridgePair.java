/*
 * Copyright 2017-18, ArtisTech, Inc.
 */
package com.artistech.cnr;

import org.apache.commons.lang3.tuple.MutablePair;

/**
 * Represents a pair of IP addresses to bind.
 */
public class BridgePair extends MutablePair<String, String> {

    /**
     * Constructor.
     *
     * @param ip1 cnr IP
     * @param ip2 emane IP
     */
    public BridgePair(String ip1, String ip2) {
        super(ip1, ip2);
    }

    /**
     * Hash code
     *
     * @return combination of cnr + emane
     */
    @Override
    public int hashCode() {
        return (super.left + super.right).hashCode();
    }
}
