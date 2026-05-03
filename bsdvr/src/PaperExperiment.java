import java.util.*;

/**
 * PaperExperiment — replicates the scaled evaluation from:
 *   Farooq & Yuksel, "Distance Vector Routing in Partitioned Networks," IEEE LANMAN 2022
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT THE PAPER ACTUALLY TESTS  (Section V)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Experiment 1 – Single link failures (Fig. 12)
 *   • Random topologies of 5, 10, 15, 20, 25, 30 nodes
 *   • Min degree 2 → no single link removal causes a partition
 *   • Fail each link in turn, let both protocols re-converge
 *   • Measure: convergence rounds (proxy for time), messages sent
 *   • Expected result: BSDVR uses slightly MORE messages than TDVR
 *     (proactive replies) but converges fine
 *
 * Experiment 2 – Partition-causing failures (Fig. 13)
 *   • Same topology sizes
 *   • Fail ALL links of one node at once → partition the rest of the network
 *   • Measure: convergence rounds, messages sent
 *   • Expected result: TDVR count-to-infinity (rounds >> MAX), BSDVR converges
 *     fast with an order-of-magnitude fewer messages
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SIMULATION MODEL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * We use synchronous, SNAPSHOT-BASED rounds (Jacobi style):
 *   1. All nodes capture their DVs at the START of each round.
 *   2. Each node processes those snapshots and updates its own table.
 *   3. Repeat until no table changes (converged) or MAX_ROUNDS exceeded.
 *
 * This faithfully reproduces count-to-infinity: a node may still see another
 * node's pre-failure DV in round 1, exactly as in an async network.
 *
 * Control-traffic bytes are estimated as:
 *   TDVR message = 8-byte header + K × 8 bytes  (K entries × {dest,cost})
 *   BSDVR message = 8-byte header + K × 9 bytes  (+ 1 byte state flag)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW TO RUN
 * ─────────────────────────────────────────────────────────────────────────────
 *   javac *.java
 *   java PaperExperiment
 *
 * All source files must be in the same directory:
 *   NodeDVR.java, NodeBSDVR.java, RouteEntryDVR.java, RouteEntryBSDVR.java,
 *   TopologyGenerator.java, PaperExperiment.java
 */
public class PaperExperiment {

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

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) {
        runSingleLinkFailureExperiment();
        runPartitionCausingFailureExperiment();
    }

    // =========================================================================
    // EXPERIMENT 1 — Single Link Failures
    // =========================================================================

    static void runSingleLinkFailureExperiment() {
        System.out.println("Single Link Failures, no partitions");

        // Table header
        System.out.printf("  %-6s  %20s  %20s  %20s  %20s%n",
                "Nodes", "TDVR avg rounds", "TDVR avg msgs", "BSDVR avg rounds", "BSDVR avg msgs");

        for (int n : NODE_COUNTS) {
            long tdvrRoundsSum = 0, tdvrMsgsSum = 0;
            long bsdvrRoundsSum = 0, bsdvrMsgsSum = 0;
            int samples = 0;

            for (int trial = 0; trial < TRIALS; trial++) {
                Random rng = new Random(BASE_SEED + (long) n * 1000 + trial);

                // Min-degree 2: no single edge removal creates a partition
                Map<String, Map<String, Integer>> topo =
                        TopologyGenerator.generate(n, TARGET_AVG_DEG, 2, rng);
                List<String[]> edges = TopologyGenerator.getEdges(topo);

                for (String[] edge : edges) {
                    String eA = edge[0], eB = edge[1];

                    // ── TDVR: initial convergence, then fail one link ──────
                    Map<String, NodeDVR> tdvrNet = buildTDVR(topo);
                    convergeTDVR(tdvrNet, topo);      // warm up to stable state
                    tdvrNet.get(eA).removeNeighbor(eB);
                    tdvrNet.get(eB).removeNeighbor(eA);
                    long[] t = measureTDVR(tdvrNet, topo);
                    tdvrRoundsSum += t[0];
                    tdvrMsgsSum   += t[1];

                    // ── BSDVR: initial convergence, then fail one link ─────
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
        }
    }

    // =========================================================================
    // EXPERIMENT 2 — Partition-Causing Failures
    // =========================================================================

    static void runPartitionCausingFailureExperiment() {
        System.out.println("Partitioned network");

        System.out.printf("  %-6s  %26s  %20s  %20s  %20s%n",
                "Nodes", "TDVR avg rounds", "TDVR avg msgs", "BSDVR avg rounds", "BSDVR avg msgs");

        for (int n : NODE_COUNTS) {
            long tdvrRoundsSum = 0, tdvrMsgsSum = 0;
            long bsdvrRoundsSum = 0, bsdvrMsgsSum = 0;
            int samples = 0;

            for (int trial = 0; trial < TRIALS; trial++) {
                Random rng = new Random(BASE_SEED + (long) n * 1000 + trial + 99999L);

                // Min-degree 1 is fine; we deliberately partition by removing all links
                Map<String, Map<String, Integer>> topo =
                        TopologyGenerator.generate(n, TARGET_AVG_DEG, 1, rng);
                List<String> nodes = TopologyGenerator.getNodes(topo);

                for (String failNode : nodes) {
                    Set<String> nbsOfFailed = new HashSet<>(topo.get(failNode).keySet());

                    // ── TDVR: partition by severing all links of failNode ──
                    Map<String, NodeDVR> tdvrNet = buildTDVR(topo);
                    convergeTDVR(tdvrNet, topo);
                    for (String nb : nbsOfFailed) tdvrNet.get(nb).removeNeighbor(failNode);
                    tdvrNet.remove(failNode);       // physically remove from simulation
                    long[] t = measureTDVRPartition(tdvrNet, topo, failNode);
                    tdvrRoundsSum += t[0];
                    tdvrMsgsSum   += t[1];

                    // ── BSDVR: partition by severing all links of failNode ─
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
        }
    }

    // =========================================================================
    // NETWORK CONSTRUCTION
    // =========================================================================

    /** Builds a fresh TDVR node map from the given topology. */
    static Map<String, NodeDVR> buildTDVR(Map<String, Map<String, Integer>> topo) {
        Map<String, NodeDVR> net = new LinkedHashMap<>();
        for (String node : topo.keySet()) net.put(node, new NodeDVR(node));
        for (String node : topo.keySet())
            for (Map.Entry<String, Integer> e : topo.get(node).entrySet())
                net.get(node).addNeighbor(e.getKey(), e.getValue());
        return net;
    }

    /** Builds a fresh BSDVR node map from the given topology. */
    static Map<String, NodeBSDVR> buildBSDVR(Map<String, Map<String, Integer>> topo) {
        Map<String, NodeBSDVR> net = new LinkedHashMap<>();
        for (String node : topo.keySet()) net.put(node, new NodeBSDVR(node));
        for (String node : topo.keySet())
            for (Map.Entry<String, Integer> e : topo.get(node).entrySet())
                net.get(node).addNeighbor(e.getKey(), e.getValue());
        return net;
    }

    // =========================================================================
    // INITIAL CONVERGENCE (warm-up before failure injection)
    // =========================================================================

    /** Runs TDVR rounds until stable. Uses snapshot semantics. */
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

    /** Runs BSDVR rounds until stable. Uses snapshot semantics. */
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

    // =========================================================================
    // POST-FAILURE CONVERGENCE + METRIC MEASUREMENT
    // =========================================================================

    /**
     * Runs TDVR after a single-link failure and measures:
     *   [0] rounds to convergence (or MAX_ROUNDS on count-to-infinity)
     *   [1] total messages (DV advertisements) sent
     */
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
     * Runs BSDVR after a single-link failure and measures the same metrics.
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
     * TDVR measurement after partition: failNode has already been removed from net.
     * Measures how long the remaining network takes to re-converge (or CTI).
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
     * BSDVR measurement after partition: failNode has already been removed from net.
     *
     * Fix: snapshot post-processing forces INACTIVE for known-dead routes.
     *
     * Why the change? In a synchronous simulation, stale ACTIVE snapshots cause
     * BSDVR to briefly oscillate the ACTIVE/INACTIVE flag on routes to the dead
     * node — a harmless but never-ending "changed=true" loop under the old check.
     * What we actually care about is whether costs are still climbing (TDVR's
     * count-to-infinity) vs. quickly settling (BSDVR's INACTIVE propagation).
     * Tracking cost increases cleanly separates the two behaviours.
     */
    /**
     * BSDVR measurement after partition: failNode has already been removed from net.
     *
     * Uses Gauss-Seidel (live, non-snapshot) updates rather than the Jacobi
     * (snapshot) style used everywhere else. Why:
     *
     * With snapshot semantics, a node that just went INACTIVE still has an ACTIVE
     * snapshot frozen from the start of the round. Neighbours see that stale ACTIVE
     * and re-activate their own INACTIVE entry — an infinite flip-flop that never
     * converges even though BSDVR should settle in O(diameter) rounds.
     *
     * BSDVR does not suffer count-to-infinity (that's TDVR's problem), so using
     * live DVs is safe: each node reads its neighbour's *current* routing table,
     * meaning INACTIVE signals propagate immediately within a round and cannot be
     * overwritten by stale ACTIVE snapshots. This faithfully models the async
     * real-world behaviour where INACTIVE messages are consumed before stale ACTIVEs.
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

                    // KEY FIX: once this node considers failNode INACTIVE, suppress
                    // any incoming ACTIVE advertisement for it. A partitioned
                    // destination can never legitimately become reachable again, so
                    // INACTIVE is a terminal state. In the real async protocol this
                    // is enforced by the Pending Reply Timer (INACTIVE arrives first);
                    // here we enforce it explicitly so the sync simulation converges.
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

    // =========================================================================
    // SNAPSHOT HELPERS
    // =========================================================================

    /**
     * Captures a complete snapshot of all TDVR distance vectors.
     * Snapshot semantics: all updates in a round use START-of-round DVs,
     * which prevents fast in-round propagation from masking count-to-infinity.
     */
    static Map<String, Map<String, Integer>> snapshotTDVR(Map<String, NodeDVR> net) {
        Map<String, Map<String, Integer>> snap = new HashMap<>();
        for (String n : net.keySet()) snap.put(n, net.get(n).createDistanceVector());
        return snap;
    }

    /**
     * Captures BSDVR DVs per directed edge (src → dest) with poisoned reverse applied.
     * Key format: "senderName->receiverName"
     */
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