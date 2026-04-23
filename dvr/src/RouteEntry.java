public class RouteEntry {
    private String destination;
    private String nextHop;
    private int cost;

    public RouteEntry(String destination, String nextHop, int cost) {
        this.destination = destination;
        this.nextHop = nextHop;
        this.cost = cost;
    }

    //Getters
    public String getDestination() {
        return destination;
    }

    public String getNextHop() {
        return nextHop;
    }

    public int getCost() {
        return cost;
    }

    //setters
    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    @Override
    public String toString() {
        return "dest: " + destination + ", nextHop: " + nextHop + ", cost: " + cost;
    }
}
