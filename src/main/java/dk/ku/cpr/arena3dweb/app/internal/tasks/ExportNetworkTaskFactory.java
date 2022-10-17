package dk.ku.cpr.arena3dweb.app.internal.tasks;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.AbstractNetworkTaskFactory;
import org.cytoscape.task.NetworkViewTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class ExportNetworkTaskFactory extends AbstractNetworkTaskFactory implements NetworkViewTaskFactory, TaskFactory{

	final CyServiceRegistrar registrar;
	
	public ExportNetworkTaskFactory(final CyServiceRegistrar registrar) {
		this.registrar = registrar;
	}

	public boolean isReady() {
		return true;
	}

	public TaskIterator createTaskIterator() {
		return new TaskIterator(new ExportNetworkTask(registrar, null, null));
	}

	public boolean isReady(CyNetwork net) {
		return true;
	}

	public TaskIterator createTaskIterator(CyNetwork net) {
		return new TaskIterator(new ExportNetworkTask(registrar, net, null));
	}

	public boolean isReady(CyNetworkView arg0) {
		return true;
	}

	public TaskIterator createTaskIterator(CyNetworkView netView) {
		return new TaskIterator(new ExportNetworkTask(registrar, netView.getModel(), netView));	
	}

}
