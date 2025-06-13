package csd;

import csd.records.Alliance;
import csd.records.AlphaParams;
import csd.records.Relay;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class PathSelection {

	private final List<Alliance> alliances;
	private final List<Relay> relays;
	private final GeoIPWrapper geoIPWrapper;

	public PathSelection(List<Alliance> alliances, List<Relay> relays, InputStream geoIPCountryCodeDB) throws Exception {
		this.alliances = alliances;
		this.relays = relays;
		this.geoIPWrapper = new GeoIPWrapper(geoIPCountryCodeDB);
	}

	public double guardSecurity(String clientIP, List<Relay> guards) {
		String clientCountryCode = geoIPWrapper.getCountryCode(clientIP);
		Set<String> clientAllies = getAlliedCountries(clientCountryCode);
		List<Double> scores = new ArrayList<>();

		for (Relay guard : guards) {
			String guardCountryCode = geoIPWrapper.getCountryCode(guard.ip());
			Set<String> guardAllies = getAlliedCountries(guardCountryCode);
			double guardCountryTrust = getCountryTrust(guardCountryCode);

			Set<String> intersectingCountryCodes = new HashSet<>(clientAllies);
			intersectingCountryCodes.retainAll(guardAllies);

			if (intersectingCountryCodes.isEmpty()) guardCountryTrust *= 0.5;

			scores.add(guardCountryTrust);
		}

		return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
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

	public List<String> selectPath(String clientIP, String destinationIP, AlphaParams guardParams, AlphaParams exitParams) {
		Map<Relay, Double> guardScores = new HashMap<>();
		for (Relay g : relays)
			guardScores.put(g, guardSecurity(clientIP, List.of(g)));
		Relay chosenGuard = sortAndPickRelay(guardParams, guardScores);

		Map<Relay, Double> exitScores = new HashMap<>();
		for (Relay e : relays.stream().filter(Relay::canBeExit).toList())
			exitScores.put(e, exitSecurity(clientIP, chosenGuard, e, destinationIP));
		Relay chosenExit = sortAndPickRelay(exitParams, exitScores);

		List<Relay> middleCandidates = relays.stream()
			.filter(r -> !r.family().contains(chosenGuard.fingerprint()) && !r.family().contains(chosenExit.fingerprint()))
			.collect(Collectors.toList());
		Relay chosenMiddle = pickWeightedRandom(middleCandidates);

		List<String> path = new ArrayList<>(3);
		path.add(chosenGuard.fingerprint());
		path.add(chosenMiddle.fingerprint());
		path.add(chosenExit.fingerprint());
		return path;
	}

	private Relay sortAndPickRelay(AlphaParams alphaParams, Map<Relay, Double> relayScores) {
		double maxRelayScore = relayScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
		List<Relay> safeRelays = new ArrayList<>();
		List<Relay> acceptableRelays = new ArrayList<>();

		for (Map.Entry<Relay, Double> entry : relayScores.entrySet()) {
			double s = entry.getValue();
			if (s >= maxRelayScore * alphaParams.safeUpper() && (1 - s) <= (1 - maxRelayScore) * alphaParams.safeLower()) {
				safeRelays.add(entry.getKey());
			} else if (s >= maxRelayScore * alphaParams.acceptUpper() && (1 - s) <= (1 - maxRelayScore) * alphaParams.acceptLower()) {
				acceptableRelays.add(entry.getKey());
			}
		}

		List<Relay> usableRelays = !safeRelays.isEmpty() ? safeRelays : acceptableRelays;
		return pickWeightedRandom(usableRelays);
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

}
