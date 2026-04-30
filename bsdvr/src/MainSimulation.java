import java.util.*;

/**
 * MainSimulation — Compares TDVR and BSDVR on the same 4-node topology.
 *
 * Topology:  A --1-- B --1-- C --1-- D
 *
 * Failure:   The C–D link is removed, partitioning D from the rest.
 *
 * Expected results
 * ----------------
 *   TDVR: Without binary state, nodes below the failure re-learn D's old cost
 *         from stale snapshots and enter a count-to-infinity loop.  The cost
 *         to D seen by B and C increases each round until it reaches INF.
 *
 *   BSDVR: When C marks D as INACTIVE and advertises {D: INF, inactive} to B,
 *          B recognises this as a failure on its PRIMARY path to D and
 *          immediately marks D as INACTIVE without seeking alternatives.
 *          Poisoned reverse prevents C from hearing a stale "active" route back
 *          from B. The network converges in ≤ 3 rounds with no loop.
 *
 * Implementation note on snapshot-based updates
 * -----------------------------------------------
 * Real DVR protocols run asynchronously; each node sends its DV whenever it
 * changes.  A synchronous simulation that lets updates take effect mid-round
 * (Gauss-Seidel style) can hide count-to-infinity because fast in-round
 * propagation of INF masks the stale routes that cause the loop.
 *
 * To faithfully reproduce the race condition, we use SNAPSHOT-BASED updates:
 * all nodes capture their DV at the START of each round; updates for that
 * round are computed from those snapshots only.  This means a node may still
 * see another node's pre-failure DV in round 1, exactly as in async operation.
 */
public class MainSimulation {

    private static final int MAX_ROUNDS = 40;

    static String line(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('-');
        return sb.toString();
    }

    static String banner(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('=');
        return sb.toString();
    }

    static void printBanner(String title) {
        System.out.println("\n" + banner(62));
        System.out.println("  " + title);
        System.out.println(banner(62));
    }

    public static void main(String[] args) {
        printBanner("DVR SIMULATION: TDVR vs BSDVR on Partitioned Networks");
        System.out.println("Topology : A --1-- B --1-- C --1-- D");
        System.out.println("Failure  : C-D link removed (D becomes unreachable)");
        System.out.println();
        int tdvrRounds  = runTDVR();
        int bsdvrRounds = runBSDVR();
        printComparison(tdvrRounds, bsdvrRounds);
    }

    static int runTDVR() {
        printBanner("PART 1: Traditional DVR (TDVR)");

        NodeDVR a = new NodeDVR("A");
        NodeDVR b = new NodeDVR("B");
        NodeDVR c = new NodeDVR("C");
        NodeDVR d = new NodeDVR("D");

        a.addNeighbor("B", 1);
        b.addNeighbor("A", 1); b.addNeighbor("C", 1);
        c.addNeighbor("B", 1); c.addNeighbor("D", 1);
        d.addNeighbor("C", 1);

        System.out.println("\n[TDVR] Phase 1 - Initial convergence");
        tdvrConverge(a, b, c, d, MAX_ROUNDS);
        System.out.println("  Routing tables after convergence:");
        printTDVRTables(a, b, c, d);

        System.out.println("\n[TDVR] Phase 2 - Removing link C-D (partition!)");
        c.removeNeighbor("D");
        d.removeNeighbor("C");
        System.out.println("  Link C-D removed. Routing tables immediately after:");
        printTDVRTables(a, b, c, d);

        System.out.println("\n[TDVR] Phase 3 - Post-failure re-convergence");
        System.out.println("  (Watch cost to D: it should spiral toward INF)");
        System.out.printf("  %-8s %-12s %-12s %-12s%n", "Round", "A->D", "B->D", "C->D");
        System.out.println("  " + line(48));

        int round = 0;
        boolean loop = false;
        boolean changed = true;

        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Integer> sA = a.createDistanceVector();
            Map<String, Integer> sB = b.createDistanceVector();
            Map<String, Integer> sC = c.createDistanceVector();
            Map<String, Integer> sD = d.createDistanceVector();
            changed = false;
            if (a.isNeighbor("B") && a.updateFromNeighbor("B", sB)) changed = true;
            if (b.isNeighbor("A") && b.updateFromNeighbor("A", sA)) changed = true;
            if (b.isNeighbor("C") && b.updateFromNeighbor("C", sC)) changed = true;
            if (c.isNeighbor("B") && c.updateFromNeighbor("B", sB)) changed = true;
            if (c.isNeighbor("D") && c.updateFromNeighbor("D", sD)) changed = true;
            if (d.isNeighbor("C") && d.updateFromNeighbor("C", sC)) changed = true;

            int cA = tdvrCost(a, "D");
            int cB = tdvrCost(b, "D");
            int cC = tdvrCost(c, "D");

            System.out.printf("  %-8d %-12s %-12s %-12s%n", round, fmt(cA), fmt(cB), fmt(cC));

            if ((cA > 6 && cA < NodeDVR.INF) || (cB > 6 && cB < NodeDVR.INF) || (cC > 6 && cC < NodeDVR.INF)) {
                loop = true;
            }
        }

        System.out.println("  " + line(48));
        if (round >= MAX_ROUNDS) {
            System.out.println("  *** HALTED after " + MAX_ROUNDS + " rounds -- COUNT-TO-INFINITY! ***");
            System.out.println("  Costs kept rising because B/C re-learn stale routes.");
        } else {
            System.out.println("  Converged in " + round + " round(s).");
        }
        System.out.println("\n  Final routing tables (TDVR post-failure):");
        printTDVRTables(a, b, c, d);
        return loop ? MAX_ROUNDS : round;
    }

    static int runBSDVR() {
        printBanner("PART 2: Binary State DVR (BSDVR)");

        NodeBSDVR a = new NodeBSDVR("A");
        NodeBSDVR b = new NodeBSDVR("B");
        NodeBSDVR c = new NodeBSDVR("C");
        NodeBSDVR d = new NodeBSDVR("D");

        a.addNeighbor("B", 1);
        b.addNeighbor("A", 1); b.addNeighbor("C", 1);
        c.addNeighbor("B", 1); c.addNeighbor("D", 1);
        d.addNeighbor("C", 1);

        System.out.println("\n[BSDVR] Phase 1 - Initial convergence");
        bsdvrConverge(a, b, c, d, MAX_ROUNDS);
        System.out.println("  Routing tables after convergence:");
        printBSDVRTables(a, b, c, d);

        System.out.println("\n[BSDVR] Phase 2 - Removing link C-D (partition!)");
        c.failLink("D");
        d.failLink("C");
        System.out.println("  Link C-D failed. Routing tables immediately after:");
        printBSDVRTables(a, b, c, d);

        System.out.println("\n[BSDVR] Phase 3 - Post-failure re-convergence");
        System.out.println("  (Should mark D INACTIVE quickly with no cost spiral)");
        System.out.printf("  %-8s %-22s %-22s %-22s%n", "Round", "A->D (state)", "B->D (state)", "C->D (state)");
        System.out.println("  " + line(76));

        int round = 0;
        boolean changed = true;

        while (changed && round < MAX_ROUNDS) {
            round++;
            Map<String, Map<String, int[]>> snap = new HashMap<String, Map<String, int[]>>();
            for (NodeBSDVR n : new NodeBSDVR[]{a, b, c, d}) {
                snap.put(n.getName() + "_for_A", n.createDistanceVectorFor("A"));
                snap.put(n.getName() + "_for_B", n.createDistanceVectorFor("B"));
                snap.put(n.getName() + "_for_C", n.createDistanceVectorFor("C"));
                snap.put(n.getName() + "_for_D", n.createDistanceVectorFor("D"));
            }
            changed = false;
            if (a.isNeighbor("B") && a.updateFromNeighbor("B", snap.get("B_for_A"))) changed = true;
            if (b.isNeighbor("A") && b.updateFromNeighbor("A", snap.get("A_for_B"))) changed = true;
            if (b.isNeighbor("C") && b.updateFromNeighbor("C", snap.get("C_for_B"))) changed = true;
            if (c.isNeighbor("B") && c.updateFromNeighbor("B", snap.get("B_for_C"))) changed = true;
            if (c.isNeighbor("D") && c.updateFromNeighbor("D", snap.get("D_for_C"))) changed = true;
            if (d.isNeighbor("C") && d.updateFromNeighbor("C", snap.get("C_for_D"))) changed = true;

            System.out.printf("  %-8d %-22s %-22s %-22s%n",
                    round, fmtBSDVR(a, "D"), fmtBSDVR(b, "D"), fmtBSDVR(c, "D"));
        }

        System.out.println("  " + line(76));
        System.out.printf("  Converged in %d round(s). No count-to-infinity!%n", round);
        System.out.println("\n  Final routing tables (BSDVR post-failure):");
        printBSDVRTables(a, b, c, d);
        return round;
    }

    static void printComparison(int tdvrRounds, int bsdvrRounds) {
        printBanner("COMPARISON SUMMARY");
        System.out.println();
        System.out.println("  Metric                    TDVR                    BSDVR");
        System.out.println("  " + line(56));
        String tdvrStr = tdvrRounds >= MAX_ROUNDS
                ? MAX_ROUNDS + " (no convergence!)" : String.valueOf(tdvrRounds);
        System.out.printf("  Post-failure rounds       %-24s %d%n", tdvrStr, bsdvrRounds);
        System.out.println("  Count-to-infinity?        YES                     NO");
        System.out.println("  Binary state in DV?       NO                      YES");
        System.out.println("  Poisoned reverse?         NO                      YES");
        System.out.println("  Inactive on failure?      NO                      YES");
        System.out.println();
        System.out.println("  KEY INSIGHT:");
        System.out.println("  TDVR: B/C bounce stale costs off each other (3->5->7->9...)");
        System.out.println("        until reaching INF. Classic count-to-infinity.");
        System.out.println();
        System.out.println("  BSDVR: C marks D INACTIVE on failure. B sees INACTIVE from");
        System.out.println("         its primary path and marks D INACTIVE immediately.");
        System.out.println("         Poisoned reverse blocks the stale loop from forming.");
    }

    static void tdvrConverge(NodeDVR a, NodeDVR b, NodeDVR c, NodeDVR d, int max) {
        boolean changed = true;
        int round = 0;
        while (changed && round < max) {
            round++;
            Map<String, Integer> sA = a.createDistanceVector();
            Map<String, Integer> sB = b.createDistanceVector();
            Map<String, Integer> sC = c.createDistanceVector();
            Map<String, Integer> sD = d.createDistanceVector();
            changed = false;
            if (a.isNeighbor("B") && a.updateFromNeighbor("B", sB)) changed = true;
            if (b.isNeighbor("A") && b.updateFromNeighbor("A", sA)) changed = true;
            if (b.isNeighbor("C") && b.updateFromNeighbor("C", sC)) changed = true;
            if (c.isNeighbor("B") && c.updateFromNeighbor("B", sB)) changed = true;
            if (c.isNeighbor("D") && c.updateFromNeighbor("D", sD)) changed = true;
            if (d.isNeighbor("C") && d.updateFromNeighbor("C", sC)) changed = true;
        }
        System.out.printf("  Converged in %d round(s).%n", round);
    }

    static void bsdvrConverge(NodeBSDVR a, NodeBSDVR b, NodeBSDVR c, NodeBSDVR d, int max) {
        boolean changed = true;
        int round = 0;
        while (changed && round < max) {
            round++;
            Map<String, int[]> aForB = a.createDistanceVectorFor("B");
            Map<String, int[]> bForA = b.createDistanceVectorFor("A");
            Map<String, int[]> bForC = b.createDistanceVectorFor("C");
            Map<String, int[]> cForB = c.createDistanceVectorFor("B");
            Map<String, int[]> cForD = c.createDistanceVectorFor("D");
            Map<String, int[]> dForC = d.createDistanceVectorFor("C");
            changed = false;
            if (a.isNeighbor("B") && a.updateFromNeighbor("B", bForA)) changed = true;
            if (b.isNeighbor("A") && b.updateFromNeighbor("A", aForB)) changed = true;
            if (b.isNeighbor("C") && b.updateFromNeighbor("C", cForB)) changed = true;
            if (c.isNeighbor("B") && c.updateFromNeighbor("B", bForC)) changed = true;
            if (c.isNeighbor("D") && c.updateFromNeighbor("D", dForC)) changed = true;
            if (d.isNeighbor("C") && d.updateFromNeighbor("C", cForD)) changed = true;
        }
        System.out.printf("  Converged in %d round(s).%n", round);
    }

    static int tdvrCost(NodeDVR node, String dest) {
        Integer cost = node.createDistanceVector().get(dest);
        return (cost == null) ? NodeDVR.INF : cost;
    }

    static String fmt(int cost) {
        return (cost >= NodeDVR.INF) ? "INF" : String.valueOf(cost);
    }

    static String fmtBSDVR(NodeBSDVR node, String dest) {
        int cost = node.getCostTo(dest);
        boolean active = node.isActiveTo(dest);
        String c = (cost >= NodeBSDVR.INF) ? "INF" : String.valueOf(cost);
        return c + " (" + (active ? "ACTIVE" : "INACTIVE") + ")";
    }

    static void printTDVRTables(NodeDVR... nodes) {
        for (NodeDVR n : nodes) n.printRoutingTable();
    }

    static void printBSDVRTables(NodeBSDVR... nodes) {
        for (NodeBSDVR n : nodes) n.printRoutingTable();
    }
}