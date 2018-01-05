# EMANE/VBS3/CNR Integration

This provides a user space wrapper for transmitting data from CNR to CNR via EMANE.  CNR pushes PDU datagrams which can
be read, tunneled, and re-broadcast.

The architecture is CNR-side client listens to CNR.
When the CNR-side client receives a PDU, it sends it to the bridge server.
The bridge server will receive the packet on an out-facing network interface.

On each node in the XCN, an emane-side client connects to the in-facing network interface from the bridge server.
This allows the the bridge to send data into the XCN/emane simulation.

The emane-side clients then do a unicast of packets to each node.
As the other emane-side clients recieve data from within emane, they will send data back out from emane though the bridge-server to their own respective in-facing network interface to a paird CNR-side client.

At run-time, the IP addresses of the CNR-side clients must be known.

## Start XCN

`./cnr.scn`

## Start the bridge server

`./run_bridge.sh CNR_IP_1 CNR_IP_2 ... CNR_IP_N`

Each CNR_IP address will be mapped to emane node 1 .. N in the order listed on the command line.

## Start each emane-side client on each node

`./emane-client.sh X`

Where X is n1, n2, ..., nN.

The emane-client.sh file has a hard-coded BRIDGE_SERVER_IP set and so may require editing.

## Start each CNR-side client

`java -jar cnr-sniffer-1.0.jar -server BRIDGE_SERVER_IP`

# Logging

To get detailed logging, a `-log LEVEL` argument can be sent on the Java commandline.

`java -jar cnr-sniffer-1.0.jar -server BRIDGE_SERVER_IP -log FINER`

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

# Known Issues

The setup does not deal well unless unicast is used on the emane-side clients.  Broadcast sometimes work using OLSRD, but is unreliable.  Using unicast works, but if any server/client disconnects, the entire simulation must be shutdown and restarted.
