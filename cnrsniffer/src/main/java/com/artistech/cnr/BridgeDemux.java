package com.artistech.cnr;

import java.util.HashSet;
import java.util.Set;

/**
 * Maintains collection of bound IP addresses.
 */
public class BridgeDemux {

    /**
     * IP addresses kept in a set to remove duplicates
     */
    private final Set<BridgePair> pairs = new HashSet<>();

    /**
     * Public constructor.
     */
    public BridgeDemux() {}

    /**
     * Get the pairs.
     *
     * @return all pairs
     */
    public Set<BridgePair> getPairs() {
        return pairs;
    }

    /**
     * Set the pairs.
     *
     * @param value all pairs
     */
    public void setPairs(Set<BridgePair> value) {
        pairs.clear();
        pairs.addAll(value);
    }

    /**
     * Add a pair.
     *
     * @param pair pair to add
     */
    public void addPair(BridgePair pair) {
        pairs.add(pair);
    }
}
