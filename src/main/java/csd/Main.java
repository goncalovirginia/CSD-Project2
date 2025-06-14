package csd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import csd.records.Alliance;
import csd.records.AlphaParams;
import csd.records.Relay;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class Main {

	public static void main(String[] args) throws Exception {
		ClassLoader classLoader = Main.class.getClassLoader();

		InputStream config = classLoader.getResourceAsStream("files.properties");
		Properties properties = new Properties();
		properties.load(config);

		InputStream clientInput = classLoader.getResourceAsStream(properties.getProperty("client.input.file"));
		InputStream torConsensus = classLoader.getResourceAsStream(properties.getProperty("tor.consensus.file"));
		InputStream geoIPCountryCodeDB = classLoader.getResourceAsStream(properties.getProperty("geo.ip.country.code.db"));
		InputStream alphaParams = classLoader.getResourceAsStream(properties.getProperty("alpha.params.file"));

		Properties alphaParamsProps = new Properties();
		alphaParamsProps.load(alphaParams);
		AlphaParams guardParams = parseAlphaParams(alphaParamsProps, "guard");
		AlphaParams exitParams = parseAlphaParams(alphaParamsProps, "exit");

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode clientInputJsonNode = objectMapper.readTree(clientInput);
		JsonNode torConsensusJsonNode = objectMapper.readTree(torConsensus);

		List<Alliance> alliances = parseAlliances(clientInputJsonNode);
		String client = clientInputJsonNode.get("Client").asText();
		String destination = clientInputJsonNode.get("Destination").asText();

		List<Relay> relays = parseRelays(torConsensusJsonNode);

		try {
			PathSelection pathSelection = new PathSelection(alliances, relays, geoIPCountryCodeDB);
			List<Relay> selectedPath = pathSelection.selectPath(client, destination, guardParams, exitParams);

			ObjectNode selectedPathJson = objectMapper.createObjectNode();
			selectedPathJson.put("guard", selectedPath.get(0).fingerprint());
			selectedPathJson.put("middle", selectedPath.get(1).fingerprint());
			selectedPathJson.put("exit", selectedPath.get(2).fingerprint());

			System.out.println(selectedPathJson.toPrettyString());
		} catch (PathSelectionException e) {
			System.out.println(e.getMessage());
		}
	}

	private static AlphaParams parseAlphaParams(Properties props, String relayType) {
		return new AlphaParams(
			Double.parseDouble(props.getProperty(relayType + ".safe_upper")),
			Double.parseDouble(props.getProperty(relayType + ".safe_lower")),
			Double.parseDouble(props.getProperty(relayType + ".accept_upper")),
			Double.parseDouble(props.getProperty(relayType + ".accept_lower")),
			Double.parseDouble(props.getProperty(relayType + ".bandwidth_frac"))
		);
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

	private static List<Relay> parseRelays(JsonNode torConsensusJsonNode) {
		List<Relay> relays = new ArrayList<>();

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
				family.add(familyJsonNodes.next().asText().substring(1));
			}

			String asn = relayJsonNode.get("asn").asText();
			List<String> exit = List.of(relayJsonNode.get("exit").asText().split(", "));

			relays.add(new Relay(fingerprint, nickname, ip, asn, exit, port, measured, average, burst, family));
		}

		return relays;
	}

}