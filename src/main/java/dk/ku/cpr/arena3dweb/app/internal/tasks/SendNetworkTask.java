package dk.ku.cpr.arena3dweb.app.internal.tasks;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import org.cytoscape.work.Tunable;
import org.cytoscape.work.util.ListSingleSelection;
import org.json.simple.JSONObject;

import dk.ku.cpr.arena3dweb.app.internal.io.ConnectionException;
import dk.ku.cpr.arena3dweb.app.internal.io.HttpUtils;
import dk.ku.cpr.arena3dweb.app.internal.utils.ModelUtils;

// TODO: clean up 
public class SendNetworkTask extends AbstractTask implements ObservableTask {

	final private CyServiceRegistrar reg;
	private CyNetworkView netView;
	
	private static int limitUniqueAttributes = 18;	
	// private boolean doFullEncoding = true;
	
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
			initLayerColumn();
			// initLabelColumn();
			initDescrColumn();
			initURLColumn();
		}
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void run(TaskMonitor monitor) throws Exception {
		// TODO: Move the jsonNet stuff into another class to be able to use it differently, e.g. for exporting into a file
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
			String returnURL = (String) results.get("url");
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


    public <R> R getResults(Class<? extends R> type) {
		return null;
	}
    
    private void initDescrColumn() {
		Collection<CyColumn> colList = network.getDefaultNodeTable().getColumns();
		List<String> showList = new ArrayList<String>();
		for (CyColumn col : colList) {
			if (col.getType().equals(String.class)) {
				showList.add(col.getName());
			}
		}
		Collections.sort(showList);
		showList.add("");
		descrColumn = new ListSingleSelection<String>(showList);
		descrColumn.setSelectedValue("");
		if (network.getDefaultNodeTable().getColumn("stringdb::description") != null) {
			descrColumn.setSelectedValue("stringdb::description");
		} else if (network.getDefaultNodeTable().getColumn("description") != null) {
			descrColumn.setSelectedValue("description");
		}
    }

    private void initURLColumn() {
		Collection<CyColumn> colList = network.getDefaultNodeTable().getColumns();
		List<String> showList = new ArrayList<String>();
		for (CyColumn col : colList) {
			if (col.getType().equals(String.class)) {
				showList.add(col.getName());
			}
		}
		Collections.sort(showList);
		showList.add("");
		urlColumn = new ListSingleSelection<String>(showList);
		urlColumn.setSelectedValue("");
		if (network.getDefaultNodeTable().getColumn("stringdb::url") != null) {
			urlColumn.setSelectedValue("stringdb::url");
		} else if (network.getDefaultNodeTable().getColumn("url") != null) {
			urlColumn.setSelectedValue("url");
		} 
		// TODO: why would I put the name for URL?
		//else if (network.getDefaultNodeTable().getColumn("name") != null) {
		//	urlColumn.setSelectedValue("name");
		// }
    }

    private void initLayerColumn() {
		Collection<CyColumn> colList = network.getDefaultNodeTable().getColumns();
		List<CyColumn> showList = new ArrayList<CyColumn>();
		for (CyColumn col : colList) {
			Set<?> colValues = new HashSet<>();
			int numValues = network.getNodeCount();
			if (col.getType().equals(String.class)) {
				colValues = new HashSet<String>(col.getValues(String.class));
			} else if (col.getType().equals(Integer.class)) {
				colValues = new HashSet<Integer>(col.getValues(Integer.class));
			}
			if (colValues.size() != numValues && colValues.size() > 1
					&& colValues.size() <= limitUniqueAttributes) {
				showList.add(col);
			}
		}
		Collections.sort(showList, new LexicographicComparator());
		layerColumn = new ListSingleSelection<CyColumn>(showList);
		layerColumn.setSelectedValue(showList.get(0));
		// tableColumn.setSelectedValue(network.getDefaultNodeTable().getColumn("layer"));
    }
    
	class LexicographicComparator implements Comparator<CyColumn> {
	    public int compare(CyColumn a, CyColumn b) {
	        return a.getName().compareToIgnoreCase(b.getName());
	    }
	}

}
