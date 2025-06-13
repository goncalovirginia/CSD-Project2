package csd.records;

import java.util.List;

public record Alliance(List<String> countryCodes, double trust) {

	public boolean contains(String countryCode) {
		for (String c : countryCodes)
			if (c.equals(countryCode)) return true;
		return false;
	}

}
