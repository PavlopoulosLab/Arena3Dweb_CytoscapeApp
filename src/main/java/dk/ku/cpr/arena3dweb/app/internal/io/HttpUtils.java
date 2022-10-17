package dk.ku.cpr.arena3dweb.app.internal.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import org.cytoscape.io.util.StreamUtil;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class HttpUtils {

	public static String ARENA3DWEB_URL = "https://bib.fleming.gr/bib/api/arena3dweb";
	
	public static JSONObject postJSON(JSONObject jsonNetwork, CyServiceRegistrar reg) throws ConnectionException {

		// Set up our connection
		JSONObject jsonObject = new JSONObject();

		URLConnection connection = null;
		try {
			connection = executeWithRedirect(reg, ARENA3DWEB_URL, jsonNetwork);
			// addVersion(connection, jsonObject);
			InputStream entityStream = connection.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(entityStream));

			reader.mark(2097152); // Set a mark so that if we get a parse failure, we can recover the error

			JSONParser parser = new JSONParser();
			try {
				jsonObject = (JSONObject) parser.parse(reader);
				// jsonObject.put("Result", obj);
			} catch (Exception parseFailure) {
				// Get back to the start of the error
				reader.reset();
				StringBuilder errorString = new StringBuilder();
				String line;
				try {
					while ((line = reader.readLine()) != null) {
						// System.out.println(line);
						errorString.append(line);
					}
				} catch (Exception ioe) {
					// ignore
				}
				System.out.println("Exception reading JSON response from  server: \n"+ parseFailure.getMessage()+"\n Text: "+errorString);
				throw new ConnectionException("Exception reading JSON response from server: \n"+ parseFailure.getMessage());
			}
	 	} catch(UnknownHostException e) {
			e.printStackTrace();
			throw new ConnectionException("Unknown host: " + e.getMessage());
		} catch (Exception e) {
			// e.printStackTrace();
			System.out.println("Unexpected error from server: \n" + e.getMessage());
			throw new ConnectionException("Unexpected error from server: \n" + e.getMessage());
		} finally {
			// ignore
		}
		return jsonObject;
	}

	private static URLConnection executeWithRedirect(CyServiceRegistrar reg, String url, JSONObject jsonNetwork) throws Exception {
		// manager.info("POSTing JSON from "+url);
		// Get the connection from Cytoscape
		HttpURLConnection connection = (HttpURLConnection) reg.getService(StreamUtil.class).getURLConnection( new URL(url) );
		
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type","application/json");
		
		// We want to write on the stream
		connection.setDoOutput(true);
		// We want to deal with redirection ourself
		connection.setInstanceFollowRedirects(false);
		
		// We write the POST arguments
		OutputStreamWriter out = new OutputStreamWriter( connection.getOutputStream() );
		jsonNetwork.writeJSONString(out);
		out.close();
		
		// Check for redirections
		int statusCode = connection.getResponseCode();
		switch (statusCode) {
		case HttpURLConnection.HTTP_MOVED_PERM: // code 301
		case HttpURLConnection.HTTP_MOVED_TEMP: // code 302
		case HttpURLConnection.HTTP_SEE_OTHER: // code 303
			// Got a redirect.
			// Get the new location
			// manager.info("...but we were redirected to "+connection.getHeaderField("Location"));
			return executeWithRedirect(reg, connection.getHeaderField("Location"), jsonNetwork);
		case HttpURLConnection.HTTP_INTERNAL_ERROR:
		case HttpURLConnection.HTTP_BAD_REQUEST:
			readStream(connection.getErrorStream());
			return connection;
		}
		
		return connection;
	}
	
	private static String readStream(InputStream stream) throws Exception {
	    StringBuilder builder = new StringBuilder();
	    try (BufferedReader in = new BufferedReader(new InputStreamReader(stream))) {
	        String line;
	        while ((line = in.readLine()) != null) {
	            builder.append(line); // + "\r\n"(no need, json has no line breaks!)
	        }
	        in.close();
	    }
	    System.out.println("JSON error response: " + builder.toString());
	    return builder.toString();
	}

}
