package dk.ku.cpr.arena3dweb.app.internal.tasks;

import java.util.Arrays;
import java.util.List;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskMonitor.Level;
import org.cytoscape.work.json.JSONResult;

public class AboutTask extends AbstractTask implements ObservableTask {

	final String version;
	final String aboutURI = "https://apps.cytoscape.org/apps/Arena3DwebApp";
	CyServiceRegistrar reg;
	
	public AboutTask(final String version, CyServiceRegistrar reg) {
		this.version = version;
		this.reg = reg;
	}

	public void run(TaskMonitor monitor) {
		monitor.setTitle("Arena3DwebApp About page");
		monitor.showMessage(Level.INFO, aboutURI);
		OpenBrowser openBrowser = reg.getService(OpenBrowser.class);
		if (openBrowser != null)
			openBrowser.openURL(aboutURI);
	}

	@SuppressWarnings("unchecked")
	public <R> R getResults(Class<? extends R> type) {
		if (type.equals(String.class)) {
			String response = "About URI: "+aboutURI+"\n";
			return (R)response;
		} else if (type.equals(JSONResult.class)) {
			return (R)new JSONResult() {
				public String getJSON() { return "{\"aboutURI\":\""+aboutURI+"\"}"; }
			};
		}
		return (R)aboutURI;
	}

	@SuppressWarnings("unchecked")
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(JSONResult.class, String.class);
	}
}
