package csd.records;

import java.util.List;
import java.util.Objects;

public record Relay(String fingerprint, String nickname, String ip, String asn, String exit, int port, int bandwidthMeasured, int bandwidthAverage, int bandwidthBurst, List<String> family) {

	public boolean canBeExit() {
		return exit.contains("accept");
	}

	public boolean belongsToSameFamily(Relay other) {
		return this.fingerprint.equals(other.fingerprint) ||
			this.family.contains(other.fingerprint) || other.family.contains(this.fingerprint);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Relay relay = (Relay) o;
		return fingerprint.equals(relay.fingerprint);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fingerprint);
	}

}
