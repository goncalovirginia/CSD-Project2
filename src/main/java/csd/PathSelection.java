package csd;

import csd.records.Alliance;
import csd.records.AlphaParams;
import csd.records.Relay;
import csd.util.GeoIPWrapper;

import java.io.InputStream;
import java.util.*;

public class PathSelection {

	private static final double NON_INTERSECTING_GUARD_PENALTY = 0.75;

	private final List<Alliance> alliances;
	private final List<Relay> relays;
	private final GeoIPWrapper geoIPWrapper;

	public PathSelection(List<Alliance> alliances, List<Relay> relays, InputStream geoIPCountryCodeDB) throws Exception {
		this.alliances = alliances;
		this.relays = relays.stream().filter(r -> r.bandwidthMeasured() > 0).toList();
		this.geoIPWrapper = new GeoIPWrapper(geoIPCountryCodeDB);
	}

	public Map<Relay, Double> guardSecurity(String clientIP, List<Relay> guards) {
		String clientCountryCode = geoIPWrapper.getCountryCode(clientIP);
		Set<String> clientAllies = getAlliedCountries(clientCountryCode);
		Map<Relay, Double> guardScores = new HashMap<>();

		for (Relay guard : guards) {
			String guardCountryCode = geoIPWrapper.getCountryCode(guard.ip());
			Set<String> guardAllies = getAlliedCountries(guardCountryCode);
			double guardTrust = getCountryTrust(guardCountryCode);

			Set<String> intersectingCountryCodes = new HashSet<>(clientAllies);
			intersectingCountryCodes.retainAll(guardAllies);

			if (intersectingCountryCodes.isEmpty()) guardTrust *= NON_INTERSECTING_GUARD_PENALTY;

			guardScores.put(guard, guardTrust);
		}

		return guardScores;
	}

	public double exitSecurity(String clientIP, Relay guard, Relay exit, String destinationIP) {
		String clientCountry = geoIPWrapper.getCountryCode(clientIP);
		String guardCountry = geoIPWrapper.getCountryCode(guard.ip());
		String exitCountry = geoIPWrapper.getCountryCode(exit.ip());
		String destCountry = geoIPWrapper.getCountryCode(destinationIP);

		Set<String> guardAdversaries = new HashSet<>();
		guardAdversaries.addAll(getAlliedCountries(clientCountry));
		guardAdversaries.addAll(getAlliedCountries(guardCountry));

		Set<String> exitAdversaries = new HashSet<>();
		exitAdversaries.addAll(getAlliedCountries(destCountry));
		exitAdversaries.addAll(getAlliedCountries(exitCountry));

		Set<String> sharedAdversaries = new HashSet<>(guardAdversaries);
		sharedAdversaries.retainAll(exitAdversaries);

		double minTrust = 1.0;
		for (String adversary : sharedAdversaries) {
			minTrust = Math.min(minTrust, getCountryTrust(adversary));
		}

		return minTrust;
	}

	public List<Relay> selectPath(String clientIP, String destinationIP, AlphaParams guardParams, AlphaParams exitParams) {
		for (Relay guardCandidate : rankGuardRelays(clientIP, guardParams)) {
			for (Relay exitCandidate : rankExitRelays(clientIP, guardCandidate, destinationIP, exitParams)) {
				List<Relay> middleCandidates = relays.stream().filter(r -> !r.belongsToSameFamily(guardCandidate) && !r.belongsToSameFamily(exitCandidate)).toList();

				if (!middleCandidates.isEmpty()) {
					Relay chosenMiddle = pickWeightedRandom(middleCandidates);
					return List.of(guardCandidate, chosenMiddle, exitCandidate);
				}
			}
		}

		throw new PathSelectionException("No valid path could be constructed.");
	}

	private List<Relay> rankGuardRelays(String clientIP, AlphaParams params) {
		Map<Relay, Double> guardScores = guardSecurity(clientIP, relays);
		return sortRelaysByScore(guardScores, params);
	}

	private List<Relay> rankExitRelays(String clientIP, Relay guard, String destinationIP, AlphaParams params) {
		Map<Relay, Double> exitScores = new HashMap<>();
		for (Relay r : relays)
			if (r.canBeExit(destinationIP) && !r.belongsToSameFamily(guard))
				exitScores.put(r, exitSecurity(clientIP, guard, r, destinationIP));

		return sortRelaysByScore(exitScores, params);
	}

	private List<Relay> sortRelaysByScore(Map<Relay, Double> relayScores, AlphaParams alphaParams) {
		double maxScore = relayScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
		List<Relay> safe = new ArrayList<>();
		List<Relay> acceptable = new ArrayList<>();

		for (Map.Entry<Relay, Double> entry : relayScores.entrySet()) {
			double s = entry.getValue();
			if (s >= maxScore * alphaParams.safeUpper() && (1 - s) <= (1 - maxScore) * alphaParams.safeLower())
				safe.add(entry.getKey());
			else if (s >= maxScore * alphaParams.acceptUpper() && (1 - s) <= (1 - maxScore) * alphaParams.acceptLower())
				acceptable.add(entry.getKey());
		}

		List<Relay> shuffledSafe = bandwidthWeightedShuffle(safe);
		List<Relay> shuffledAcceptable = bandwidthWeightedShuffle(acceptable);

		List<Relay> result = new ArrayList<>(shuffledSafe);
		result.addAll(shuffledAcceptable);
		return result;
	}

	private double getCountryTrust(String countryCode) {
		double maxTrust = 0.0;
		for (Alliance alliance : alliances)
			if (alliance.contains(countryCode))
				maxTrust = Math.max(maxTrust, alliance.trust());
		return maxTrust;
	}

	private Set<String> getAlliedCountries(String countryCode) {
		Set<String> allies = new HashSet<>();
		for (Alliance alliance : alliances)
			if (alliance.contains(countryCode))
				allies.addAll(alliance.countryCodes());
		return allies;
	}

	private Relay pickWeightedRandom(List<Relay> relays) {
		double total = relays.stream().mapToDouble(Relay::bandwidthMeasured).sum();
		double rnd = Math.random() * total;
		double cumulative = 0.0;
		for (Relay r : relays) {
			cumulative += r.bandwidthMeasured();
			if (rnd <= cumulative) return r;
		}
		return relays.getLast();
	}

	private List<Relay> bandwidthWeightedShuffle(List<Relay> relays) {
		List<Relay> toBeShuffled = new ArrayList<>(relays), shuffled = new ArrayList<>();

		while (!toBeShuffled.isEmpty()) {
			Relay chosen = pickWeightedRandom(toBeShuffled);
			shuffled.add(chosen);
			toBeShuffled.remove(chosen);
		}

		return shuffled;
	}

}
