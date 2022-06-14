package dk.ku.cpr.arena3dweb.app.internal.tasks;

import java.awt.Paint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.util.ListSingleSelection;

public class SendNetworkTask extends AbstractTask implements ObservableTask {

	final private CyServiceRegistrar reg;
	private CyNetworkView netView;
	
	private boolean doFullEncoding = true;
	
	@Tunable(description = "Network to send", 
			longDescription = StringToModel.CY_NETWORK_LONG_DESCRIPTION, 
			exampleStringValue = StringToModel.CY_NETWORK_EXAMPLE_STRING, 
			context = "nogui", required=true)
	public CyNetwork network;

	@Tunable(description="Column to use for layers", 
	         longDescription="Select the column to use as layers in Arena3D.",
	         exampleStringValue="name",
	         required=true)
	public ListSingleSelection<CyColumn> tableColumn = null;

	
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
		
		// get the layers 
		CyColumn colLayers = tableColumn.getSelectedValue();
		String colLayerName = colLayers.getName();
		Class<?> colLayerClass = colLayers.getType();
		Set<String> layers = new HashSet<String>(); 
		if (colLayerClass.equals(String.class)) {
			layers.addAll(colLayers.getValues(String.class));
		} else if (colLayerClass.equals(Integer.class)) {
			Set<Integer> colValuesInt = new HashSet<Integer>(colLayers.getValues(Integer.class));
			for (Integer colValue : colValuesInt) {
				layers.add(colValue.toString());
			}
		}
		monitor.setStatusMessage("Network will contain " + layers.size() + " layers.");
		
		// get the visual style and locked node size property
		boolean lockedNodeSize = false;
		VisualMappingManager vmm = reg.getService(VisualMappingManager.class);
		VisualStyle netStyle = vmm.getVisualStyle(netView);
		for (VisualPropertyDependency<?> vpd: netStyle.getAllVisualPropertyDependencies()) {
			// System.out.println(vpd.getDisplayName() + " " + vpd.getIdString());			
			if (vpd.getIdString().equals("nodeSizeLocked"))
				lockedNodeSize = vpd.isDependencyEnabled();
		}

		// go over all nodes and save info
		HashMap<CyNode, String> nodeNames = new HashMap<CyNode, String>();
		for (CyNode node : network.getNodeList()) {
			if (netView == null || !network.containsNode(node)) 
				continue;

			View<CyNode> view = netView.getNodeView(node);
			// Node name
			// System.out.println("id=" + node.getSUID());
			// System.out.println("name=" + encode(getRowFromNetOrRoot(network, node, null).get(CyNetwork.NAME, String.class)));
			String node_label = view.getVisualProperty(BasicVisualLexicon.NODE_LABEL);
			System.out.println("name: " + node_label);
			nodeNames.put(node, node_label);
			
			// Node layer
			if (colLayerClass.equals(String.class))
				System.out.println("layer: " + encode(getRowFromNetOrRoot(network, node, null).get(colLayerName, String.class)));
			else if (colLayerClass.equals(Integer.class))
				System.out.println("layer: " + encode(getRowFromNetOrRoot(network, node, null).get(colLayerName, Integer.class).toString()));
			
			// Node coordinates
			Double node_x = view.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
			System.out.println("position_y: " + node_x);
			Double node_y = view.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
			System.out.println("position_z: " + node_y);
			
			// Node size
			if (lockedNodeSize) {
				Double node_size = view.getVisualProperty(BasicVisualLexicon.NODE_SIZE);
				// Double default_node_size = BasicVisualLexicon.NODE_SIZE.getDefault();
				Double default_node_size = netStyle.getDefaultValue(BasicVisualLexicon.NODE_SIZE);
				System.out.println("scale_x: " + node_size/default_node_size);
			} else {
				Double node_height = view.getVisualProperty(BasicVisualLexicon.NODE_HEIGHT);
				Double default_node_height = netStyle.getDefaultValue(BasicVisualLexicon.NODE_HEIGHT);
				Double node_width = view.getVisualProperty(BasicVisualLexicon.NODE_WIDTH);
				Double default_node_width = netStyle.getDefaultValue(BasicVisualLexicon.NODE_WIDTH);
				System.out.println("scale_x: " + (node_height/default_node_height + node_width/default_node_width)/2);
			}
			
			// Node color
			Paint node_color = view.getVisualProperty(BasicVisualLexicon.NODE_FILL_COLOR);
			System.out.println("color: " + BasicVisualLexicon.NODE_FILL_COLOR.toSerializableString(node_color));
			// String hex = "#"+Integer.toHexString(Color.decode(node_color.toString()).getRGB()).substring(2);

			System.out.println("url: ");
			System.out.println("descr: ");
			
			// for (VisualProperty<?> vp : visualProperties) {
			//	Object value = view.getVisualProperty(vp);
			//	if (value == null)
			//		continue;
			//	// System.out.println(vp.getDisplayName() + ": " + vp.getDefault() + ", " + value);
			//	final String key = getGraphicsKey(vp);
			//	if (key != null && key.length() > 0) {
			//		System.out.println(key + "=" + quote(value.toString()));
			//	}
			//}
		}
		// go over all edges and save info
		for (CyEdge edge : network.getEdgeList()) {
			if (netView == null || !network.containsEdge(edge)) 
				continue;

			// System.out.println("name: " + encode(getRowFromNetOrRoot(network, edge, null).get(CyNetwork.NAME, String.class)));
			// Edge source and target
			CyNode source = edge.getSource();
			System.out.println("src: " + nodeNames.get(source));
			CyNode target = edge.getTarget();
			System.out.println("trg: " + nodeNames.get(target));

			// Edge color and width
			View<CyEdge> view = netView.getEdgeView(edge);
			// TODO: figure out ow to change edge width into an opacity value
			Double edge_width = view.getVisualProperty(BasicVisualLexicon.EDGE_WIDTH);
			Double default_edge_width = netStyle.getDefaultValue(BasicVisualLexicon.EDGE_WIDTH);
			// System.out.println("opacity=" + edge_width/default_edge_width);
			System.out.println("width: " + edge_width);
			Paint edge_color = view.getVisualProperty(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
			System.out.println("color: " + BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT.toSerializableString(edge_color));
			String interaction = encode(getRowFromNetOrRoot(network, edge, null).get(CyEdge.INTERACTION, String.class));
			System.out.println("channel: " + interaction);
		}
		
//		JSONResult res = () -> {
//			String result = "{";
//			if (enrichmentTable != null)
//				result += "\"EnrichmentTable\": "+enrichmentTable.getSUID();
//			if (ppiSummary != null) {
//				result = addResult(result, ModelUtils.NET_PPI_ENRICHMENT);
//			}
//			result += "}";

	}

	private String quote(final String str) {
		return '"' + encode(str) + '"';
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

    private void initLayerColumn() {
		Collection<CyColumn> colList = network.getDefaultNodeTable().getColumns();
		List<CyColumn> showList = new ArrayList<CyColumn>();
		for (CyColumn col : colList) {
			// System.out.println(col.getName());
			Set<?> colValues = new HashSet();
			if (col.getType().equals(String.class)) {
				colValues = new HashSet<String>(col.getValues(String.class));
			} else if (col.getType().equals(Integer.class)) {
				colValues = new HashSet<Integer>(col.getValues(Integer.class));
			}
			// TODO: filter for empty strings
			// System.out.println(colValues.size());
			if (colValues.size() > 1 && colValues.size() <= 5) {
					showList.add(col);
			}
		}
		tableColumn = new ListSingleSelection<CyColumn>(showList);
		tableColumn.setSelectedValue(showList.get(0));
		// tableColumn.setSelectedValue(network.getDefaultNodeTable().getColumn("layer"));
    }
    
}
