package dk.ku.cpr.arena3dweb.app.internal.tasks;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class VersionTaskFactory extends AbstractTaskFactory implements TaskFactory {

	final String version;
	public VersionTaskFactory(final String version) {
			this.version = version;
	}

	public boolean isReady() {
		return true;
	}

	public TaskIterator createTaskIterator() {
		return new TaskIterator(new VersionTask(version));
	}
}
