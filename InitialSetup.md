# Initial XCN Setup [One Time]

- Install Ubuntu 16.04 LTS
- Grab XCN software from NSCTA SVN
  - `Emulation_Experiments\instal_scripts`
  - `Emulation_Experiments\xcn`
  - `Emulation_Experiments\cnr`
- From the `install_scripts` directory, run
    - `./setup_env_apt.sh`
    - `./install-emane.sh`
    - `./install-olsrd.sh`
    - `./install-docker.sh`

- Restart computer

# Run CNR XCN [Every Time]

- From the `Emulation_Experiments\cnr` directory
  - Find the IP address of the computer running the XCN framework
    - `ifconfig`
    - This is required so that the CNR-side clients know what IP address to connect to.
  - `./cnr.scn`
  - `./run_bridge_fcfs.sh`
    - This will require access to the Internet to clone Vbs3EmanePlugin

# Stop CNR XCN
  - `CTRL + C` to kill the bridge server
  - `./kill.sh` to terminate docker instances
