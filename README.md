# Distance Vector Routing Project

Term project that compares traditional distance vector routing with binary state distance vector routing.

To run the project, run src/Main.java.

**Course:** CS 258  
**Team:** Arun Murugan, Henry Ha

## Files

- src/Main.java - Runs the simulation of single link and partitioned network failures on network topology with different number of nodes. Tracks total rounds and average messages for DVR and BSDVR and outputs them into a csv file for graphing.
- src/NodeBSDVR.java - Sets up a node for the BSDVR algorithm with a routing table. Has methods for nodes to communicate and update routing tables.
- src/NodeDVR.java - Sets up a node for the DVR algorithm with a routing table. Has methods for nodes to communicate and update routing tables.
- src/RouteEntryBSDVR,java - routing table for the BSDVR algorithm. Used by the NodeBSDVR class to hold routing tables.
- src/RouteEntryDVR,java - routing table for the DVR algorithm. Used by the NodeDVR class to hold routing tables.
- src/TopologyGenerator.java - creates a network topology given the node count and degree to simulate a real network.
- OutputGraphs.py - Loads csv files and outputs the graphs for single link and partitioned network failures.
- linkfailure.csv - holds data for DVR and BSDVR for the single link failure simulation.
- partition.csv - holds data for DVR and BSDVR for the partitioned network simulation.
