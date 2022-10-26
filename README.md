# Arena3DwebApp for Cytoscape

## Description
The Arena3DwebApp enables users to load their 2D Cytoscape networks instantly in [Arena3D<sup>web</sup>](https://bib.fleming.gr:8084/app/arena3d). The app provides a simple interface, where the user can configure how the Cytoscape network will be transferred. 

The most important setting in the dedicated dialog is choosing which node attribute contains the layer information. It could be any numeric or string value that defines up to 18 different non-overlapping groups. In addition to the layers, Arena3DwebApp extracts the currently displayed color, size and coordinates of the nodes as well as the directionality, color, thickness, and transparency of the edges. The node label font and the network background are also transferred. The user can choose which column to use for the node description and URL that can be seen in Arena3D<sup>web</sup> as additional node information (on node right-click). If there are nodes that do not participate in any named layer, they are added to a layer named “unassigned” by default, but the user can choose to not import them in Arena3D<sup>web</sup>. The app generates a JSON file that is automatically sent to Arena3D<sup>web</sup> and gets displayed in the user’s default web browser. If users want to share the layered network or open it later, they can export the JSON file from Cytoscape and import it in Arena3D<sup>web</sup> as a session file.  

## Example
To illustrate the interoperability between Cytoscape and Arena3D<sup>web</sup>, we used [stringApp v2.0](https://apps.cytoscape.org/apps/stringapp) and Arena3DwebApp in combination from Cytoscape. Specifically, we used the “STITCH: protein/compound query” of stringApp to search for the compound “aspirin” with a confidence score cutoff of 0.7 and up to ten additional interactors (compounds or human proteins). We then retrieved functional enrichment with stringApp and added all enriched diseases, tissues and KEGG pathways to the network. To transfer the network to Arena3D<sup>web</sup>, we opened the Arena3DwebApp dialog box. We defined the layers using the column “stringdb::node type”, chose not to consider edges as directed, and set the column “stringdb::description” for node descriptions. We then submitted this three-layer network to Arena3D<sup>web</sup> for further 3D manipulations. In the enriched_term layer, we show the three categories of enriched terms from STRING in three separate neighborhoods; KEGG pathways on top, tissues in the middle and diseases on the bottom.

## Developers
Nadezhda T. Doncheva (NNF Center for Protein Research, University of Copenhagen)  
Maria Kokoli (Institute for Fundamental Biomedical Research, BSRC "Alexander Fleming")  
Vagelis Karatzas (Institute for Fundamental Biomedical Research, BSRC "Alexander Fleming")  
Fotis Baltoumas (Institute for Fundamental Biomedical Research, BSRC "Alexander Fleming")  
Lars Juhl Jensen (NNF Center for Protein Research, University of Copenhagen)  
Georgios Pavlopoulos (Institute for Fundamental Biomedical Research, BSRC "Alexander Fleming") 
