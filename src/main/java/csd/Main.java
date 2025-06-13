package csd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import csd.records.Alliance;
import csd.records.Relay;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Main {

	public static void main(String[] args) throws Exception {
		ClassLoader classLoader = Main.class.getClassLoader();

		InputStream config = classLoader.getResourceAsStream("config.properties");
		Properties properties = new Properties();
		properties.load(config);

		InputStream clientInput = classLoader.getResourceAsStream(properties.getProperty("client.input.file.path"));
		InputStream torConsensus = classLoader.getResourceAsStream(properties.getProperty("tor.consensus.file.path"));
		InputStream geoIPCountryCodeDB = classLoader.getResourceAsStream(properties.getProperty("geo.ip.country.code.db"));

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode clientInputJsonNode = objectMapper.readTree(clientInput);
		JsonNode torConsensusJsonNode = objectMapper.readTree(torConsensus);

		List<Alliance> alliances = parseAlliances(clientInputJsonNode);
		String client = clientInputJsonNode.get("Client").asText();
		String destination = clientInputJsonNode.get("Destination").asText();

		Map<String, Relay> relays = parseRelays(torConsensusJsonNode);

		PathSelection pathSelection = new PathSelection(alliances, relays, geoIPCountryCodeDB);
	}

	private static List<Alliance> parseAlliances(JsonNode clientInputJsonNode) {
		List<Alliance> alliances = new ArrayList<>();

		Iterator<JsonNode> alliancesIterator = clientInputJsonNode.get("Alliances").elements();

		while (alliancesIterator.hasNext()) {
			JsonNode allianceJsonNode = alliancesIterator.next();

			List<String> countryCodes = new ArrayList<>();

			Iterator<JsonNode> countriesIterator = allianceJsonNode.get("countries").elements();
			while (countriesIterator.hasNext()) {
				String countryCode = countriesIterator.next().asText();
				countryCodes.add(countryCode);
			}
			double trust = allianceJsonNode.get("trust").asDouble();

			alliances.add(new Alliance(countryCodes, trust));
		}

		return alliances;
	}

	private static Map<String, Relay> parseRelays(JsonNode torConsensusJsonNode) {
		Map<String, Relay> relays = new HashMap<>();

		Iterator<JsonNode> relayJsonNodes = torConsensusJsonNode.elements();
		while (relayJsonNodes.hasNext()) {
			JsonNode relayJsonNode = relayJsonNodes.next();

			String fingerprint = relayJsonNode.get("fingerprint").asText();
			String nickname = relayJsonNode.get("nickname").asText();
			String ip = relayJsonNode.get("ip").asText();
			int port = relayJsonNode.get("port").asInt();

			JsonNode bandwidthNode = relayJsonNode.get("bandwidth");
			int measured = bandwidthNode.get("measured").asInt();
			int average = bandwidthNode.get("average").asInt();
			int burst = bandwidthNode.get("burst").asInt();

			List<String> family = new ArrayList<>();
			Iterator<JsonNode> familyJsonNodes = relayJsonNode.get("family").elements();
			while (familyJsonNodes.hasNext()) {
				family.add(familyJsonNodes.next().asText());
			}

			String asn = relayJsonNode.get("asn").asText();
			String exit = relayJsonNode.get("exit").asText();

			relays.put(ip, new Relay(fingerprint, nickname, ip, asn, exit, port, measured, average, burst, family));
		}

		return relays;
	}

}