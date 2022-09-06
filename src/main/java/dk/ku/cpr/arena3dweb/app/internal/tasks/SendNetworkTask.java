package dk.ku.cpr.arena3dweb.app.internal.tasks;

import java.awt.Desktop;
import java.awt.Paint;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.StringToModel;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualPropertyDependency;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.TaskMonitor.Level;
import org.cytoscape.work.util.ListSingleSelection;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import dk.ku.cpr.arena3dweb.app.internal.io.ConnectionException;
import dk.ku.cpr.arena3dweb.app.internal.io.HttpUtils;

// TODO: clean up 
public class SendNetworkTask extends AbstractTask implements ObservableTask {

	final private CyServiceRegistrar reg;
	private CyNetworkView netView;
	
	private static int limitUniqueAttributes = 10;	
	private boolean doFullEncoding = true;
	
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

	// @Tunable(description="Column to use for node labels",
	// longDescription="Select the column to use for node labels in Arena3D.",
	// exampleStringValue="name",
	// required=true)
	// public ListSingleSelection<CyColumn> labelColumn = null;
	
	@Tunable(description="Column to use for node description", 
	         longDescription="Select the column to use for node description in Arena3D.",
	         exampleStringValue="description",
	         required=false)
	public ListSingleSelection<String> descrColumn = null;

	@Tunable(description="Column to use for node URL", 
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
		// TODO: consider moving the jsonNet stuff into another class to be able to use it differently, e.g. for exporting into a file
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

		// get the visual style and locked node size property
		boolean lockedNodeSize = false;
		VisualMappingManager vmm = reg.getService(VisualMappingManager.class);
		VisualStyle netStyle = vmm.getVisualStyle(netView);
		for (VisualPropertyDependency<?> vpd: netStyle.getAllVisualPropertyDependencies()) {
			// System.out.println(vpd.getDisplayName() + " " + vpd.getIdString());			
			if (vpd.getIdString().equals("nodeSizeLocked"))
				lockedNodeSize = vpd.isDependencyEnabled();
		}

		// setup json object with basics
		JSONObject jsonNet = setupJsonNetwork(netStyle);
		
		// get the layers 
		CyColumn colLayers = layerColumn.getSelectedValue();
		String colLayerName = colLayers.getName();
		Class<?> colLayerClass = colLayers.getType();
		Set<String> layers = new HashSet<String>(); 
		if (colLayerClass.equals(String.class)) {
			Set<String> colValues = new HashSet<String>(colLayers.getValues(String.class));
			for (String colValue : colValues) {
				if ((colValue == null || colValue.equals("")) && keepUnassigned)
					layers.add(defaultLayerName);
				else 
					layers.add(colValue);
			}
		} else if (colLayerClass.equals(Integer.class)) {
			Set<Integer> colValuesInt = new HashSet<Integer>(colLayers.getValues(Integer.class));
			for (Integer colValue : colValuesInt) {
				if (colValue == null && keepUnassigned)
					layers.add(defaultLayerName);
				else
					layers.add(colValue.toString());
			}
		}
		monitor.setStatusMessage("Network will contain " + layers.size() + " layers.");
		System.out.println("Network will contain " + layers.size() + " layers.");
		// add layers to json object
		addLayersToJsonNetwork(jsonNet, layers);
		
		// go over all nodes and save info
		HashMap<CyNode, String> nodeLayerNames = new HashMap<CyNode, String>();
		JSONArray json_nodes = new JSONArray();
		Map<String, String> json_node = null;
		for (CyNode node : network.getNodeList()) {
			if (netView == null || !network.containsNode(node)) 
				continue;

			// init map for saving nodes
			json_node = new LinkedHashMap<String, String>();
			
			View<CyNode> view = netView.getNodeView(node);
			// Node name
			// System.out.println("id=" + node.getSUID());
			// System.out.println("name=" + encode(getRowFromNetOrRoot(network, node, null).get(CyNetwork.NAME, String.class)));
			String node_label = view.getVisualProperty(BasicVisualLexicon.NODE_LABEL);
			json_node.put("name", node_label);
			
			// Node layer
			String nodeLayer = "layer1";
			if (colLayerClass.equals(String.class)) {
				nodeLayer = encode(getRowFromNetOrRoot(network, node, null).get(colLayerName, String.class));
				if (nodeLayer == null || nodeLayer.equals("")) 
					nodeLayer = defaultLayerName;
			} else if (colLayerClass.equals(Integer.class)) {
				Integer nodeLayerInt = getRowFromNetOrRoot(network, node, null).get(colLayerName, Integer.class);
				if (nodeLayerInt == null)
					nodeLayer = defaultLayerName;
				nodeLayer = encode(nodeLayerInt.toString());
				if (nodeLayer.equals(""))
					nodeLayer = defaultLayerName;
			}
			// ignore node if it belongs to an unassigned layer
			if (nodeLayer.endsWith(defaultLayerName) && !keepUnassigned)
				continue;
			// otherwise add layer to node and continue
			json_node.put("layer", nodeLayer);

			// define node name for edges
			// add a cy node to arena3d node id mapping  
			nodeLayerNames.put(node, node_label + "_" + nodeLayer);
			
			// Node coordinates
			// json_node.put("position_x", "0");
			Double node_x = view.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
			json_node.put("position_y", node_x.toString());
			Double node_y = view.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
			json_node.put("position_z", node_y.toString());
			
			// Node size
			// transform node size into scale by dividing by default node size seems to work 
			double scale = 1.0;
			if (lockedNodeSize) {
				Double node_size = view.getVisualProperty(BasicVisualLexicon.NODE_SIZE);
				Double default_node_size = netStyle.getDefaultValue(BasicVisualLexicon.NODE_SIZE);
				// System.out.println("node size: " + netStyle.getVisualMappingFunction(BasicVisualLexicon.NODE_SIZE).getVisualProperty().getRange().toString());
				scale = node_size/default_node_size;
			} else {
				Double node_height = view.getVisualProperty(BasicVisualLexicon.NODE_HEIGHT);
				Double default_node_height = netStyle.getDefaultValue(BasicVisualLexicon.NODE_HEIGHT);
				Double node_width = view.getVisualProperty(BasicVisualLexicon.NODE_WIDTH);
				Double default_node_width = netStyle.getDefaultValue(BasicVisualLexicon.NODE_WIDTH);
				scale = (node_height/default_node_height + node_width/default_node_width)/2.0;
			}
			json_node.put("scale", new Double(scale).toString());
			
			// Node color
			Paint node_color = view.getVisualProperty(BasicVisualLexicon.NODE_FILL_COLOR);
			json_node.put("color", BasicVisualLexicon.NODE_FILL_COLOR.toSerializableString(node_color));

			// TODO: add a URL column to stringApp
			String url = encode(getRowFromNetOrRoot(network, node, null).get(urlColumn.getSelectedValue(), String.class));
			if (url != null && !url.equals(""))
				json_node.put("url", url);

			String descr = encode(getRowFromNetOrRoot(network, node, null).get(descrColumn.getSelectedValue(), String.class));
			if (descr != null && !descr.equals(""))
				json_node.put("descr", descr);
			
			// finally add the complete node object 
			json_nodes.add(json_node);
		}
		// add all nodes to the json object
		jsonNet.put("nodes", json_nodes);

		// go over all edges and save info
		JSONArray json_edges = new JSONArray();
		Map<String, String> json_edge = null;
		HashMap<CyEdge, Double> edgeScaleMap = getEdgeScales();
		for (CyEdge edge : network.getEdgeList()) {
			if (netView == null || !network.containsEdge(edge)) 
				continue;

			// init the edge object map
			json_edge = new LinkedHashMap<String, String>();

			// Edge source and target
			CyNode source = edge.getSource();
			CyNode target = edge.getTarget();
			if (!nodeLayerNames.containsKey(source) || !nodeLayerNames.containsKey(target))
				continue;
			
			json_edge.put("src", nodeLayerNames.get(source));
			json_edge.put("trg", nodeLayerNames.get(target));

			// Edge color and width
			if (edgeScaleMap.containsKey(edge))
				json_edge.put("opacity", new Double(edgeScaleMap.get(edge)).toString());
			
			View<CyEdge> view = netView.getEdgeView(edge);
			Paint edge_color = view.getVisualProperty(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
			json_edge.put("color", BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT.toSerializableString(edge_color));
			
			String interaction = encode(getRowFromNetOrRoot(network, edge, null).get(CyEdge.INTERACTION, String.class));
			json_edge.put("channel", interaction);
			
			// finally add the complete edge object 
			json_edges.add(json_edge);
		}
		// add all edges to the json object
		jsonNet.put("edges", json_edges);
		
		// output the json we are sending
		System.out.println(jsonNet);
		
		// Get the results
		JSONObject results;
		try {
			// results = HttpUtils.postJSON(getExampleJsonNetwork(), reg);
			results = HttpUtils.postJSON(jsonNet, reg);
		} catch (ConnectionException e) {
			e.printStackTrace();
			monitor.showMessage(Level.ERROR, "Network error: " + e.getMessage());
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
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				Runtime runtime = Runtime.getRuntime();
				try {
					runtime.exec("xdg-open " + returnURL);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}		
	}

	@ProvidesTitle
	public String getTitle() {
		return "Send current network to Arena3Dweb";
	}

	private String encode(String str) {
		// Find and replace any "magic", control, non-printable etc. characters
		// For maximum safety, everything other than printable ASCII (0x20 thru 0x7E) is converted
		// into a character entity
		String s = null;

		if (str != null) {
			StringBuilder sb = new StringBuilder(str.length());

			for (int i = 0; i < str.length(); i++) {
				char c = str.charAt(i);

				if ((c < ' ') || (c > '~')) {
					if (doFullEncoding) {
						sb.append("&#x");
						sb.append(Integer.toHexString((int) c));
						sb.append(";");
					} else {
						sb.append(c);
					}
				} else if (c == '"') {
					sb.append("&quot;");
				} else if (c == '\'') {
					sb.append("&apos;");
				} else if (c == '&') {
					sb.append("&amp;");
				} else if (c == '<') {
					sb.append("&lt;");
				} else if (c == '>') {
					sb.append("&gt;");
				} else {
					sb.append(c);
				}
			}

			s = sb.toString();
		}

		return s;
	}

	@SuppressWarnings("unchecked")
	private JSONObject setupJsonNetwork(VisualStyle netStyle) {
		Map<String, String> json_scene = new LinkedHashMap<String, String>();
		// get color from default network background color
		Paint default_net_bg_paint = netStyle.getDefaultValue(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT);
		json_scene.put("color", BasicVisualLexicon.NETWORK_BACKGROUND_PAINT.toSerializableString(default_net_bg_paint));

		// Other optional parameters to use: 
		//json_scene.put("position_x", "0");
		//json_scene.put("position_y", "0");
		//json_scene.put("scale", "0.6561");
		//json_scene.put("rotation_x", "0.261799387799149");
		//json_scene.put("rotation_y", "0.261799387799149");
		//json_scene.put("rotation_z", "0.0872664625997165");
		
		JSONObject jsonObjectNetwork = new JSONObject();
		jsonObjectNetwork.put("scene", json_scene);
		// get color from default node label color
		Paint default_node_label_color = netStyle.getDefaultValue(BasicVisualLexicon.NODE_LABEL_COLOR);
		// TODO: test this once new version is released
		jsonObjectNetwork.put("universalLabelColor", BasicVisualLexicon.NODE_LABEL_COLOR.toSerializableString(default_node_label_color));
		// use edge weight to opacity mapping
		// TODO: test this once new version is released
		jsonObjectNetwork.put("edgeOpacityByWeight", new Boolean(true));
		// Optional parameter to use
		// jsonObjectNetwork.put("direction", new Boolean(false));
		return jsonObjectNetwork;
	}
	
	@SuppressWarnings("unchecked")
	private void addLayersToJsonNetwork(JSONObject jsonObjectNetwork, Set<String> layers) {
		JSONArray json_layers = new JSONArray();
		Map<String, String> json_layer = null;
		// int x = -480;
		for (String layer : layers) {
			if (layer == null || layer.equals("")) {
				// System.out.println("ignore layer because it is null or empty");
				// TODO: double check why this happens and needs to be checked for?
				continue;
			}
			json_layer = new LinkedHashMap<String, String>();
			json_layer.put("name", layer);
			//json_layer.put("position_x", new Integer(x).toString());
			//json_layer.put("position_y", "0");
			//json_layer.put("position_z", "0");
			//json_layer.put("last_layer_scale", "1");
			//json_layer.put("rotation_x", "0");
			//json_layer.put("rotation_y", "0");
			//json_layer.put("rotation_z", "0");
			//json_layer.put("floor_current_color", "");
			//json_layer.put("geometry_parameters_width", "");
			json_layers.add(json_layer);
			// x += 480;
		}
		jsonObjectNetwork.put("layers", json_layers);
	}
	
	private HashMap<CyEdge, Double> getEdgeScales() {
		HashMap<CyEdge, Double> edgeScales = new HashMap<CyEdge, Double>();
		double maxWidth = 0.0;
		int maxOpacity = 0;
		for (CyEdge edge : network.getEdgeList()) {
			if (netView == null || !network.containsEdge(edge)) 
				continue;

			// Edge width
			View<CyEdge> view = netView.getEdgeView(edge);
			// TODO: test this with a network with different edge weights
			// take both opacity and edge tickness and combined them to one value
			Double edge_width = view.getVisualProperty(BasicVisualLexicon.EDGE_WIDTH);
			Integer edge_opacity = view.getVisualProperty(BasicVisualLexicon.EDGE_TRANSPARENCY);
			// TODO: if needed, do gamma factor correction (put the scale to the power of gamma = 2 or something else)
			double edge_scale = edge_width*edge_opacity;
			// System.out.println(edge_width + " " + edge_opacity + " " + edge_scale) ;
			edgeScales.put(edge, new Double(edge_scale));
			if (edge_width.doubleValue() > maxWidth)
				maxWidth = edge_width.doubleValue();
			if (edge_opacity.intValue() > maxOpacity)
				maxOpacity = edge_opacity.intValue(); 
		}
		for (CyEdge edge : edgeScales.keySet()) {
			double newScale = edgeScales.get(edge)/(maxOpacity*maxWidth);
			edgeScales.put(edge, new Double(newScale));
		}
		return edgeScales;
	}
	
	
	@SuppressWarnings("unchecked")
	private JSONObject getExampleJsonNetwork() {
		Map<String, String> json_scene = new LinkedHashMap<String, String>();
		json_scene.put("position_x", "0");
		json_scene.put("position_y", "0");
		json_scene.put("scale", "0.6561");
		json_scene.put("color", "#000000");
		json_scene.put("rotation_x", "0.261799387799149");
		json_scene.put("rotation_y", "0.261799387799149");
		json_scene.put("rotation_z", "0.0872664625997165");
		
		Map<String, String> json_layers_1 = new LinkedHashMap<String, String>();
		json_layers_1.put("name", "1");
		json_layers_1.put("position_x", "-480");
		json_layers_1.put("position_y", "0");
		json_layers_1.put("position_z", "0");
		json_layers_1.put("last_layer_scale", "1");
		json_layers_1.put("rotation_x", "0");
		json_layers_1.put("rotation_y", "0");
		json_layers_1.put("rotation_z", "0");
		json_layers_1.put("floor_current_color", "#777777");
		json_layers_1.put("geometry_parameters_width", "947");
		Map<String, String> json_layers_2 = new LinkedHashMap<String, String>();
		json_layers_2.put("name", "2");
		json_layers_2.put("position_x", "480");
		json_layers_2.put("position_y", "0");
		json_layers_2.put("position_z", "0");
		json_layers_2.put("last_layer_scale", "1");
		json_layers_2.put("rotation_x", "0");
		json_layers_2.put("rotation_y", "0");
		json_layers_2.put("rotation_z", "0");
		json_layers_2.put("floor_current_color", "#777777");
		json_layers_2.put("geometry_parameters_width", "947");
	    
		JSONArray json_layers = new JSONArray();
		json_layers.add(json_layers_1);
	    json_layers.add(json_layers_2);
	    
		Map<String, String> json_nodes_1 = new LinkedHashMap<String, String>();
		json_nodes_1.put("name", "A");
		json_nodes_1.put("layer", "1");
		json_nodes_1.put("position_x", "0");
		json_nodes_1.put("position_y", "-410.179206860405");
		json_nodes_1.put("position_z", "87.2109740224067");
		json_nodes_1.put("scale", "1");
		json_nodes_1.put("color", "#e41a1c");
		json_nodes_1.put("url", "");
		json_nodes_1.put("descr", "");
		Map<String, String> json_nodes_2 = new LinkedHashMap<String, String>();
		json_nodes_2.put("name", "B");
		json_nodes_2.put("layer", "1");
		json_nodes_2.put("position_x", "0");
		json_nodes_2.put("position_y", "244.693623604753");
		json_nodes_2.put("position_z", "-203.550830988035");
		json_nodes_2.put("scale", "1");
		json_nodes_2.put("color", "#e41a1c");
		json_nodes_2.put("url", "");
		json_nodes_2.put("descr", "");
		Map<String, String> json_nodes_3 = new LinkedHashMap<String, String>();
		json_nodes_3.put("name", "C");
		json_nodes_3.put("layer", "2");
		json_nodes_3.put("position_x", "0");
		json_nodes_3.put("position_y", "-10.2895227857923");
		json_nodes_3.put("position_z", "361.274295019168");
		json_nodes_3.put("scale", "1");
		json_nodes_3.put("color", "#377eb8");
		json_nodes_3.put("url", "");
		json_nodes_3.put("descr", "");

		JSONArray json_nodes = new JSONArray();
		json_nodes.add(json_nodes_1);
		json_nodes.add(json_nodes_2);
		json_nodes.add(json_nodes_3);
	    
		Map<String, String> json_edges_1 = new LinkedHashMap<String, String>();
		json_edges_1.put("src", "A_1");
		json_edges_1.put("trg", "B_1");
		json_edges_1.put("opacity", "1");
		json_edges_1.put("color", "#CFCFCF");
		json_edges_1.put("channel", "");
		Map<String, String> json_edges_2 = new LinkedHashMap<String, String>();
		json_edges_2.put("src", "A_1");
		json_edges_2.put("trg", "C_2");
		json_edges_2.put("opacity", "1");
		json_edges_2.put("color", "#CFCFCF");
		json_edges_2.put("channel", "");

		JSONArray json_edges = new JSONArray();
		json_edges.add(json_edges_1);
		json_edges.add(json_edges_2);
		
		JSONObject jsonObjectNetwork = new JSONObject();
		jsonObjectNetwork.put("scene", json_scene);
		jsonObjectNetwork.put("layers", json_layers);
		jsonObjectNetwork.put("nodes", json_nodes);
		jsonObjectNetwork.put("edges", json_edges);
		jsonObjectNetwork.put("universal_label_color", "#FFFFFF");
		jsonObjectNetwork.put("direction", new Boolean(false));
		// System.out.println(jsonObjectNetwork);
		
		return jsonObjectNetwork;
	}
	
	private String getGraphicsKey(VisualProperty<?> vp) {
		// Nodes
		if (vp.equals(BasicVisualLexicon.NODE_LABEL))
			return new String("name");
		if (vp.equals(BasicVisualLexicon.NODE_X_LOCATION))
			return new String("position_y");
		if (vp.equals(BasicVisualLexicon.NODE_Y_LOCATION))
			return new String("position_z");
		if (vp.equals(BasicVisualLexicon.NODE_FILL_COLOR))
			return new String("color");
		if (vp.equals(BasicVisualLexicon.NODE_SIZE))
			return new String("scale_x");
		
		// if (vp.equals(BasicVisualLexicon.NODE_Z_LOCATION)) return new String[]{"z"};
		// if (vp.equals(BasicVisualLexicon.NODE_WIDTH)) return new String("w");
		// if (vp.equals(BasicVisualLexicon.NODE_HEIGHT)) return new String("h");
		// if (vp.equals(BasicVisualLexicon.NODE_SHAPE)) return new String[]{"type"};
		// if (vp.equals(BasicVisualLexicon.NODE_BORDER_WIDTH)) return new String[]{"width"};
		// if (vp.equals(BasicVisualLexicon.NODE_BORDER_PAINT)) return new String[]{"outline"};

		// Edges
		if (vp.equals(BasicVisualLexicon.EDGE_WIDTH))
			return new String("opacity");
		if (vp.equals(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT))
			return new String("color");

		return new String();
	}

	protected CyRow getRowFromNetOrRoot(final CyNetwork network, final CyIdentifiable entry,
			String namespace) {
		CyRow row = null;
		try {
			row = namespace == null ? network.getRow(entry) : network.getRow(entry, namespace);
		} catch (final IllegalArgumentException e) {
			// Ignore this exception
		} catch (final RuntimeException e) {
			// monitor.error("Cannot get \"" + namespace +"\" row for entry \"" + entry + "\" in
			// network \"" + network +
			// "\".", e);
		}

		if (row == null && network instanceof CySubNetwork) {
			// Doesn't exist in subnetwork? Try to get it from the root network.
			final CyRootNetwork root = ((CySubNetwork) network).getRootNetwork();
			row = namespace == null ? root.getRow(entry) : root.getRow(entry, namespace);
		}
		return row;
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
