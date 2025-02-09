/*
 * DatabaseHostNode.java
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

package org.executequery.gui.browser.nodes;

import org.executequery.databasemediators.DatabaseConnection;
import org.executequery.databaseobjects.DatabaseHost;
import org.executequery.databaseobjects.DatabaseMetaTag;
import org.executequery.databaseobjects.DatabaseSchema;
import org.executequery.databaseobjects.DatabaseSource;
import org.executequery.util.UserProperties;
import org.underworldlabs.jdbc.DataSourceException;
import org.underworldlabs.util.SystemProperties;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Takis Diakoumis
 */
public class DatabaseHostNode extends DatabaseObjectNode {

    private int order;

    /**
     * the direct descendants of this object
     */
    private List<DatabaseObjectNode> visibleChildren;
    private List<DatabaseObjectNode> allChildren;


    /**
     * indicates whether to show only default catalogs/schemas
     */
    private boolean defaultCatalogsAndSchemasOnly;

    private ConnectionsFolderNode parentFolder;

    /**
     * Creates a new instance of DatabaseHostNode
     */
    public DatabaseHostNode(DatabaseHost host, ConnectionsFolderNode parentFolder) {

        super(host);
        this.parentFolder = parentFolder;
        applyUserPreferences();
    }

    @Override
    public DatabaseObjectNode copy() {
        return new DatabaseHostNode((DatabaseHost) getDatabaseObject(), parentFolder);
    }

    @Override
    public boolean isDraggable() {
        return true;
    }

    @Override
    public boolean isNameEditable() {
        return true;
    }

    public void setParentFolder(ConnectionsFolderNode parentFolder) {
        this.parentFolder = parentFolder;
    }

    public ConnectionsFolderNode getParentFolder() {
        return parentFolder;
    }

    public void applyUserPreferences() {

        setDefaultCatalogsAndSchemasOnly(UserProperties.getInstance().getBooleanProperty(
                "browser.catalog.schema.defaults.only"));
    }

    /**
     * Returns whether the host is connected.
     */
    public boolean isConnected() {
        return getDatabaseConnection().isConnected();
    }

    public DatabaseConnection getDatabaseConnection() {
        return ((DatabaseHost) getDatabaseObject()).getDatabaseConnection();
    }

    /**
     * Adds this object's children as expanded nodes.
     */
    public void populateChildren() throws DataSourceException {
        if (isConnected()) {
            super.populateChildren();
            ensureValidCatalogsAndSchemasVisible();
        }
    }

    /**
     * Indicates that this host has been disconnected.
     */
    public void disconnected() {
        reset();
    }

    /**
     * Indicates whether this node is a leaf node.
     *
     * @return true | false
     */
    public boolean isLeaf() {
        return !(isConnected());
    }

    /**
     * Override to return true.
     */
    public boolean allowsChildren() {
        return true;
    }

    /**
     * Returns the children associated with this node.
     *
     * @return a list of children for this node
     */
    public List<DatabaseObjectNode> getChildObjects() throws DataSourceException {

        if (visibleChildren != null) {

            return visibleChildren;
        }

        // check for catalogs - then schemas - then meta tags
        DatabaseHost host = (DatabaseHost) getDatabaseObject();

        // check for catalogs
        List<?> _children = host.getCatalogs();


        // check we have schemas
        if (_children == null || _children.isEmpty()) {

            // otherwise get meta tags
            _children = host.getMetaObjects();

        } else {  // have schemas so return these

            int count = _children.size();
            visibleChildren = new ArrayList<DatabaseObjectNode>();

            for (int i = 0; i < count; i++) {

                DatabaseSchema schema = (DatabaseSchema) _children.get(i);
                visibleChildren.add(new DatabaseSchemaNode(schema));
            }

            return visibleChildren;
        }

        // check we have meta tags
        if (_children != null && !_children.isEmpty()) {

            int count = _children.size();
            visibleChildren = new ArrayList<>();
            allChildren = new ArrayList<>();
            for (int i = 0; i < count; i++) {

                DatabaseMetaTag metaTag = (DatabaseMetaTag) _children.get(i);
                DatabaseObjectNode metaTagNode = new DatabaseObjectNode(metaTag);
                allChildren.add(metaTagNode);
                if (!metaTag.getMetaDataKey().contains("SYSTEM") || SystemProperties.getBooleanProperty("user", "browser.show.system.objects"))
                    visibleChildren.add(new DatabaseObjectNode(metaTag));
            }

            return visibleChildren;
        }

        return null;
    }

    public List<DatabaseObjectNode> getAllChildren() {
        if (allChildren == null)
            getChildObjects();
        return allChildren;
    }

    /**
     * Clears out the children of this node.
     */
    public void reset() {
        super.reset();
        visibleChildren = null;
    }

    public boolean isDefaultCatalogsAndSchemasOnly() {
        return defaultCatalogsAndSchemasOnly;
    }

    public void setDefaultCatalogsAndSchemasOnly(
            boolean defaultCatalogsAndSchemasOnly) {

        this.defaultCatalogsAndSchemasOnly = defaultCatalogsAndSchemasOnly;

        ensureValidCatalogsAndSchemasVisible();
    }

    private void ensureValidCatalogsAndSchemasVisible() {

        if (visibleChildren == null) {

            return;
        }

        if (isDefaultCatalogsAndSchemasOnly()) {

            showOnlyDefaults();

        } else {

            showAll();
        }

    }

    private void showAll() {

        int index = 0;

        for (DatabaseObjectNode node : visibleChildren) {

            if (!childExists(node)) {

                insert(node, index);
            }

            index++;
        }

    }

    private void showOnlyDefaults() {

        if (!hasDefaults()) {

            return;
        }

        int index = 0;

        for (DatabaseObjectNode node : visibleChildren) {

            if (isDatabaseSourceNode(node)) {

                DatabaseSource source = databaseSourceObjectFromNode(node);

                if (source.isDefault()) {

                    if (!childExists(node)) {

                        insert(node, index);
                    }

                } else {

                    if (getIndex(node) != -1) {

                        remove(node);
                    }

                }

            }

            index++;
        }

    }

    private boolean hasDefaults() {

        for (DatabaseObjectNode node : visibleChildren) {

            if (isDatabaseSourceNode(node)) {

                DatabaseSource source = databaseSourceObjectFromNode(node);

                if (source.isDefault()) {

                    return true;
                }

            }

        }

        return false;
    }

    private DatabaseSource databaseSourceObjectFromNode(DatabaseObjectNode node) {

        return (DatabaseSource) node.getDatabaseObject();
    }

    public boolean isDatabaseSourceNode(DatabaseObjectNode node) {

        return (node.getDatabaseObject() instanceof DatabaseSource);
    }

    private boolean childExists(TreeNode node) {

        for (Enumeration<?> i = children(); i.hasMoreElements(); ) {

            if (i.nextElement().equals(node)) {

                return true;
            }

        }

        return false;
    }

    public int getOrder() {

        return order;
    }

    public void setOrder(int order) {

        this.order = order;
    }

    public void removeFromFolder() {

        if (parentFolder != null) {

            parentFolder.removeNode(this);
        }
    }

    public List<DatabaseObjectNode> getAllDBObjects(String type) {
        List<DatabaseObjectNode> childs = getAllChildren();
        for (DatabaseObjectNode child : childs) {
            if (child.getMetaDataKey().contains(type))
                return child.getChildObjects();
        }
        return new ArrayList<>();
    }

}




