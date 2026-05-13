import java.util.*;

public class TopologyGenerator {

    /**
     * Generate a random connected topology.
     *
     * @param nodeCount      number of nodes
     * @param targetAvgDeg   target average degree (paper uses 3–5)
     * @param minDegree      minimum degree per node (use 2 for single-link experiments)
     * @param rng            seeded Random for reproducibility
     * @return adjacency map: nodeName → (neighborName → linkCost)
     */
    public static Map<String, Map<String, Integer>> generate(
            int nodeCount, int targetAvgDeg, int minDegree, Random rng) {

        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) nodes.add("N" + i);

        Map<String, Map<String, Integer>> adj = new LinkedHashMap<>();
        for (String n : nodes) adj.put(n, new LinkedHashMap<>());

        List<String> inTree = new ArrayList<>();
        inTree.add(nodes.get(0));

        List<String> notInTree = new ArrayList<>(nodes.subList(1, nodes.size()));
        Collections.shuffle(notInTree, rng);

        for (String node : notInTree) {
            String parent = inTree.get(rng.nextInt(inTree.size()));
            addEdge(adj, node, parent, 1 + rng.nextInt(10));
            inTree.add(node);
        }

        int targetEdges = (nodeCount * targetAvgDeg) / 2;
        int currentEdges = nodeCount - 1;    // spanning tree has exactly n-1 edges

        for (int attempt = 0; attempt < targetEdges * 30 && currentEdges < targetEdges; attempt++) {
            String a = nodes.get(rng.nextInt(nodeCount));
            String b = nodes.get(rng.nextInt(nodeCount));
            if (!a.equals(b) && !adj.get(a).containsKey(b)) {
                addEdge(adj, a, b, 1 + rng.nextInt(10));
                currentEdges++;
            }
        }

        for (String node : nodes) {
            while (adj.get(node).size() < minDegree) {
                // Connect to any node not already a neighbor
                for (String other : nodes) {
                    if (!other.equals(node) && !adj.get(node).containsKey(other)) {
                        addEdge(adj, node, other, 1 + rng.nextInt(10));
                        break;
                    }
                }
            }
        }

        return adj;
    }

    private static void addEdge(Map<String, Map<String, Integer>> adj, String a, String b, int cost) {
        adj.get(a).put(b, cost);
        adj.get(b).put(a, cost);
    }

    public static List<String[]> getEdges(Map<String, Map<String, Integer>> adj) {
        List<String[]> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String a : adj.keySet()) {
            for (Map.Entry<String, Integer> e : adj.get(a).entrySet()) {
                String b   = e.getKey();
                String key;
                if (a.compareTo(b) < 0) {
                    key = a + "|" + b;
                } else {
                    key = b + "|" + a;
                }
                if (seen.add(key)) {
                    edges.add(new String[]{a, b, String.valueOf(e.getValue())});
                }
            }
        }
        return edges;
    }

    //get all nodes
    public static List<String> getNodes(Map<String, Map<String, Integer>> adj) {
        return new ArrayList<>(adj.keySet());
    }

    //get average degree
    public static double avgDegree(Map<String, Map<String, Integer>> adj) {
        int total = 0;
        for (Map<String, Integer> nb : adj.values()) total += nb.size();
        return (double) total / adj.size();
    }
}