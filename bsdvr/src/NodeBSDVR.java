import java.util.*;

/**
 * NodeBSDVR — Binary State Distance Vector Routing node.
 *
 * Implements the protocol described in:
 *   Farooq & Yuksel, "Distance Vector Routing in Partitioned Networks," IEEE LANMAN 2022.
 *
 * Key ideas vs. traditional DVR (TDVR):
 *   1. Every routing-table entry carries an ACTIVE / INACTIVE state flag.
 *   2. When a link fails, affected entries are marked INACTIVE (not deleted).
 *   3. On receiving an INACTIVE update from the *primary* next-hop, the node
 *      immediately marks its own entry INACTIVE without looking for alternatives —
 *      this is the "forced recalculation" that stops count-to-infinity loops.
 *   4. Distance vectors sent to a neighbor apply POISONED REVERSE:
 *      routes whose next-hop IS that neighbor are advertised as INACTIVE/INF,
 *      preventing the neighbour from routing back through us.
 *
 * Merging rules (Table I of the paper):
 *   Current  Incoming   Action
 *   -------  --------   ------
 *   Active   Active     Bellman-Ford min-cost
 *   Inactive Inactive   Bellman-Ford min-cost
 *   Inactive Active     Accept active entry iff cost < INF  (re-activation)
 *   Active   Inactive   If primary path → accept inactive, forced recalculation
 *                       If non-primary  → ignore (keep our active path)
 *                       If current cost already INF → accept inactive
 */
public class NodeBSDVR {

    public static final int INF = 9999;

    private final String name;

    /** Direct neighbours: name → link cost */
    private final Map<String, Integer> neighbors;

    /**
     * One entry per known destination.
     * The distance-vector table (DVT) keeps BOTH active AND inactive entries
     * (we store only the best one of each type; here we simplify to one entry
     *  per destination, consistent with the reference simulation approach).
     */
    private final Map<String, RouteEntryBSDVR> routingTable;

    // -------------------------------------------------------------------------
    public NodeBSDVR(String name) {
        this.name         = name;
        this.neighbors    = new HashMap<>();
        this.routingTable = new HashMap<>();
        // Self-route is always active with cost 0.
        routingTable.put(name, new RouteEntryBSDVR(name, name, 0, true));
    }

    // ---- basic accessors ----------------------------------------------------

    public String getName() {
        return name;
    }
    public boolean isNeighbor(String n) {
        return neighbors.containsKey(n);
    }
    public int getCostTo(String dest) {
        RouteEntryBSDVR e = routingTable.get(dest);
        return (e == null) ? INF : e.getCost();
    }
    public boolean isActiveTo(String dest) {
        RouteEntryBSDVR e = routingTable.get(dest);
        return e != null && e.isActive();
    }

    // ---- topology setup -----------------------------------------------------

    public void addNeighbor(String neighborName, int cost) {
        neighbors.put(neighborName, cost);
        routingTable.put(neighborName,
                new RouteEntryBSDVR(neighborName, neighborName, cost, true));
    }

    // ---- link failure -------------------------------------------------------

    /**
     * Simulates a link failure to {@code neighborName}.
     *
     * BSDVR behaviour: mark every route whose next-hop is the failed neighbour
     * as INACTIVE (cost → INF).  The entry is KEPT (not deleted) so that
     * neighbours receiving the update know a failure occurred, not just a cost
     * increase.  This is the key that breaks count-to-infinity cycles.
     */
    public void failLink(String neighborName) {
        neighbors.remove(neighborName);
        for (RouteEntryBSDVR entry : routingTable.values()) {
            if (!entry.getDestination().equals(name)
                    && entry.getNextHop().equals(neighborName)) {
                entry.setActive(false);
                entry.setCost(INF);
            }
        }
        // Self-route always stays active.
        routingTable.put(name, new RouteEntryBSDVR(name, name, 0, true));
    }

    // ---- distance-vector creation -------------------------------------------

    /**
     * Creates the distance vector to advertise to {@code toNeighbor}.
     *
     * Applies POISONED REVERSE: if our best route to destination X goes
     * through {@code toNeighbor}, we advertise X as INACTIVE / INF to that
     * neighbour.  This prevents the neighbour from routing back through us
     * and is one of the standard BSDVR optimisations mentioned in the paper.
     *
     * @return Map: destination → int[]{cost, state}  (state: 1=active, 0=inactive)
     */
    public Map<String, int[]> createDistanceVectorFor(String toNeighbor) {
        Map<String, int[]> dv = new HashMap<>();
        for (RouteEntryBSDVR entry : routingTable.values()) {
            String dest = entry.getDestination();
            if (!dest.equals(name) && entry.getNextHop().equals(toNeighbor)) {
                // Poisoned reverse: mask this route
                dv.put(dest, new int[]{INF, 0});
            } else {
                dv.put(dest, new int[]{entry.getCost(), entry.isActive() ? 1 : 0});
            }
        }
        return dv;
    }

    /**
     * Creates a full distance vector (no poisoned reverse).
     * Used for inspection / initial convergence display.
     */
    public Map<String, int[]> createDistanceVector() {
        Map<String, int[]> dv = new HashMap<>();
        for (RouteEntryBSDVR entry : routingTable.values()) {
            dv.put(entry.getDestination(),
                    new int[]{entry.getCost(), entry.isActive() ? 1 : 0});
        }
        return dv;
    }

    // ---- core update logic --------------------------------------------------

    /**
     * Processes a distance-vector update from {@code senderName}.
     *
     * Implements the four cases of Table I from the BSDVR paper.
     *
     * @param senderName  which neighbour sent the update
     * @param senderDV    their distance vector: dest → int[]{cost, state}
     * @return true if any entry in this node's routing table changed
     */
    public boolean updateFromNeighbor(String senderName, Map<String, int[]> senderDV) {
        boolean changed = false;

        Integer linkCost = neighbors.get(senderName);
        if (linkCost == null) return false;   // no longer a neighbour

        for (Map.Entry<String, int[]> e : senderDV.entrySet()) {
            String dest = e.getKey();
            if (dest.equals(name)) continue;  // skip self-advertisement

            int     receivedCost   = e.getValue()[0];
            boolean receivedActive = (e.getValue()[1] == 1);

            // Compute total path cost through sender.
            int newCost = (receivedCost >= INF)
                    ? INF
                    : Math.min(linkCost + receivedCost, INF);

            // An entry can only be active if both the received state is active
            // AND the resulting cost is finite.
            boolean newEffectiveActive = receivedActive && (newCost < INF);

            RouteEntryBSDVR current = routingTable.get(dest);

            // --- No existing entry: install whatever we received. ------------
            if (current == null) {
                routingTable.put(dest,
                        new RouteEntryBSDVR(dest, senderName, newCost, newEffectiveActive));
                changed = true;
                continue;
            }

            boolean curActive  = current.isActive();
            boolean isPrimary  = current.getNextHop().equals(senderName);

            // -----------------------------------------------------------------
            // TABLE I  — Merging rules
            // -----------------------------------------------------------------

            if (curActive == receivedActive) {
                // CASE 1 (Active + Active)  or  CASE 2 (Inactive + Inactive)
                // → standard Bellman-Ford: prefer lower cost, always trust primary.
                if (isPrimary || newCost < current.getCost()) {
                    if (current.getCost() != newCost || !isPrimary) {
                        routingTable.put(dest,
                                new RouteEntryBSDVR(dest, senderName, newCost, curActive));
                        changed = true;
                    }
                }

            } else if (!curActive && receivedActive) {
                // CASE 3: Inactive (current) + Active (incoming)
                // → Re-activate path if cost is finite (sender has found a live route).
                if (newCost < INF) {
                    routingTable.put(dest,
                            new RouteEntryBSDVR(dest, senderName, newCost, true));
                    changed = true;
                }

            } else {
                // CASE 4: Active (current) + Inactive (incoming)
                if (current.getCost() >= INF) {
                    // 4a: Our current active entry already has infinite cost → take the inactive.
                    routingTable.put(dest,
                            new RouteEntryBSDVR(dest, senderName, INF, false));
                    changed = true;

                } else if (isPrimary) {
                    // 4b: Inactive update from PRIMARY next-hop — forced recalculation.
                    // The paper states: accept inactive, erase all alternatives.
                    // This is the critical anti-count-to-infinity mechanism.
                    // In our single-best-entry model, marking inactive IS the erasure.
                    routingTable.put(dest,
                            new RouteEntryBSDVR(dest, senderName, INF, false));
                    changed = true;

                }
                // 4c: Inactive from NON-primary while we still have a valid active path
                // → ignore (keep our active route).  The full protocol would also send
                //   a proactive reply; that is simplified away here.
            }
        }
        return changed;
    }

    // ---- display ------------------------------------------------------------

    public void printRoutingTable() {
        System.out.println("  Node " + name + ":");
        routingTable.values().stream()
                .sorted(Comparator.comparing(RouteEntryBSDVR::getDestination))
                .forEach(entry -> System.out.println("    " + entry));
    }
}