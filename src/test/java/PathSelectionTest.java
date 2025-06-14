import csd.PathSelection;
import csd.PathSelectionException;
import csd.records.Alliance;
import csd.records.AlphaParams;
import csd.records.Relay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PathSelectionTest {

	private final String clientIP = "193.136.122.65";
	private final String destinationIP = "185.199.111.153";
	private PathSelection pathSelection;

	@BeforeEach
	public void setUp() throws Exception {
		List<Alliance> alliances = List.of(
			new Alliance(List.of("PT", "BR", "AO", "MZ", "ST"), 0.25),
			new Alliance(List.of("US", "RU"), 0.01),
			new Alliance(List.of("PT", "CN"), 0.4),
			new Alliance(List.of("IR", "DE"), 0.8)
		);

		List<Relay> relays = List.of(
			new Relay("G1", "guard1", "5.199.134.200", "AS1", List.of("reject *:*"), 9001, 3000, 2500, 3500, List.of()), // DE
			new Relay("M1", "middle1", "202.108.35.147", "AS3", List.of("reject *:*"), 9001, 1500, 1400, 1800, List.of()), // CN
            new Relay("E1", "exit1", "185.199.109.153", "AS2", List.of("accept *:443"), 9001, 2500, 2000, 3000, List.of()) // US
        );

		InputStream geoIPStream = this.getClass().getResourceAsStream("GeoLite2-Country.mmdb");
		pathSelection = new PathSelection(alliances, relays, geoIPStream);
	}

	@Test
	public void testGuardSecurity() {
		Relay guard = new Relay("G1", "guard1", "5.199.134.200", "AS1", List.of(), 9001, 3000, 2500, 3500, List.of());
		Map<Relay, Double> scores = pathSelection.guardSecurity(clientIP, List.of(guard));

		double expectedTrust = 0.8 * 0.75;
		assertEquals(expectedTrust, scores.get(guard), 0.001);
	}

	@Test
	public void testExitSecurity() {
		Relay guard = new Relay("G1", "guard1", "103.117.124.1", "AS1", List.of("reject *:*"), 9001, 3000, 2500, 3500, List.of());
		Relay exit = new Relay("E1", "exit1", "177.192.255.38", "AS2", List.of("accept *:443"), 9001, 2500, 2000, 3000, List.of());

		double score = pathSelection.exitSecurity(clientIP, guard, exit, destinationIP);
		assertEquals(0.25, score);
	}

	@Test
	public void testSelectPath() {
		AlphaParams params = new AlphaParams(1.0, 1.0, 1.0, 1.0, 1.0);
		List<Relay> path = pathSelection.selectPath(clientIP, destinationIP, params, params);

		assertNotEquals(path.get(0).fingerprint(), path.get(1).fingerprint());
        assertNotEquals(path.get(0).fingerprint(), path.get(2).fingerprint());
		assertNotEquals(path.get(1).fingerprint(), path.get(2).fingerprint());
	}

	@Test
	public void testNoValidMiddleThrows() throws Exception {
		AlphaParams params = new AlphaParams(1.0, 1.0, 1.0, 1.0, 1.0);

        Relay relay1 = new Relay("R1", "relay1", "185.199.109.153", "AS2", List.of("accept *:443"), 9001, 1000, 900, 1200, List.of());
        Relay relay2 = new Relay("R2", "relay2", "185.199.109.154", "AS2", List.of("accept *:443"), 9001, 1000, 900, 1200, List.of());

        InputStream geoIPStream = this.getClass().getResourceAsStream("GeoLite2-Country.mmdb");
        PathSelection ps = new PathSelection(List.of(), List.of(relay1, relay2), geoIPStream);

		assertThrows(PathSelectionException.class, () -> ps.selectPath(clientIP, destinationIP, params, params));
	}

}