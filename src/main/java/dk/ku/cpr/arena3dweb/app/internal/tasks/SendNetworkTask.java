package dk.ku.cpr.arena3dweb.app.internal.tasks;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.StringToModel;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskMonitor.Level;
import org.cytoscape.work.json.JSONResult;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.util.ListSingleSelection;
import org.json.simple.JSONObject;

import dk.ku.cpr.arena3dweb.app.internal.io.ConnectionException;
import dk.ku.cpr.arena3dweb.app.internal.io.HttpUtils;
import dk.ku.cpr.arena3dweb.app.internal.utils.ModelUtils;

public class SendNetworkTask extends AbstractTask implements ObservableTask {

	final private CyServiceRegistrar reg;
	private CyNetworkView netView;
	private String returnURL;
	
	private static int limitUniqueAttributes = 18;	
	
	@Tunable(description = "Network to send", 
			longDescription = StringToModel.CY_NETWORK_LONG_DESCRIPTION, 
			exampleStringValue = StringToModel.CY_NETWORK_EXAMPLE_STRING, 
			context = "nogui", required=true)
	public CyNetwork network;

	@Tunable(description="Column to use for layers", 
	         longDescription="Select the column to use as layers in Arena3D.",
	         exampleStringValue="layer",
	         required=true)
	public ListSingleSelection<CyColumn> layerColumn = null;

	@Tunable(description="Consider edges as directed", 
	         longDescription="Option to set edge directionality.",
	         exampleStringValue="false",
	         required=false)
	public boolean directed = false;

	@Tunable(description="Column to use for node descriptions", 
	         longDescription="Select the column to use for node description in Arena3D.",
	         exampleStringValue="description",
	         required=false)
	public ListSingleSelection<String> descrColumn = null;

	@Tunable(description="Column to use for node URLs", 
	         longDescription="Select the column to use for node URL in Arena3D.",
	         exampleStringValue="url",
	         required=false)
	public ListSingleSelection<String> urlColumn = null;
	
	@Tunable(description="Keep unassigned nodes in a layer", 
	         longDescription="Option to choose to keep nodes not assigned to a layer in an extra layer.",
	         groups={"Advanced"}, params="displayState=collapsed",
	         exampleStringValue="true",
	         required=false)
	public boolean keepUnassigned = true;

	@Tunable(description="Layer name for unassigned nodes", 
	         longDescription="This default name will be used for the layer that contains nodes without.",
	         groups={"Advanced"}, params="displayState=collapsed",
	         exampleStringValue="unassigned",
	         required=false)
	public String defaultLayerName = "unassigned";

	
	public SendNetworkTask(CyServiceRegistrar reg, CyNetwork net, CyNetworkView netView) {
		this.reg = reg;
		if (net != null)
			network = net;
		this.netView = netView;
		// Make sure we have a network.  This should only happen at this point if we're coming in
		// via a command
		if (network == null)
			network = reg.getService(CyApplicationManager.class).getCurrentNetwork();
		
		if (network != null) {
			layerColumn = ModelUtils.initLayerColumn(network, layerColumn, limitUniqueAttributes);
			// initLabelColumn();
			descrColumn = ModelUtils.initDescrColumn(network, descrColumn);
			urlColumn = ModelUtils.initURLColumn(network, urlColumn);
		}
	}
	
	@Override
	public void run(TaskMonitor monitor) throws Exception {
		monitor.setTitle("Send network to Arena3D");
		// check if we have a network
		if (network == null) {
			monitor.showMessage(TaskMonitor.Level.WARN, "No network to send");
			return;
		}
		
		// see if we have a view
		if (netView == null) {
			Collection<CyNetworkView> views = reg.getService(CyNetworkViewManager.class).getNetworkViews(network);
			for (CyNetworkView view: views) {
				if (view.getRendererId().equals("org.cytoscape.ding")) {
					netView = view;
					break;
				}
			}
		}

		CyColumn colLayers = layerColumn.getSelectedValue();
		String colURL = urlColumn.getSelectedValue();
		String colDescr = descrColumn.getSelectedValue();
		
		JSONObject jsonNet = ModelUtils.getJSONNetwork(reg, network, netView, colLayers, defaultLayerName, keepUnassigned, colURL, colDescr, directed);
		if (jsonNet == null) {
			return;
		}
		
		// Get the results
		JSONObject results;
		try {
			// results = HttpUtils.postJSON(getExampleJsonNetwork(), reg);
			results = HttpUtils.postJSON(jsonNet, reg);
		} catch (ConnectionException e) {
			//e.printStackTrace();
			monitor.showMessage(Level.ERROR, "Encountered network error: " + e.getMessage());
			return;
		}
		
		if (results != null && results.containsKey("url")) {
			returnURL = (String) results.get("url");
			System.out.println("Succesfully sent network to Arena3dWeb: " + returnURL);
			monitor.showMessage(Level.INFO, "Succesfully sent network to Arena3dWeb: " + returnURL);
			if (Desktop.isDesktopSupported()) {
				Desktop desktop = Desktop.getDesktop();
				try {
					desktop.browse(new URI(returnURL));
				} catch (IOException | URISyntaxException e) {
					// e.printStackTrace();
					monitor.showMessage(Level.ERROR, "Encountered error: " + e.getMessage());
				}
			} else {
				Runtime runtime = Runtime.getRuntime();
				try {
					runtime.exec("xdg-open " + returnURL);
				} catch (IOException e) {
					// e.printStackTrace();
					monitor.showMessage(Level.ERROR, "Encountered error: " + e.getMessage());
				}
			}

		}		
	}

	@ProvidesTitle
	public String getTitle() {
		return "Send current network to Arena3Dweb";
	}


	@SuppressWarnings("unchecked")
	public <R> R getResults(Class<? extends R> type) {
		if (type.equals(String.class)) {
			String response = "Network URI: "+returnURL+"\n";
			return (R)response;
		} else if (type.equals(JSONResult.class)) {
			return (R)new JSONResult() {
				public String getJSON() { return "{\"networkURI\":\""+returnURL+"\"}"; }
			};
		}
		return (R)returnURL;
	}

	@SuppressWarnings("unchecked")
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(JSONResult.class, String.class);
	}    
}
