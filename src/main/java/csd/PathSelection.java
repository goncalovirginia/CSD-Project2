package csd;

import csd.records.Alliance;
import csd.records.Relay;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathSelection {

	private final List<Alliance> alliances;
	private final List<Relay> relays;

	public PathSelection(List<Alliance> alliances, List<Relay> relays) {
		this.alliances = alliances;
		this.relays = relays;
	}

	public double guardSecurity(String clientLocation, Set<String> guards) {
		return 0.0;
	}

	public double exitSecurity(String clientLocation, String exitLocation, String guard, String exit) {
		return 0.0;
	}

	public List<String> selectPath(List<Relay> relays, Map<String, String> guardParams, Map<String, String> exitParams) {
		return List.of();
	}

}
