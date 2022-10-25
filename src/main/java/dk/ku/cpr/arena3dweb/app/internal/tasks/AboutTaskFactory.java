package dk.ku.cpr.arena3dweb.app.internal.tasks;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class AboutTaskFactory extends AbstractTaskFactory implements TaskFactory {

	final String version;
	final CyServiceRegistrar reg;
	public AboutTaskFactory(final String version, final CyServiceRegistrar reg) {
			this.version = version;
			this.reg = reg;
	}

	public boolean isReady() {
		return true;
	}

	public TaskIterator createTaskIterator() {
		return new TaskIterator(new AboutTask(version, reg));
	}
}
