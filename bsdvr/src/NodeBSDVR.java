import java.util.*;

public class NodeBSDVR {

    public static final int INF = 9999;

    private final String name;

    private final Map<String, Integer> neighbors;

    private final Map<String, RouteEntryBSDVR> routingTable;

    // -------------------------------------------------------------------------
    public NodeBSDVR(String name) {
        this.name         = name;
        this.neighbors    = new HashMap<>();
        this.routingTable = new HashMap<>();
        //route to itself is cost 0
        routingTable.put(name, new RouteEntryBSDVR(name, name, 0, true));
    }

    public String getName() {
        return name;
    }
    public boolean isNeighbor(String n) {
        return neighbors.containsKey(n);
    }
    public int getCostTo(String dest) {
        RouteEntryBSDVR e = routingTable.get(dest);
        if (e == null) {
            return INF;
        } else {
            return e.getCost();
        }
    }
    public boolean isActiveTo(String dest) {
        RouteEntryBSDVR e = routingTable.get(dest);
        return e != null && e.isActive();
    }

    public void addNeighbor(String neighborName, int cost) {
        neighbors.put(neighborName, cost);
        routingTable.put(neighborName,
                new RouteEntryBSDVR(neighborName, neighborName, cost, true));
    }

    public void failLink(String neighborName) {
        neighbors.remove(neighborName);
        for (RouteEntryBSDVR entry : routingTable.values()) {
            if (!entry.getDestination().equals(name)
                    && entry.getNextHop().equals(neighborName)) {
                entry.setActive(false);
                entry.setCost(INF);
            }
        }
        routingTable.put(name, new RouteEntryBSDVR(name, name, 0, true));
    }

    public Map<String, int[]> createDistanceVectorFor(String toNeighbor) {
        Map<String, int[]> dv = new HashMap<>();
        for (RouteEntryBSDVR entry : routingTable.values()) {
            String dest = entry.getDestination();
            if (!dest.equals(name) && entry.getNextHop().equals(toNeighbor)) {
                // Poisoned reverse: mask this route
                dv.put(dest, new int[]{INF, 0});
            } else {
                int state;
                if (entry.isActive()) {
                    state = 1;
                } else {
                    state = 0;
                }
                dv.put(dest, new int[]{entry.getCost(), state});
            }
        }
        return dv;
    }

    public Map<String, int[]> createDistanceVector() {
        Map<String, int[]> dv = new HashMap<>();
        for (RouteEntryBSDVR entry : routingTable.values()) {
            int state;
            if (entry.isActive()) {
                state = 1;
            } else {
                state = 0;
            }
            dv.put(entry.getDestination(), new int[]{entry.getCost(), state});
        }
        return dv;
    }


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

            int newCost;
            if (receivedCost >= INF) {
                newCost = INF;
            } else {
                newCost = Math.min(linkCost + receivedCost, INF);
            }

            // An entry can only be active if both the received state is active
            // AND the resulting cost is finite.
            boolean newEffectiveActive = receivedActive && (newCost < INF);

            RouteEntryBSDVR current = routingTable.get(dest);

            if (current == null) {
                routingTable.put(dest,
                        new RouteEntryBSDVR(dest, senderName, newCost, newEffectiveActive));
                changed = true;
                continue;
            }

            boolean curActive  = current.isActive();
            boolean isPrimary  = current.getNextHop().equals(senderName);

            if (curActive == receivedActive) {
                if (isPrimary || newCost < current.getCost()) {
                    if (current.getCost() != newCost || !isPrimary) {
                        routingTable.put(dest,
                                new RouteEntryBSDVR(dest, senderName, newCost, curActive));
                        changed = true;
                    }
                }
            } else if (!curActive && receivedActive) {
                if (newCost < INF) {
                    routingTable.put(dest,
                            new RouteEntryBSDVR(dest, senderName, newCost, true));
                    changed = true;
                }
            } else {
                if (current.getCost() >= INF) {
                    routingTable.put(dest,
                            new RouteEntryBSDVR(dest, senderName, INF, false));
                    changed = true;

                } else if (isPrimary) {
                    routingTable.put(dest,
                            new RouteEntryBSDVR(dest, senderName, INF, false));
                    changed = true;

                }
            }
        }
        return changed;
    }

    public void printRoutingTable() {
        System.out.println("  Node " + name + ":");
        routingTable.values().stream()
                .sorted(Comparator.comparing(RouteEntryBSDVR::getDestination))
                .forEach(entry -> System.out.println("    " + entry));
    }
}