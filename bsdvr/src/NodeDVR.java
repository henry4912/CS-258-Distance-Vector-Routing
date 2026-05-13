import java.util.*;

public class NodeDVR {
    public static final int INF = 9999;

    private String name;
    private Map<String, Integer> neighbors;
    private Map<String, RouteEntryDVR> routingTable;

    public NodeDVR(String name) {
        this.name = name;
        this.neighbors = new HashMap<>();
        this.routingTable = new HashMap<>();

        routingTable.put(name, new RouteEntryDVR(name, name, 0));
    }

    public String getName() {
        return name;
    }

    public void addNeighbor(String neighborName, int cost) {
        neighbors.put(neighborName, cost);
        routingTable.put(neighborName, new RouteEntryDVR(neighborName, neighborName, cost));
    }

    public Map<String, Integer> createDistanceVector() {
        Map<String, Integer> distanceVector = new HashMap<>();
        for (RouteEntryDVR entry : routingTable.values()) {
            distanceVector.put(entry.getDestination(), entry.getCost());
        }
        return distanceVector;
    }

    public boolean updateFromNeighbor(String neighborName, Map<String, Integer> neighborVector) {
        boolean changed = false;

        Integer costToNeighbor = neighbors.get(neighborName);
        if (costToNeighbor == null) {
            return false;
        }

        for (Map.Entry<String, Integer> entry : neighborVector.entrySet()) {
            String destination = entry.getKey();
            int neighborCost = entry.getValue();

            int newCost;
            if (neighborCost >= INF) {
                newCost = INF;
            } else {
                newCost = costToNeighbor + neighborCost;
                if (newCost > INF) {
                    newCost = INF;
                }
            }

            RouteEntryDVR current = routingTable.get(destination);

            boolean shouldUpdate = current == null || newCost < current.getCost() || current.getNextHop().equals(neighborName);

            if (shouldUpdate) {
                boolean isActuallyDifferent = current == null || current.getCost() != newCost || !current.getNextHop().equals(neighborName);

                if (isActuallyDifferent) {
                    routingTable.put(destination, new RouteEntryDVR(destination, neighborName, newCost));
                    changed = true;
                }
            }
        }

        return changed;
    }

    public void printRoutingTable() {
        System.out.println("Routing table for node " + name + ":");
        for (RouteEntryDVR entry : routingTable.values()) {
            System.out.println(entry);
        }
    }

    public void removeNeighbor(String neighborName) {
        neighbors.remove(neighborName);

        for (Map.Entry<String, RouteEntryDVR> entry : routingTable.entrySet()) {
            RouteEntryDVR route = entry.getValue();

            if (route.getNextHop().equals(neighborName)) {
                route.setCost(INF);
            }
        }

        routingTable.put(name, new RouteEntryDVR(name, name, 0));
    }

    public boolean isNeighbor(String neighborName) {
        return neighbors.containsKey(neighborName);
    }
}