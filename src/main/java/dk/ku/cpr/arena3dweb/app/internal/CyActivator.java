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
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

import dk.ku.cpr.arena3dweb.app.internal.tasks.VersionTaskFactory;

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
			VersionTaskFactory versionFactory = new VersionTaskFactory(version);
			Properties versionProps = new Properties();
			versionProps.setProperty(PREFERRED_MENU, "Apps.Arena3Dweb");
			versionProps.setProperty(TITLE, "Version");
			versionProps.setProperty(MENU_GRAVITY, "1.0");
			versionProps.setProperty(IN_MENU_BAR, "true");
			versionProps.setProperty(COMMAND_NAMESPACE, "arena3dweb");
			versionProps.setProperty(COMMAND, "version");
			versionProps.setProperty(COMMAND_DESCRIPTION, "Returns the version of Arena3DwebApp");
			versionProps.setProperty(COMMAND_LONG_DESCRIPTION, "Returns the version of Arena3DwebApp.");
			versionProps.setProperty(COMMAND_SUPPORTS_JSON, "true");
			versionProps.setProperty(COMMAND_EXAMPLE_JSON, "{\"version\":\"0.1.0\"}");
			registerService(bc, versionFactory, TaskFactory.class, versionProps);
		}

		logger.info("Arena3DwebApp " + version + " initialized.");
		System.out.println("Arena3DwebApp " + version + " initialized.");
	
	}
}