package dk.ku.cpr.arena3dweb.app.internal.tasks;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.AbstractNetworkTaskFactory;
import org.cytoscape.task.NetworkViewTaskFactory;
import org.cytoscape.task.NodeViewTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class SendNetworkTaskFactory extends AbstractNetworkTaskFactory implements NetworkViewTaskFactory, TaskFactory{

	// NodeViewTaskFactory, 

	final CyServiceRegistrar registrar;
	
	public SendNetworkTaskFactory(final CyServiceRegistrar registrar) {
		this.registrar = registrar;
	}

	public boolean isReady() {
		return true;
	}

	public TaskIterator createTaskIterator() {
		return new TaskIterator(new SendNetworkTask(registrar, null, null));
	}

	public boolean isReady(CyNetwork net) {
		return true;
	}

	public TaskIterator createTaskIterator(CyNetwork net) {
		return new TaskIterator(new SendNetworkTask(registrar, net, null));
	}

	public boolean isReady(CyNetworkView arg0) {
		return true;
	}

	public TaskIterator createTaskIterator(CyNetworkView netView) {
		return new TaskIterator(new SendNetworkTask(registrar, netView.getModel(), netView));	
	}

}
