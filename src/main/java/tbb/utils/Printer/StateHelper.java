package tbb.utils.Printer;

public class StateHelper {
	public StateHelper() {
		this(State.LOADING);
	}
	
	public StateHelper(State startingState) {
		update(startingState, "N/A");	
	}
	
	private State state;
	private String currentURL;
	
	@Override
	public String toString() {
		switch (getState()) {
		case NAVIGATING: return "Navigating";
		case LOADING: return "Loading";
		case COLLECTING: return "Collecting data";
		case WAITING: return "Idle";
		case SCROLLING: return "Scrolling page";
		case SCANNING: return "Scanning";
		case INTERACTING: return "Interacting";
		default: return "Unknown";
		}
	}

	public State getState() {
		return state;
	}
	public String getURL() {
		return currentURL;
	}

	public void update(State state, String currentURL) {
		this.state = state;
		this.currentURL = currentURL;
	}
}
