package com.artistech.cnr;

/**
 * Represents a pair of IP addresses to bind.
 */
public class BridgePair {

    private String cnr;
    private String emane;

    /**
     * Default constructor.
     */
    public BridgePair() {}

    /**
     * Constructor.
     *
     * @param cnr cnr IP
     * @param emane emane IP
     */
    public BridgePair(String cnr, String emane) {
        this.cnr = cnr;
        this.emane = emane;
    }

    /**
     * Get the CNR IP
     *
     * @return cnr IP
     */
    public String getCnr() {
        return cnr;
    }

    /**
     * Set the CNR IP
     *
     * @param value cnr IP
     */
    public void setCnr(String value) {
        cnr = value;
    }

    /**
     * Get the EMANE IP
     *
     * @return emane IP
     */
    public String getEmane() {
        return emane;
    }

    /**
     * Set the EMANE IP
     *
     * @param value emane IP
     */
    public void setEmane(String value) {
        emane = value;
    }

    /**
     * Hash code
     *
     * @return combination of cnr + emane
     */
    @Override
    public int hashCode() {
        return (cnr + emane).hashCode();
    }
}
