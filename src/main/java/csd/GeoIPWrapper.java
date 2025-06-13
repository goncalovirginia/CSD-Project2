package csd;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;

import java.io.InputStream;
import java.net.InetAddress;

public class GeoIPWrapper {

	private final DatabaseReader reader;

	public GeoIPWrapper(InputStream dbInputStream) throws Exception {
		this.reader = new DatabaseReader.Builder(dbInputStream).build();
	}

	public String getCountryCode(String ip) {
		try {
			InetAddress ipAddress = InetAddress.getByName(ip);
			CountryResponse response = reader.country(ipAddress);
			return response.getCountry().getIsoCode();
		} catch (Exception e) {
			return null;
		}
	}

}
