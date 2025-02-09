/*
 * SchemaTree.java
 *
 * Copyright (C) 2002-2017 Takis Diakoumis
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.executequery.gui.browser.tree;

import org.executequery.GUIUtilities;
import org.executequery.components.table.BrowserTreeCellRenderer;
import org.executequery.databasemediators.QueryTypes;
import org.executequery.databasemediators.spi.DefaultStatementExecutor;
import org.executequery.databaseobjects.NamedObject;
import org.executequery.gui.browser.BrowserConstants;
import org.executequery.gui.browser.ConnectionsTreePanel;
import org.executequery.gui.browser.depend.DependPanel;
import org.executequery.gui.browser.nodes.ConnectionsFolderNode;
import org.executequery.gui.browser.nodes.DatabaseHostNode;
import org.executequery.gui.browser.nodes.DatabaseObjectNode;
import org.executequery.gui.browser.nodes.RootDatabaseObjectNode;
import org.executequery.sql.SqlStatementResult;
import org.executequery.util.ThreadUtils;
import org.executequery.util.UserProperties;
import org.underworldlabs.swing.tree.DynamicTree;
import org.underworldlabs.util.MiscUtils;
import org.underworldlabs.util.SystemProperties;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Takis Diakoumis
 */
public class SchemaTree extends DynamicTree
        implements TreeExpansionListener,
        TreeSelectionListener, MouseListener, KeyListener {

    private final TreePanel panel;

    public SchemaTree(DefaultMutableTreeNode root, TreePanel panel) {

        super(root);
        this.panel = panel;

        addTreeSelectionListener(this);
        addTreeExpansionListener(this);
        addMouseListener(this);
        addKeyListener(this);


        DefaultTreeCellRenderer renderer = new BrowserTreeCellRenderer(loadIcons());
        setCellRenderer(renderer);

        setEditable(!UserProperties.getInstance().getBooleanProperty("browser.double-click.to.connect"));
        setCellEditor(new ConnectionTreeCellEditor(this, renderer));

        setShowsRootHandles(true);
        setDragEnabled(true);
        setAutoscrolls(true);

        setDropMode(DropMode.ON_OR_INSERT);
        setTransferHandler(new TreeTransferHandler());
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        int h = Integer.parseInt(SystemProperties.getProperty("user", "treeconnection.row.height"));
        if (h == 16)
            setRowHeight(17);
        setRowHeight(h);

    }

    private Map<String, Icon> loadIcons() {

        Map<String, Icon> icons = new HashMap<String, Icon>();
        for (int i = 0; i < BrowserConstants.NODE_ICONS.length; i++) {
            icons.put(BrowserConstants.NODE_ICONS[i],
                    GUIUtilities.loadIcon(BrowserConstants.NODE_ICONS[i], true));
        }

        icons.put(BrowserConstants.DATABASE_OBJECT_IMAGE,
                GUIUtilities.loadIcon(BrowserConstants.DATABASE_OBJECT_IMAGE, true));

        return icons;
    }

    public DefaultMutableTreeNode getConnectionsBranchNode() {
        return getRootNode();
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
    }

    /**
     * Removes the tree listener.
     */
    public void removeTreeSelectionListener() {

        removeTreeSelectionListener(this);
    }

    /**
     * Adds the tree listener.
     */
    public void addTreeSelectionListener() {

        addTreeSelectionListener(this);
    }

    // --------------------------------------------------
    // ------- TreeSelectionListener implementation
    // --------------------------------------------------

    public void valueChanged(TreeSelectionEvent e) {

        panel.pathChanged(e.getOldLeadSelectionPath(), e.getPath());
    }


    // --------------------------------------------------
    // ------- TreeExpansionListener implementation
    // --------------------------------------------------

    public void treeExpanded(TreeExpansionEvent e) {

        panel.pathExpanded(e.getPath());
    }

    public void treeCollapsed(TreeExpansionEvent e) {

        // do nothing
    }

    private boolean hasFolders() {
        
        for (Enumeration<TreeNode> i = getRootNode().children(); i.hasMoreElements();) {
            
            DefaultMutableTreeNode element = (DefaultMutableTreeNode) i.nextElement();
            if (element instanceof ConnectionsFolderNode) {

                return true;

            } else if (element instanceof DatabaseHostNode) {

                return false;
            }

        }

        return false;
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() > 1) {
            TreePath selectionPath = getSelectionPath();
            if (selectionPath == null)
                return;
            DatabaseObjectNode node = (DatabaseObjectNode) selectionPath.getLastPathComponent();
            if (!node.isCatalog() && !node.isHostNode()) /**previous note: "if (!node.isCatalog())"*/ //Dzugaev

                if (node.getDatabaseObject() == null || node.getDatabaseObject().getType() != NamedObject.META_TAG)
                    panel.valueChanged(node);
        }
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseEntered(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseExited(MouseEvent mouseEvent) {

    }


    // nice example: http://www.coderanch.com/t/346509/GUI/java/JTree-drag-drop-inside-one

    public TreePanel getTreePanel() {
        return panel;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            TreePath selectionPath = getSelectionPath();
            if (selectionPath == null)
                return;
            DatabaseObjectNode node = (DatabaseObjectNode) selectionPath.getLastPathComponent();
                if (!node.isCatalog())
                    panel.valueChanged(node);

        }
    }

    class TreeTransferHandler extends TransferHandler {

        DataFlavor nodesFlavor;
        DataFlavor[] flavors = new DataFlavor[1];
        DefaultMutableTreeNode[] nodesToRemove;

        public TreeTransferHandler() {
            try {
                String mimeType = DataFlavor.javaJVMLocalObjectMimeType +
                        ";class=\"" +
                        javax.swing.tree.DefaultMutableTreeNode[].class.getName() +
                        "\"";
                nodesFlavor = new DataFlavor(mimeType);
                flavors[0] = nodesFlavor;
            } catch (ClassNotFoundException e) {
                System.out.println("ClassNotFound: " + e.getMessage());
            }
        }

        long lastTime = 0;

        public boolean canImport(TransferHandler.TransferSupport support) {

            if (!support.isDrop()) {

                return false;
            }

            support.setShowDropLocation(true);
            if (!support.isDataFlavorSupported(nodesFlavor)) {

                return false;
            }

            // Do not allow a drop on the drag source selections.
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            JTree tree = (JTree) support.getComponent();
            int[] selRows = tree.getSelectionRows();
            if (getTreePanel().getTreeType() == TreePanel.TABLESPACE) {
                /*DependPanel treePanel=(DependPanel) getTreePanel();
                if(System.currentTimeMillis()-lastTime<1000) {
                    return false;
                }
                TreePath path = ConnectionsTreePanel.getPanelFromBrowser().getTree().getSelectionPath();
                DatabaseObjectNode firstNode=(DatabaseObjectNode) path.getLastPathComponent();
                String typeObject = firstNode.getMetaDataKey();
                String name = firstNode.getName().trim();
                String query = "ALTER "+typeObject+" "+ MiscUtils.getFormattedObject(name)+" SET TABLESPACE "+
                        MiscUtils.getFormattedObject(treePanel.getDatabaseObject().getName())+";";
                ExecuteQueryDialog executeQueryDialog =new ExecuteQueryDialog("",query,treePanel.getDatabaseConnection(),true);
                executeQueryDialog.display();
                if(executeQueryDialog.getCommit()) {
                    treePanel.reloadPath(getPathForRow(0));
                }
                lastTime=System.currentTimeMillis();*/
                return true;
            }
            int dropRow = tree.getRowForPath(dl.getPath());
            for (int i = 0; i < selRows.length; i++) {
                if (selRows[i] == dropRow) {
                    return false;
                }
            }


            TreePath dest = dl.getPath();
            DefaultMutableTreeNode target = asTreeNode(dest.getLastPathComponent());

            TreePath path = tree.getPathForRow(selRows[0]);
            DefaultMutableTreeNode firstNode = asTreeNode(path.getLastPathComponent());

//            System.out.println(" ------------------- ");
//            System.out.println("target: " + target);

            if (isFolderNode(firstNode) && isFolderNode(target)) {

                return false;
            }

            if (isRootNode(target)) {

                int index = dl.getChildIndex();
                TreePath insertionPath = tree.getPathForRow(index);

                if (index == 0) {

                    if (!isFolderNode(firstNode) && hasFolders()) {

                        return false;
                    }

                } else if (insertionPath != null) {

                    DefaultMutableTreeNode nodeAtInsertionPath = asTreeNode(insertionPath.getLastPathComponent());

//                    System.out.println("node at insertion: " + nodeAtInsertionPath);

                    if (!(firstNode.getClass().getName().equals(nodeAtInsertionPath.getClass().getName()))) {

                        return false;
                    }

                }

            }

            // Do not allow MOVE-action drops if a non-leaf node is selected
            // unless all of its children are also selected.
            int action = support.getDropAction();
            if (action == MOVE) {

                return haveCompleteNode(tree);
            }

            // Do not allow a non-leaf node to be copied to a level which is less than its source level.
            return firstNode.getChildCount() <= 0 || target.getLevel() >= firstNode.getLevel();
        }

        private boolean haveCompleteNode(JTree tree) {

            int[] selRows = tree.getSelectionRows();
            TreePath path = tree.getPathForRow(selRows[0]);
            DefaultMutableTreeNode first = asTreeNode(path.getLastPathComponent());

            int childCount = first.getChildCount();

            /*
            // first has children and no children are selected.
            if (childCount > 0 && selRows.length == 1) {

                return false;
            }
            */

            // first may have children.
            for (int i = 1; i < selRows.length; i++) {

                path = tree.getPathForRow(selRows[i]);
                DefaultMutableTreeNode next = asTreeNode(path.getLastPathComponent());
                if (first.isNodeChild(next)) {

                    // Found a child of first.
                    if (childCount > selRows.length - 1) {

                        // Not all children of first are selected.
                        return false;
                    }

                }
            }
            return true;
        }

        @Override
        public void exportAsDrag(JComponent comp, InputEvent e, int action) {

            if (loadingNode) {
                // I hope that this 'hack' will clear awt blah blah blah error
                return;
                // hack!
//                throw new SuppressedException(Bundles.get("SchemaTree.error.exportAsDrag"));
            }

            super.exportAsDrag(comp, e, action);
        }

        protected Transferable createTransferable(JComponent c) {

            JTree tree = (JTree) c;
            TreePath[] paths = tree.getSelectionPaths();

            if (paths != null) {

                DefaultMutableTreeNode node = asTreeNode(paths[0].getLastPathComponent());
                if (!canDrag(node) || isExpanded(paths[0])) {

                    return null;
                }

                // Make up a node array of copies for transfer and
                // another for/of the nodes that will be removed in
                // exportDone after a successful drop.
                List<DefaultMutableTreeNode> copies = new ArrayList<DefaultMutableTreeNode>();
                List<DefaultMutableTreeNode> toRemove = new ArrayList<DefaultMutableTreeNode>();

                DefaultMutableTreeNode copy = ((DatabaseObjectNode) node).copy();
                copies.add(copy);
                toRemove.add(node);

                for (int i = 1; i < paths.length; i++) {

                    DefaultMutableTreeNode next = asTreeNode(paths[i].getLastPathComponent());

                    // Do not allow higher level nodes to be added to list.
                    if (next.getLevel() < node.getLevel()) {

                        break;

                    } else if (next.getLevel() > node.getLevel()) { // child node

                        copy.add(copy(next));
                        // node already contains child

                    } else { // sibling

                        copies.add(copy(next));
                        toRemove.add(next);
                    }

                }

                DefaultMutableTreeNode[] nodes = copies.toArray(new DefaultMutableTreeNode[copies.size()]);
                //nodesToRemove = toRemove.toArray(new DefaultMutableTreeNode[toRemove.size()]);

                return new NodesTransferable(nodes);
            }

            return null;
        }

        private boolean canDrag(DefaultMutableTreeNode node) {

            if (node instanceof DatabaseObjectNode) {

                return ((DatabaseObjectNode) node).isDraggable();
            }

            return false;
        }

        /**
         * Defensive copy used in createTransferable.
         */
        private DefaultMutableTreeNode copy(TreeNode node) {
            return new DefaultMutableTreeNode(node);
        }

        protected void exportDone(JComponent source, Transferable data, int action) {

            TreePath[] paths = getSelectionPaths();
            final Object lastPathComponent = paths[0].getLastPathComponent();

            if ((action & MOVE) == MOVE) {

                JTree tree = (JTree) source;
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

                // Remove nodes saved in nodesToRemove in createTransferable.
                if (nodesToRemove != null)
                    for (int i = 0; i < nodesToRemove.length; i++) {

                        model.removeNodeFromParent(nodesToRemove[i]);
                    }

                panel.rebuildConnectionsFromTree();
                panel.repaint();

                ThreadUtils.invokeLater(new Runnable() {
                    public void run() {
                        if (lastPathComponent instanceof DatabaseHostNode) {

                            String prefix = lastPathComponent.toString();
                            TreePath path = null;

                            // need to make sure we have the right node since
                            // nodes with the same prefix but higher will
                            // return first

                            int index = 0;
                            int rowCount = getRowCount();
                            while (index < rowCount) {

                                path = getNextMatch(prefix, index, Position.Bias.Forward);
                                if (path != null && prefix.equals(path.getLastPathComponent().toString())) {

                                    break;
                                }
                                index++;
                            }

                            if (path != null) {

                                try {

                                    removeTreeSelectionListener();
                                    scrollPathToVisible(path);
                                    setSelectionPath(path);

                                } finally {

                                    addTreeSelectionListener();
                                }

                            }

                        }
                    }
                });

            }

        }

        public int getSourceActions(JComponent c) {
            return COPY_OR_MOVE;
        }

        public boolean importData(TransferHandler.TransferSupport support) {

            if (!canImport(support)) {
                return false;
            }

            if (getTreePanel().getTreeType() == TreePanel.TABLESPACE) {
                DependPanel treePanel = (DependPanel) getTreePanel();
                TreePath path = ConnectionsTreePanel.getPanelFromBrowser().getTree().getSelectionPath();
                DatabaseObjectNode firstNode = (DatabaseObjectNode) path.getLastPathComponent();
                String typeObject = firstNode.getMetaDataKey();
                String name = firstNode.getName().trim();
                String query = "ALTER " + typeObject + " " + MiscUtils.getFormattedObject(name) + " SET TABLESPACE " +
                        MiscUtils.getFormattedObject(treePanel.getDatabaseObject().getName()) + ";";
                /*ExecuteQueryDialog executeQueryDialog =new ExecuteQueryDialog("",query,treePanel.getDatabaseConnection(),true);
                executeQueryDialog.display();
                if(executeQueryDialog.getCommit()) {
                    treePanel.reloadPath(getPathForRow(0));
                }*/
                DefaultStatementExecutor querySender = new DefaultStatementExecutor(treePanel.getDatabaseConnection());
                try {
                    SqlStatementResult result = querySender.execute(QueryTypes.ALTER_OBJECT, query);
                    if (!result.isException())
                        treePanel.reloadPath(getPathForRow(0));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return true;
            }

            // Extract transfer data.
            DefaultMutableTreeNode[] nodes = null;
            try {

                Transferable t = support.getTransferable();
                nodes = (DefaultMutableTreeNode[]) t.getTransferData(nodesFlavor);

            } catch (UnsupportedFlavorException ufe) {
                System.out.println("UnsupportedFlavor: " + ufe.getMessage());
            } catch (java.io.IOException ioe) {
                System.out.println("I/O error: " + ioe.getMessage());
            }

            // Get drop location info.
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            int childIndex = dl.getChildIndex();

            TreePath dest = dl.getPath();
            DefaultMutableTreeNode parent = asTreeNode(dest.getLastPathComponent());

            if (!(parent instanceof RootDatabaseObjectNode)) {
                return false;
            }

            JTree tree = (JTree) support.getComponent();
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            // Configure for drop mode.
            int index = childIndex;    // DropMode.INSERT
            if (childIndex == -1) {     // DropMode.ON
                index = parent.getChildCount();
            }
            // Add data to model.
            for (int i = 0; i < nodes.length; i++) {
                model.insertNodeInto(nodes[i], parent, index++);
            }

            return true;
        }

        public String toString() {
            return getClass().getName();
        }

        public class NodesTransferable implements Transferable {

            private final DefaultMutableTreeNode[] nodes;

            public NodesTransferable(DefaultMutableTreeNode[] nodes) {
                this.nodes = nodes;
            }

            public Object getTransferData(DataFlavor flavor)
                    throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor))
                    throw new UnsupportedFlavorException(flavor);
                return nodes;
            }

            public DataFlavor[] getTransferDataFlavors() {
                return flavors;
            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return nodesFlavor.equals(flavor);
            }
        }

    }

    private boolean loadingNode;

    public void startLoadingNode() {

        loadingNode = true;
    }

    public void finishedLoadingNode() {

        loadingNode = false;
    }

    public void connectionNameChanged(String name) {

        panel.connectionNameChanged(name);
    }

    public boolean canMoveSelection(int direction) {

        DefaultMutableTreeNode node = asTreeNode(getLastPathComponent());
        int currentIndex = getIndexWithinParent(node);

        TreeNode parent = node.getParent();
        DefaultMutableTreeNode adjacentNode = null;

        if (direction == MOVE_UP) {

            if (currentIndex == 0) {

                return false;
            }

            int newIndex = currentIndex - 1;
            adjacentNode = (DefaultMutableTreeNode) parent.getChildAt(newIndex);

        } else {

            int newIndex = currentIndex + 1;
            int childCount = parent.getChildCount();
            if (newIndex > (childCount - 1)) {

                return false;
            }

            adjacentNode = (DefaultMutableTreeNode) parent.getChildAt(newIndex);
        }

        return (adjacentNode.getClass().getName().equals(node.getClass().getName()));
    }

    private boolean isFolderNode(DefaultMutableTreeNode node) {
        return node instanceof ConnectionsFolderNode;
    }

    private boolean isRootNode(DefaultMutableTreeNode node) {
        return node instanceof RootDatabaseObjectNode && !(isFolderNode(node));
    }

    private DefaultMutableTreeNode asTreeNode(Object object) {
        return (DefaultMutableTreeNode) object;
    }

}





