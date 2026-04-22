import java.util.*;

public class Node {
    private String name;
    private HashMap<Node, ArrayList<Integer>> table;
    public Node(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public ArrayList<Integer> getRoute(Node n) {
        return table.get(n);
    }
}