package csd.records;

import inet.ipaddr.IPAddressString;

import java.util.List;
import java.util.Objects;

public record Relay(String fingerprint, String nickname, String ip, String asn, List<String> exit, int port, int bandwidthMeasured, int bandwidthAverage, int bandwidthBurst, List<String> family) {

	public boolean canBeExit(String destinationIP) {
		for (String rule : exit) {
			String[] ruleAndIP = rule.split(" ");
			if (ruleAndIP[0].equals("reject") && new IPAddressString(ruleAndIP[1].split(":")[0]).contains(new IPAddressString(destinationIP)))
				return false;
			if (ruleAndIP[0].equals("accept"))
				return true;
		}
		return false;
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
