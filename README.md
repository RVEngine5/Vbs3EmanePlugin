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

If using the [WindowsCNRLauncher](https://github.com/artistech-inc/WindowsCNRLauncher), specify the IP address and press `Run`.

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
 
# Known Issues

- Uses the `wc -l` command to determine the length of the cnr.eel file which in turn is used as the number of nodes in emane to create.  If this file as additiona properties specified, a more detailed method for determining the number of nodes to create will be required.  This method is used in a couple of the launch scripts (.sh files) and should be easily modified.
- Requires Java 8.  This is a limitation if using Ubuntu < 16.04 as there is no Java 8 in the apt repositories.  Solution is to either manually install Java from Oracle or to use sdkman.
  - [Oracle Install](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)
  - [Sdkman Install](http://sdkman.io/)
```sh
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java
sudo ln -s $HOME/.sdkman/candidates/java/current/bin/java /usr/local/bin/java
sudo chmod -R 755 .sdkman
```
  - This will put Java in an accessible location for Docker.
  
# Support Scripts
- `generate_connected_eel.sh`
  - Run: `./generate_connected_eel.sh [SIZE] > cnr.eel`
  - Uses supplied argument to create an eel file with the specified number of nodes
- `install_cnr_bridge.sh`: invoked automatically to get source from github and compile
  - If it exists, it won't download and compile
  - Requres Internet connection and maven (and Java JDK 8)
- `kill.sh`: stops the emane nodes
- `emane_client.sh`: runs an emane client with the cnr-bridge and specifications
  - for now this is automatically invoked in the `run_emane_clients.sh` script.
- `run_emane_clients.sh` invokes the emane clients to run the cnr-bridge software.
  - invoked by `run_bridge_fcfs.sh`
