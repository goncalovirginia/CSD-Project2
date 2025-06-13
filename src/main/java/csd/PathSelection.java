package csd;

import csd.records.Alliance;
import csd.records.AlphaParams;
import csd.records.Relay;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class PathSelection {

	private final List<Alliance> alliances;
	private final Map<String, Relay> relays;
	private final GeoIPWrapper geoIPWrapper;

	public PathSelection(List<Alliance> alliances, Map<String, Relay> relays, InputStream geoIPCountryCodeDB) throws Exception {
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
		List<Relay> guards = relays.values().stream().toList();
		List<Relay> exits = relays.values().stream().filter(Relay::canBeExit).toList();

		Map<Relay, Double> guardScores = new HashMap<>();
		for (Relay g : guards) {
			double score = guardSecurity(clientIP, List.of(g));
			guardScores.put(g, score);
		}
		double maxGuardScore = guardScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

		List<Relay> safeGuards = new ArrayList<>();
		List<Relay> acceptableGuards = new ArrayList<>();
		for (Relay g : guards) {
			double s = guardScores.get(g);
			if (s >= maxGuardScore * guardParams.safeUpper() && (1 - s) <= (1 - maxGuardScore) * guardParams.safeLower()) {
				safeGuards.add(g);
			} else if (s >= maxGuardScore * guardParams.acceptUpper() && (1 - s) <= (1 - maxGuardScore) * guardParams.acceptLower()) {
				acceptableGuards.add(g);
			}
		}
		List<Relay> usableGuards = !safeGuards.isEmpty() ? safeGuards : acceptableGuards;
		Relay chosenGuard = pickWeightedRandom(usableGuards);

		Map<Relay, Double> exitScores = new HashMap<>();
		for (Relay e : exits) {
			double score = exitSecurity(clientIP, chosenGuard, e, destinationIP);
			exitScores.put(e, score);
		}
		double maxExitScore = exitScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

		List<Relay> safeExits = new ArrayList<>();
		List<Relay> acceptableExits = new ArrayList<>();
		for (Relay e : exits) {
			double s = exitScores.get(e);
			if (s >= maxExitScore * exitParams.safeUpper() && (1 - s) <= (1 - maxExitScore) * exitParams.safeLower()) {
				safeExits.add(e);
			} else if (s >= maxExitScore * exitParams.acceptUpper() && (1 - s) <= (1 - maxExitScore) * exitParams.acceptLower()) {
				acceptableExits.add(e);
			}
		}
		List<Relay> usableExits = !safeExits.isEmpty() ? safeExits : acceptableExits;
		Relay chosenExit = pickWeightedRandom(usableExits);

		List<Relay> middleCandidates = relays.values().stream()
			.filter(r -> !r.family().contains(chosenGuard.fingerprint()) && !r.family().contains(chosenExit.fingerprint()))
			.collect(Collectors.toList());
		Relay chosenMiddle = pickWeightedRandom(middleCandidates);

		List<String> path = new ArrayList<>(3);
		path.add(chosenGuard.fingerprint());
		path.add(chosenMiddle.fingerprint());
		path.add(chosenExit.fingerprint());
		return path;
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
