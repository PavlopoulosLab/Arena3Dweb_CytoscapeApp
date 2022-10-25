package dk.ku.cpr.arena3dweb.app.internal.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

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
import org.cytoscape.work.Tunable;
import org.cytoscape.work.TaskMonitor.Level;
import org.cytoscape.work.util.ListSingleSelection;
import org.json.simple.JSONObject;

import dk.ku.cpr.arena3dweb.app.internal.utils.ModelUtils;

public class ExportNetworkTask extends AbstractTask implements ObservableTask {

	final private CyServiceRegistrar reg;
	private CyNetworkView netView;
	
	private static int limitUniqueAttributes = 18;
	
	@Tunable(description = "Network to send", 
			longDescription = StringToModel.CY_NETWORK_LONG_DESCRIPTION, 
			exampleStringValue = StringToModel.CY_NETWORK_EXAMPLE_STRING, 
			context = "nogui", required=true)
	public CyNetwork network;

	@Tunable(description="File to save network to", params = "input=false;fileCategory=network",
	         longDescription="Name of file to save the network to.",
	         exampleStringValue="network.json",
	         required=true)
	public File jsonFile = null;

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

	
	public ExportNetworkTask(CyServiceRegistrar reg, CyNetwork net, CyNetworkView netView) {
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
		monitor.setTitle("Export network as Arena3Dweb session file");
		// check if we have a network
		if (network == null) {
			monitor.showMessage(TaskMonitor.Level.WARN, "No network to export");
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

		if (jsonFile == null) {
			monitor.showMessage(TaskMonitor.Level.WARN, "No file to export to.");
		}
		File file = jsonFile;
		if (file.getName().endsWith(".json")) {
		    // filename is OK as-is
		} else {
		    file = new File(file.toString() + ".json");  // append .json
		}
		
		CyColumn colLayers = layerColumn.getSelectedValue();
		String colURL = urlColumn.getSelectedValue();
		String colDescr = descrColumn.getSelectedValue();
		
		JSONObject jsonNet = ModelUtils.getJSONNetwork(reg, network, netView, colLayers, defaultLayerName, keepUnassigned, colURL, colDescr, directed);
		if (jsonNet == null) {
			return;
		}
		
		// Export the network
		System.out.println("Export network to " + file.getAbsolutePath());
		monitor.showMessage(TaskMonitor.Level.INFO, "Export network to " + file.getAbsolutePath());

		FileWriter fw = null;
		try {
			// Constructs a FileWriter given a file name, using the platform's default charset
			fw = new FileWriter(file);
			fw.write(jsonNet.toJSONString());
		} catch (IOException e) {
			// e.printStackTrace();
			monitor.showMessage(Level.ERROR, "Encountered error: " + e.getMessage());
		} finally {
			try {
				fw.flush();
				fw.close();
			} catch (IOException e) {
				// e.printStackTrace();
				monitor.showMessage(Level.ERROR, "Encountered error: " + e.getMessage());
			}
		}
	}

	@ProvidesTitle
	public String getTitle() {
		return "Export network as Arena3Dweb session file";
	}


    public <R> R getResults(Class<? extends R> type) {
		return null;
	}
    
}
