/**
 * RouteEntryBSDVR — a single entry in the BSDVR distance-vector table.
 *
 * The key extension over TDVR is the boolean `active` field (the "binary state"
 * from the paper).  An ACTIVE entry means the path is currently reachable;
 * an INACTIVE entry means the path was once known but is now disconnected.
 * Keeping inactive entries (instead of deleting them) lets the protocol detect
 * loops and avoid count-to-infinity during partition-causing failures.
 */
public class RouteEntryBSDVR {

    public static final int INF = 9999;

    private final String destination;
    private String      nextHop;
    private int         cost;
    private boolean     active;   // true = ACTIVE (reachable), false = INACTIVE

    public RouteEntryBSDVR(String destination, String nextHop, int cost, boolean active) {
        this.destination = destination;
        this.nextHop     = nextHop;
        this.cost        = cost;
        this.active      = active;
    }

    // ---- getters ----
    public String getDestination() {
        return destination;
    }

    public String getNextHop() {
        return nextHop;
    }

    public int getCost() {
        return cost;
    }

    public boolean isActive() {
        return active;
    }

    // ---- setters ----
    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }
    public void setCost(int cost)          {
        this.cost = cost;
    }
    public void setActive(boolean active)  { 
        this.active = active;
    }

    @Override
    public String toString() {
        String costStr = (cost >= INF) ? "INF" : String.valueOf(cost);
        return String.format("dest=%-3s  via=%-3s  cost=%-5s  state=%s",
                destination, nextHop, costStr, active ? "ACTIVE" : "INACTIVE");
    }
}