import java.util.*;

/**
 * TopologyGenerator — builds random connected graphs for scaled BSDVR experiments.
 *
 * Replicates the topology model from:
 *   Farooq & Yuksel, "Distance Vector Routing in Partitioned Networks," IEEE LANMAN 2022
 *
 * The paper uses topologies with:
 *   • N nodes (5, 10, 15, 20, 25, 30)
 *   • 3–5 average node degree
 *   • Minimum degree 2 for single-link-failure trials (so no link removal creates a partition)
 *   • 3 distinct random topologies per node count (trials)
 *
 * Construction strategy:
 *   1. Build a random spanning tree  → guarantees full connectivity.
 *   2. Add random extra edges        → approaches target average degree.
 *   3. Lift any node below minDegree → satisfies the degree floor.
 *
 * Link costs are drawn uniformly from [1, 10].
 */
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

        // --- build node name list -------------------------------------------
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) nodes.add("N" + i);

        Map<String, Map<String, Integer>> adj = new LinkedHashMap<>();
        for (String n : nodes) adj.put(n, new LinkedHashMap<>());

        // --- Step 1: random spanning tree (guarantees connectivity) ----------
        List<String> inTree = new ArrayList<>();
        inTree.add(nodes.get(0));

        List<String> notInTree = new ArrayList<>(nodes.subList(1, nodes.size()));
        Collections.shuffle(notInTree, rng);

        for (String node : notInTree) {
            String parent = inTree.get(rng.nextInt(inTree.size()));
            addEdge(adj, node, parent, 1 + rng.nextInt(10));
            inTree.add(node);
        }

        // --- Step 2: add extra edges to hit target average degree ------------
        //   targetEdges = (N * avgDeg) / 2   (each edge counted twice in degree sum)
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

        // --- Step 3: lift any node below minDegree ---------------------------
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Undirected edge: sets both directions in the adjacency map. */
    private static void addEdge(Map<String, Map<String, Integer>> adj, String a, String b, int cost) {
        adj.get(a).put(b, cost);
        adj.get(b).put(a, cost);
    }

    /**
     * Returns every edge exactly once as {nodeA, nodeB, costString}.
     * Useful for iterating over edges without double-counting.
     */
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

    /** Returns a list of all node names in the topology. */
    public static List<String> getNodes(Map<String, Map<String, Integer>> adj) {
        return new ArrayList<>(adj.keySet());
    }

    /** Computes the actual average degree of the generated topology. */
    public static double avgDegree(Map<String, Map<String, Integer>> adj) {
        int total = 0;
        for (Map<String, Integer> nb : adj.values()) total += nb.size();
        return (double) total / adj.size();
    }
}