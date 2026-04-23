public class Main {
    public static void main(String[] args) {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");

        // Partition test topology:
        // A --1-- B --2-- C
        a.addNeighbor("B", 1);

        b.addNeighbor("A", 1);
        b.addNeighbor("C", 2);

        c.addNeighbor("B", 2);

        System.out.println("=== Initial convergence ===");
        runUntilConverged(a, b, c);

        System.out.println("\n=== Remove link B-C ===");
        b.removeNeighbor("C");
        c.removeNeighbor("B");

        a.printRoutingTable();
        b.printRoutingTable();
        c.printRoutingTable();

        System.out.println("\n=== Re-converge after failure ===");
        runUntilConverged(a, b, c);
    }

    public static void runUntilConverged(Node a, Node b, Node c) {
        boolean changed;
        int round = 0;

        do {
            round++;
            changed = false;

            System.out.println("\n--- Round " + round + " ---");

            if (a.isNeighbor("B") && a.updateFromNeighbor("B", b.createDistanceVector())) changed = true;
            if (a.isNeighbor("C") && a.updateFromNeighbor("C", c.createDistanceVector())) changed = true;

            if (b.isNeighbor("A") && b.updateFromNeighbor("A", a.createDistanceVector())) changed = true;
            if (b.isNeighbor("C") && b.updateFromNeighbor("C", c.createDistanceVector())) changed = true;

            if (c.isNeighbor("A") && c.updateFromNeighbor("A", a.createDistanceVector())) changed = true;
            if (c.isNeighbor("B") && c.updateFromNeighbor("B", b.createDistanceVector())) changed = true;

            a.printRoutingTable();
            b.printRoutingTable();
            c.printRoutingTable();

        } while (changed);

        System.out.println("Network converged.");
    }
}