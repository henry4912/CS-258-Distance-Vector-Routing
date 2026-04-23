import java.util.*;

public class Node {
    public static final int INF = 9999;

    private String name;
    private Map<String, Integer> neighbors;
    private Map<String, RouteEntry> routingTable;

    public Node(String name) {
        this.name = name;
        this.neighbors = new HashMap<>();
        this.routingTable = new HashMap<>();

        routingTable.put(name, new RouteEntry(name, name, 0));
    }

    public String getName() {
        return name;
    }

    public void addNeighbor(String neighborName, int cost) {
        neighbors.put(neighborName, cost);
        routingTable.put(neighborName, new RouteEntry(neighborName, neighborName, cost));
    }

    public Map<String, Integer> createDistanceVector() {
        Map<String, Integer> distanceVector = new HashMap<>();
        for (RouteEntry entry : routingTable.values()) {
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

            RouteEntry current = routingTable.get(destination);

            boolean shouldUpdate = current == null || newCost < current.getCost() || current.getNextHop().equals(neighborName);

            if (shouldUpdate) {
                boolean isActuallyDifferent = current == null || current.getCost() != newCost || !current.getNextHop().equals(neighborName);

                if (isActuallyDifferent) {
                    routingTable.put(destination, new RouteEntry(destination, neighborName, newCost));
                    changed = true;
                }
            }
        }

        return changed;
    }

    public void printRoutingTable() {
        System.out.println("Routing table for node " + name + ":");
        for (RouteEntry entry : routingTable.values()) {
            System.out.println(entry);
        }
    }

    public void removeNeighbor(String neighborName) {
        neighbors.remove(neighborName);

        for (Map.Entry<String, RouteEntry> entry : routingTable.entrySet()) {
            RouteEntry route = entry.getValue();

            if (route.getNextHop().equals(neighborName)) {
                route.setCost(INF);
            }
        }

        routingTable.put(name, new RouteEntry(name, name, 0));
    }

    public boolean isNeighbor(String neighborName) {
        return neighbors.containsKey(neighborName);
    }
}