package dk.ku.cpr.arena3dweb.app.internal;

import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;
import static org.cytoscape.work.ServiceProperties.IN_MENU_BAR;
import static org.cytoscape.work.ServiceProperties.MENU_GRAVITY;
import static org.cytoscape.work.ServiceProperties.PREFERRED_MENU;
import static org.cytoscape.work.ServiceProperties.TITLE;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.cytoscape.application.CyUserLog;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.task.NetworkViewTaskFactory;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

import dk.ku.cpr.arena3dweb.app.internal.tasks.ExportNetworkTaskFactory;
import dk.ku.cpr.arena3dweb.app.internal.tasks.SendNetworkTaskFactory;
import dk.ku.cpr.arena3dweb.app.internal.tasks.AboutTaskFactory;

public class CyActivator extends AbstractCyActivator {
	String JSON_EXAMPLE = "{\"SUID\":1234}";

	public CyActivator() {
		super();
	}

	public void start(BundleContext bc) {

		// Get a handle on the CyServiceRegistrar
		CyServiceRegistrar registrar = getService(bc, CyServiceRegistrar.class);
		final Logger logger = Logger.getLogger(CyUserLog.NAME);

		// Get our version number
		Version v = bc.getBundle().getVersion();
		String version = v.toString(); // The full version

		{
			AboutTaskFactory aboutFactory = new AboutTaskFactory(version, registrar);
			Properties aboutProps = new Properties();
			// menu properties
			aboutProps.setProperty(PREFERRED_MENU, "Apps.Arena3Dweb");
			aboutProps.setProperty(TITLE, "About");
			aboutProps.setProperty(MENU_GRAVITY, "3.0");
			aboutProps.setProperty(IN_MENU_BAR, "true");
			// command properties
			aboutProps.setProperty(COMMAND_NAMESPACE, "arena3dweb");
			aboutProps.setProperty(COMMAND, "about");
			aboutProps.setProperty(COMMAND_DESCRIPTION, "Return the about URL of Arena3DwebApp.");
			aboutProps.setProperty(COMMAND_LONG_DESCRIPTION, "Returns the about URL of Arena3DwebApp.");
			aboutProps.setProperty(COMMAND_SUPPORTS_JSON, "true");
			// versionProps.setProperty(COMMAND_EXAMPLE_JSON, "{\"version\":\"1.0.0\"}");
			registerService(bc, aboutFactory, TaskFactory.class, aboutProps);
		}

		
		{
			SendNetworkTaskFactory sendNetwork = new SendNetworkTaskFactory(registrar);
			Properties props = new Properties();
			// menu properties for a network without a view 
			props.setProperty(PREFERRED_MENU, "Apps.Arena3Dweb");
			props.setProperty(TITLE, "Send network");
			props.setProperty(MENU_GRAVITY, "1.0");
			props.setProperty(IN_MENU_BAR, "true");
			registerService(bc, sendNetwork, NetworkTaskFactory.class, props);
			// menu properties for a network with a view
			Properties props2 = new Properties();
			props2.setProperty(PREFERRED_MENU, "Apps.Arena3Dweb");
			props2.setProperty(TITLE, "Send network");
			props2.setProperty(MENU_GRAVITY, "1.0");
			props2.setProperty(IN_MENU_BAR, "false");
			registerService(bc, sendNetwork, NetworkViewTaskFactory.class, props2);
			// command props
			Properties props3 = new Properties();
			props3.setProperty(COMMAND_NAMESPACE, "arena3dweb");
			props3.setProperty(COMMAND, "send");
			props3.setProperty(COMMAND_DESCRIPTION, "Send network to Arena3Dweb and output URL.");
			props3.setProperty(COMMAND_LONG_DESCRIPTION, "Send network to Arena3Dweb and output URL.");
			props3.setProperty(COMMAND_SUPPORTS_JSON, "true");
			registerService(bc, sendNetwork, TaskFactory.class, props3);
		}
		
		{
			ExportNetworkTaskFactory exportNetwork = new ExportNetworkTaskFactory(registrar);
			Properties props = new Properties();
			// menu properties for a network without a view 
			props.setProperty(PREFERRED_MENU, "Apps.Arena3Dweb");
			props.setProperty(TITLE, "Export network to file");
			props.setProperty(MENU_GRAVITY, "2.0");
			props.setProperty(IN_MENU_BAR, "true");
			registerService(bc, exportNetwork, NetworkTaskFactory.class, props);
			// menu properties for a network with a view
			Properties props2 = new Properties();
			props2.setProperty(PREFERRED_MENU, "Apps.Arena3Dweb");
			props2.setProperty(TITLE, "Export network to file");
			props2.setProperty(MENU_GRAVITY, "2.0");
			props2.setProperty(IN_MENU_BAR, "false");
			registerService(bc, exportNetwork, NetworkViewTaskFactory.class, props2);
			Properties props3 = new Properties();
			props3.setProperty(COMMAND_NAMESPACE, "arena3dweb");
			props3.setProperty(COMMAND, "export");
			props3.setProperty(COMMAND_DESCRIPTION, "Export network to Arena3Dweb session file.");
			props3.setProperty(COMMAND_LONG_DESCRIPTION, "Export network to Arena3Dweb session file.");
			props3.setProperty(COMMAND_SUPPORTS_JSON, "false");
			registerService(bc, exportNetwork, TaskFactory.class, props3);

		}

		logger.info("Arena3DwebApp " + version + " initialized.");
		System.out.println("Arena3DwebApp " + version + " initialized.");
	
	}
}