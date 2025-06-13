package csd.records;

import java.util.List;

public record Relay(String fingerprint, String nickname, String ip, String asn, String exit, int port, int bandwidthMeasured, int bandwidthAverage, int bandwidthBurst, List<String> family) {

	public boolean canBeExit() {
		return exit.contains("accept");
	}

}
