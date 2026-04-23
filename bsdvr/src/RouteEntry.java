public class RouteEntry {

    public enum State {
        ACTIVE,
        INACTIVE
    }

    private String destination;
    private String nextHop;
    private int cost;
    private State state;

    public RouteEntry(String destination, String nextHop, int cost, State state) {
        this.destination = destination;
        this.nextHop = nextHop;
        this.cost = cost;
        this.state = state;
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

    public State getState() {
        return state;
    }

    //setters
    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setState(State state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "dest: " + destination + ", nextHop: " + nextHop + ", cost: " + cost + ", state: " + state;
    }
}
