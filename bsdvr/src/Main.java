import java.io.*;
import java.util.*;

public class Main {

    // ── configuration ────────────────────────────────────────────────────────
    static final int   MAX_ROUNDS      = 1000;   // cap to terminate CTI loops
    static final int[] NODE_COUNTS     = {5, 10, 15, 20, 25, 30};
    static final int   TRIALS          = 3;     // random topologies per size
    static final int   TARGET_AVG_DEG  = 4;     // 3–5 per the paper
    static final long  BASE_SEED       = 20220101L;

    // byte-cost model
    static final int HDR_BYTES          = 8;  // packet header (src + seq-no)
    static final int TDVR_BYTES_ENTRY   = 8;  // {dest(4) + cost(4)}
    static final int BSDVR_BYTES_ENTRY  = 9;  // {dest(4) + cost(4) + state(1)}

    public static void main(String[] args) throws IOException{
        singleLinkFailure();
        partitionFailure();
    }

    static void singleLinkFailure() throws IOException{
        System.out.println("Single Link Failures, no partitions");

        System.out.printf("  %-6s  %20s  %20s  %20s  %20s%n",
                "Nodes", "TDVR avg rounds", "TDVR avg msgs", "BSDVR avg rounds", "BSDVR avg msgs");

        ArrayList<Long> tdvrMsgs = new ArrayList<>();
        ArrayList<Long> bsdvrMsgs = new ArrayList<>();
        
        for (int n : NODE_COUNTS) {
            long tdvrRoundsSum = 0, tdvrMsgsSum = 0;
            long bsdvrRoundsSum = 0, bsdvrMsgsSum = 0;
            int samples = 0;

            for (int trial = 0; trial < TRIALS; trial++) {
                Random rng = new Random(BASE_SEED + (long) n * 1000 + trial);

                Map<String, Map<String, Integer>> topo =
                        TopologyGenerator.generate(n, TARGET_AVG_DEG, 2, rng);
                List<String[]> edges = TopologyGenerator.getEdges(topo);

                for (String[] edge : edges) {
                    String eA = edge[0], eB = edge[1];

                    //TDVR
                    Map<String, NodeDVR> tdvrNet = buildTDVR(topo);
                    convergeTDVR(tdvrNet, topo);
                    tdvrNet.get(eA).removeNeighbor(eB);
                    tdvrNet.get(eB).removeNeighbor(eA);
                    long[] t = measureTDVR(tdvrNet, topo);
                    tdvrRoundsSum += t[0];
                    tdvrMsgsSum   += t[1];

                    ///BSDVR, fail 1 link
                    Map<String, NodeBSDVR> bsdvrNet = buildBSDVR(topo);
                    convergeBSDVR(bsdvrNet, topo);
                    bsdvrNet.get(eA).failLink(eB);
                    bsdvrNet.get(eB).failLink(eA);
                    long[] b = measureBSDVR(bsdvrNet, topo);
                    bsdvrRoundsSum += b[0];
                    bsdvrMsgsSum   += b[1];

                    samples++;
                }
            }

            System.out.printf("  %-6d  %20.2f  %20.1f  %20.2f  %20.1f%n",
                    n,
                    (double) tdvrRoundsSum  / samples,
                    (double) tdvrMsgsSum    / samples,
                    (double) bsdvrRoundsSum / samples,
                    (double) bsdvrMsgsSum   / samples);
            
            tdvrMsgs.add(tdvrMsgsSum);
            bsdvrMsgs.add(bsdvrMsgsSum);
        }

        FileWriter w = new FileWriter("linkfailure.csv");
        w.write("num_nodes dvr_avg_msgs bsdvr_avg_msgs\n");

        for (int i = 0; i < tdvrMsgs.size(); i++) {
            w.write(String.valueOf(NODE_COUNTS[i]) + " " + String.valueOf(tdvrMsgs.get(i)) + " " + String.valueOf(bsdvrMsgs.get(i) + "\n"));
        }

        w.close();
    }

    static void partitionFailure() throws IOException {
        System.out.println("Partitioned network");

        System.out.printf("  %-6s  %26s  %20s  %20s  %20s%n",
                "Nodes", "TDVR avg rounds", "TDVR avg msgs", "BSDVR avg rounds", "BSDVR avg msgs");

        ArrayList<Long> tdvrMsgs = new ArrayList<>();
        ArrayList<Long> bsdvrMsgs = new ArrayList<>();

        for (int n : NODE_COUNTS) {
            long tdvrRoundsSum = 0, tdvrMsgsSum = 0;
            long bsdvrRoundsSum = 0, bsdvrMsgsSum = 0;
            int samples = 0;

            for (int trial = 0; trial < TRIALS; trial++) {
                Random rng = new Random(BASE_SEED + (long) n * 1000 + trial + 99999L);

                Map<String, Map<String, Integer>> topo =
                        TopologyGenerator.generate(n, TARGET_AVG_DEG, 1, rng);
                List<String> nodes = TopologyGenerator.getNodes(topo);

                for (String failNode : nodes) {
                    Set<String> nbsOfFailed = new HashSet<>(topo.get(failNode).keySet());

                    //TDVR
                    Map<String, NodeDVR> tdvrNet = buildTDVR(topo);
                    convergeTDVR(tdvrNet, topo);
                    for (String nb : nbsOfFailed) tdvrNet.get(nb).removeNeighbor(failNode);
                    tdvrNet.remove(failNode);       // physically remove from simulation
                    long[] t = measureTDVRPartition(tdvrNet, topo, failNode);
                    tdvrRoundsSum += t[0];
                    tdvrMsgsSum   += t[1];

                    //BSDVR
                    Map<String, NodeBSDVR> bsdvrNet = buildBSDVR(topo);
                    convergeBSDVR(bsdvrNet, topo);
                    for (String nb : nbsOfFailed) bsdvrNet.get(nb).failLink(failNode);
                    bsdvrNet.remove(failNode);
                    long[] b = measureBSDVRPartition(bsdvrNet, topo, failNode);
                    bsdvrRoundsSum += b[0];
                    bsdvrMsgsSum   += b[1];

                    samples++;
                }
            }

            boolean cti = (tdvrRoundsSum / samples) >= MAX_ROUNDS - 5;
            String tdvrRoundsStr;
            if (cti) {
                tdvrRoundsStr = String.format("Hit max rounds", MAX_ROUNDS);
            } else {
                tdvrRoundsStr = String.format("%.2f", (double) tdvrRoundsSum / samples);
            }

            System.out.printf("  %-6d  %26s  %20.1f  %20.2f  %20.1f%n",
                    n,
                    tdvrRoundsStr,
                    (double) tdvrMsgsSum    / samples,
                    (double) bsdvrRoundsSum / samples,
                    (double) bsdvrMsgsSum   / samples);
            
            tdvrMsgs.add(tdvrMsgsSum);
            bsdvrMsgs.add(bsdvrMsgsSum);
        }

        FileWriter w = new FileWriter("partition.csv");
        w.write("num_nodes dvr_avg_msgs bsdvr_avg_msgs\n");

        for (int i = 0; i < tdvrMsgs.size(); i++) {
            w.write(String.valueOf(NODE_COUNTS[i]) + " " + String.valueOf(tdvrMsgs.get(i)) + " " + String.valueOf(bsdvrMsgs.get(i) + "\n"));
        }

        w.close();
    }

    static Map<String, NodeDVR> buildTDVR(Map<String, Map<String, Integer>> topo) {
        Map<String, NodeDVR> net = new LinkedHashMap<>();
        for (String node : topo.keySet()) net.put(node, new NodeDVR(node));
        for (String node : topo.keySet())
            for (Map.Entry<String, Integer> e : topo.get(node).entrySet())
                net.get(node).addNeighbor(e.getKey(), e.getValue());
        return net;
    }

    static Map<String, NodeBSDVR> buildBSDVR(Map<String, Map<String, Integer>> topo) {
        Map<String, NodeBSDVR> net = new LinkedHashMap<>();
        for (String node : topo.keySet()) net.put(node, new NodeBSDVR(node));
        for (String node : topo.keySet())
            for (Map.Entry<String, Integer> e : topo.get(node).entrySet())
                net.get(node).addNeighbor(e.getKey(), e.getValue());
        return net;
    }

    static void convergeTDVR(Map<String, NodeDVR> net, Map<String, Map<String, Integer>> topo) {
        boolean changed = true;
        int round = 0;
        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Map<String, Integer>> snap = snapshotTDVR(net);
            changed = false;
            for (String node : net.keySet())
                for (String nb : topo.get(node).keySet())
                    if (net.containsKey(nb) && net.get(node).isNeighbor(nb))
                        if (net.get(node).updateFromNeighbor(nb, snap.get(nb)))
                            changed = true;
        }
    }

    static void convergeBSDVR(Map<String, NodeBSDVR> net, Map<String, Map<String, Integer>> topo) {
        boolean changed = true;
        int round = 0;
        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Map<String, int[]>> snap = snapshotBSDVR(net, topo);
            changed = false;
            for (String node : net.keySet())
                for (String nb : topo.get(node).keySet())
                    if (net.containsKey(nb) && net.get(node).isNeighbor(nb)) {
                        Map<String, int[]> s = snap.get(nb + "->" + node);
                        if (s != null && net.get(node).updateFromNeighbor(nb, s))
                            changed = true;
                    }
        }
    }

    /**
     * Runs TDVR after a single-link failure 
     **/
    static long[] measureTDVR(Map<String, NodeDVR> net, Map<String, Map<String, Integer>> topo) {
        long msgs = 0;
        boolean changed = true;
        int round = 0;

        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Map<String, Integer>> snap = snapshotTDVR(net);
            changed = false;
            for (String node : net.keySet()) {
                for (String nb : topo.get(node).keySet()) {
                    if (net.containsKey(nb) && net.get(node).isNeighbor(nb)) {
                        Map<String, Integer> s = snap.get(nb);
                        if (s != null) {
                            msgs++; // one DV advertisement
                            if (net.get(node).updateFromNeighbor(nb, s)) changed = true;
                        }
                    }
                }
            }
        }
        return new long[]{round, msgs};
    }

    /**
     * Runs BSDVR after a single-link failure
     */
    static long[] measureBSDVR(Map<String, NodeBSDVR> net, Map<String, Map<String, Integer>> topo) {
        long msgs = 0;
        boolean changed = true;
        int round = 0;

        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Map<String, int[]>> snap = snapshotBSDVR(net, topo);
            changed = false;
            for (String node : net.keySet()) {
                for (String nb : topo.get(node).keySet()) {
                    if (net.containsKey(nb) && net.get(node).isNeighbor(nb)) {
                        Map<String, int[]> s = snap.get(nb + "->" + node);
                        if (s != null) {
                            msgs++;
                            if (net.get(node).updateFromNeighbor(nb, s)) changed = true;
                        }
                    }
                }
            }
        }
        return new long[]{round, msgs};
    }

    /**
     * TDVR measurement after partition
     */
    static long[] measureTDVRPartition(Map<String, NodeDVR> net, Map<String, Map<String, Integer>> topo, String failNode) {
        long msgs = 0;
        boolean changed = true;
        int round = 0;

        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Map<String, Integer>> snap = snapshotTDVR(net);
            changed = false;
            for (String node : net.keySet()) {
                for (String nb : topo.get(node).keySet()) {
                    // Skip partitioned node and skip removed links
                    if (nb.equals(failNode)) continue;
                    if (!net.containsKey(nb)) continue;
                    if (!net.get(node).isNeighbor(nb)) continue;

                    Map<String, Integer> s = snap.get(nb);
                    if (s != null) {
                        msgs++;
                        if (net.get(node).updateFromNeighbor(nb, s)) changed = true;
                    }
                }
            }
        }
        return new long[]{round, msgs};
    }

    /**
     * BSDVR measurement after partition
     */
    static long[] measureBSDVRPartition(Map<String, NodeBSDVR> net, Map<String, Map<String, Integer>> topo, String failNode) {
        long msgs = 0;
        boolean changed = true;
        int round = 0;

        while (changed && round < MAX_ROUNDS) {
            round++;
            changed = false;

            for (String node : net.keySet()) {
                for (String nb : topo.get(node).keySet()) {
                    if (nb.equals(failNode)) continue;
                    if (!net.containsKey(nb)) continue;
                    if (!net.get(node).isNeighbor(nb)) continue;

                    Map<String, int[]> dv = net.get(nb).createDistanceVectorFor(node);
                    msgs++;

                    if (!net.get(node).isActiveTo(failNode) && dv.containsKey(failNode)) {
                        dv = new HashMap<>(dv); // don't mutate the live DV
                        dv.put(failNode, new int[]{NodeBSDVR.INF, 0});
                    }

                    if (net.get(node).updateFromNeighbor(nb, dv)) changed = true;
                }
            }
        }
        return new long[]{round, msgs};
    }

    static Map<String, Map<String, Integer>> snapshotTDVR(Map<String, NodeDVR> net) {
        Map<String, Map<String, Integer>> snap = new HashMap<>();
        for (String n : net.keySet()) snap.put(n, net.get(n).createDistanceVector());
        return snap;
    }

    static Map<String, Map<String, int[]>> snapshotBSDVR(
            Map<String, NodeBSDVR> net,
            Map<String, Map<String, Integer>> topo) {
        Map<String, Map<String, int[]>> snap = new HashMap<>();
        for (String src : net.keySet()) {
            for (String nb : topo.get(src).keySet()) {
                if (net.containsKey(nb)) {
                    snap.put(src + "->" + nb, net.get(src).createDistanceVectorFor(nb));
                }
            }
        }
        return snap;
    }
}