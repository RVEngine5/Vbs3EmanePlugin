# EMANE/VBS3/CNR Integration

This provides a user space wrapper for transmitting data from CNR to CNR via EMANE.  CNR pushes PDU datagrams which can
be read, tunneled, and re-broadcast.

The architecture is CNR-side client listens to CNR.
When the CNR-side client receives a PDU, it sends it to the bridge server.
The bridge server will receive the packet on an out-facing network interface.

On each node in the XCN, an EMANE-side client connects to the in-facing network interface from the bridge server.
This allows the the bridge to send data into the XCN/EMANE simulation.

The EMANE-side clients then do a unicast of packets to each node.
As the other EMANE-side clients recieve data from within EMANE, they will send data back out from EMANE though the bridge-server to their own respective in-facing network interface to a paird CNR-side client.

At run-time, the IP addresses of the CNR-side clients must be known.

# Start XCN

`./cnr.scn`

## Run XCN Framework for CNR

`./run_bridge_fcfs.sh`

This will start the XCN nodes as described in the cnr.eel file, tell each node to run the EMANE-side client for communications, and then start the bridge server.  The bridge server runs as a first-come, first-serve so each CNR-side clien that connects will be 'pop' off an IP address of the EMANE-side to attache to.

## Start each CNR-side client

`java -jar cnr-bridge-1.0.jar -server BRIDGE_SERVER_IP`

# Ending Simulation
 
`CTRL + C` to end the `.\run_bridge_fcfs.sh` script and then run `.\kill.sh` to kill all XCN docker instances.

# Logging

To get detailed logging, a `-log LEVEL` argument can be sent on the Java commandline.

`java -jar cnr-bridge-1.0.jar -server BRIDGE_SERVER_IP -log FINER`

Levels are:
 - ALL
 - FINEST
 - FINER
 - FINE
 - CONFIG
 - INFO
 - WARNING
 - SEVERE
 - OFF
 
