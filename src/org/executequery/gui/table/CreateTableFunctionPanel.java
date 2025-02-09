/*
 * CreateTableFunctionPanel.java
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

package org.executequery.gui.table;

import org.executequery.GUIUtilities;
import org.executequery.databasemediators.DatabaseConnection;
import org.executequery.databasemediators.MetaDataValues;
import org.executequery.databasemediators.QueryTypes;
import org.executequery.databasemediators.spi.DefaultStatementExecutor;
import org.executequery.databaseobjects.NamedObject;
import org.executequery.datasource.ConnectionManager;
import org.executequery.gui.FocusComponentPanel;
import org.executequery.gui.WidgetFactory;
import org.executequery.gui.browser.ColumnConstraint;
import org.executequery.gui.browser.ColumnData;
import org.executequery.gui.browser.ConnectionsTreePanel;
import org.executequery.gui.text.SimpleSqlTextPanel;
import org.executequery.gui.text.TextEditor;
import org.executequery.gui.text.TextEditorContainer;
import org.executequery.localization.Bundles;
import org.executequery.log.Log;
import org.underworldlabs.jdbc.DataSourceException;
import org.underworldlabs.swing.DynamicComboBoxModel;
import org.underworldlabs.swing.GUIUtils;
import org.underworldlabs.util.SQLUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * The Create Table base panel.
 *
 * @author Takis Diakoumis
 */
public abstract class CreateTableFunctionPanel extends JPanel
        implements FocusComponentPanel,
        ItemListener,
        ChangeListener,
        TableModifier,
        TableConstraintFunction,
        TextEditorContainer {

    /**
     * The table name field
     */
    protected JTextField nameField;

    /**
     * The connection combo selection
     */
    protected JComboBox connectionsCombo;


    protected DynamicComboBoxModel connectionsModel;

    protected JComboBox tablespacesCombo;

    protected DynamicComboBoxModel tablespaceComboModel;

    /**
     * The table column definition panel
     */
    protected NewTablePanel tablePanel;

    /**
     * The table constraints panel
     */
    protected NewTableConstraintsPanel consPanel;

    /**
     * The text pane showing SQL generated
     */
    protected SimpleSqlTextPanel sqlText;

    /**
     * The tabbed pane containing definition and constraints
     */
    private JTabbedPane tableTabs;

    /**
     * The buffer off all SQL generated
     */
    protected StringBuffer sqlBuffer;

    /**
     * The tool bar
     */
    private CreateTableToolBar tools;

    /**
     * Utility to retrieve database meta data
     */
    protected MetaDataValues metaData;

    /**
     * The base panel
     */
    protected JPanel mainPanel;

    protected boolean temporary;
    private JComboBox typeTemporaryBox;

    /**
     * <p> Constructs a new instance.
     */
    public CreateTableFunctionPanel(boolean temporary) {
        super(new BorderLayout());
        this.temporary = temporary;
        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void init() throws Exception {

        nameField = WidgetFactory.createTextField();
        if (temporary)
            nameField.setText("NEW_GLOBAL_TEMPORARY_TABLE");
        else
            nameField.setText("NEW_TABLE");
        //initialise the schema label
        metaData = new MetaDataValues(true);

        // combo boxes
        Vector<DatabaseConnection> connections = ConnectionManager.getActiveConnections();
        connectionsModel = new DynamicComboBoxModel(connections);
        connectionsCombo = WidgetFactory.createComboBox(connectionsModel);
        connectionsCombo.addItemListener(this);

        tablespaceComboModel = new DynamicComboBoxModel(new Vector<>());
        tablespacesCombo = WidgetFactory.createComboBox(tablespaceComboModel);
        tablespacesCombo.addItemListener(this);

        // create tab pane
        tableTabs = new JTabbedPane();
        // create the column definition panel
        // and add this to the tabbed pane
        tablePanel = new NewTablePanel(this);
        tableTabs.add(bundledString("Columns"), tablePanel);

        // create the constraints table and model
        JPanel constraintsPanel = new JPanel(new GridBagLayout());
        consPanel = new NewTableConstraintsPanel(this);
        consPanel.setData(new Vector(0), true);
        typeTemporaryBox = new JComboBox(new DefaultComboBoxModel(new String[]{"DELETE ROWS", "PRESERVE ROWS"}));
        typeTemporaryBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setSQLText();
            }
        });

        constraintsPanel.add(consPanel, new GridBagConstraints(
                1, 1, 1, 1, 1.0, 1.0,
                GridBagConstraints.SOUTHEAST,
                GridBagConstraints.BOTH,
                new Insets(2, 2, 2, 2), 0, 0));

        tableTabs.add(bundledString("Constraints"), constraintsPanel);

        sqlText = new SimpleSqlTextPanel();
        tools = new CreateTableToolBar(this);

        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEtchedBorder());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;

        WidgetFactory.addLabelFieldPair(mainPanel, bundledString("Connection"), connectionsCombo, gbc);
        WidgetFactory.addLabelFieldPair(mainPanel, bundledString("TableName"), nameField, gbc);
        WidgetFactory.addLabelFieldPair(mainPanel, Bundles.get(TableDefinitionPanel.class, "Tablespace"), tablespacesCombo, gbc);
        if (temporary)
            WidgetFactory.addLabelFieldPair(mainPanel, bundledString("TypeTemporaryTable"), typeTemporaryBox, gbc);

        JPanel definitionPanel = new JPanel(new GridBagLayout());
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.insets.right = 5;
        gbc.insets.left = 5;
        gbc.insets.top = 20;
        gbc.fill = GridBagConstraints.VERTICAL;
        definitionPanel.add(tools, gbc);
        gbc.insets.left = 0;
        gbc.insets.right = 5;
        gbc.insets.top = 0;
        gbc.gridx = 1;
        gbc.weighty = 0.4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        definitionPanel.add(tableTabs, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets.top = 10;
        mainPanel.add(definitionPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weighty = 0.6;
        gbc.insets.left = 5;
        gbc.insets.bottom = 5;
        gbc.insets.top = 5;
        mainPanel.add(sqlText, gbc);

        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(mainPanel, BorderLayout.CENTER);

        tableTabs.addChangeListener(this);
        nameField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                setSQLText();
            }
        });

        sqlBuffer = new StringBuffer(CreateTableSQLSyntax.CREATE_TABLE);

        // check initial values for possible value inits
        if (connections == null || connections.isEmpty()) {
            connectionsCombo.setEnabled(false);
        } else {
            DatabaseConnection connection =
                    connections.elementAt(0);
            metaData.setDatabaseConnection(connection);

            tablePanel.setDataTypes(metaData.getDataTypesArray(), metaData.getIntDataTypesArray());
            tablePanel.setDomains(getDomains());
            tablePanel.setGenerators(getGenerators());
            tablePanel.setDatabaseConnection(connection);
            populateTablespaces(connection);
            //metaData
        }


    }

    String[] getDomains() {
        DefaultStatementExecutor executor = new DefaultStatementExecutor(getSelectedConnection(), true);
        List<String> domains = new ArrayList<>();
        try {
            String query = "select " +
                    "RDB$FIELD_NAME FROM RDB$FIELDS " +
                    "where RDB$FIELD_NAME not like 'RDB$%'\n" +
                    "and RDB$FIELD_NAME not like 'MON$%'\n" +
                    "order by RDB$FIELD_NAME";
            ResultSet rs = executor.execute(QueryTypes.SELECT, query).getResultSet();
            while (rs.next()) {
                domains.add(rs.getString(1).trim());
            }
            executor.releaseResources();
            return domains.toArray(new String[domains.size()]);
        } catch (Exception e) {
            Log.error("Error loading domains:" + e.getMessage());
            return null;
        }
    }

    String[] getGenerators() {
        DefaultStatementExecutor executor = new DefaultStatementExecutor(getSelectedConnection(), true);
        List<String> domains = new ArrayList<>();
        try {
            String query = "select " +
                    "RDB$GENERATOR_NAME FROM RDB$GENERATORS " +
                    "where RDB$SYSTEM_FLAG = 0 " +
                    "order by 1";
            ResultSet rs = executor.execute(QueryTypes.SELECT, query).getResultSet();
            while (rs.next()) {
                domains.add(rs.getString(1).trim());
            }
            executor.releaseResources();
            return domains.toArray(new String[domains.size()]);
        } catch (Exception e) {
            Log.error("Error loading generators:" + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the selected connection from the panel's
     * connections combo selection box.
     *
     * @return the selected connection properties object
     */
    public DatabaseConnection getSelectedConnection() {
        return (DatabaseConnection) connectionsCombo.getSelectedItem();
    }

    /**
     * Returns the table name field.
     */
    public Component getDefaultFocusComponent() {
        return nameField;
    }

    /**
     * Invoked when an item has been selected or deselected by the user.
     * The code written for this method performs the operations
     * that need to occur when an item is selected (or deselected).
     */
    public void itemStateChanged(ItemEvent event) {
        // interested in selections only
        if (event.getStateChange() == ItemEvent.DESELECTED) {
            return;
        }

        final Object source = event.getSource();
        if (event.getSource() == tablespacesCombo) {
            setSQLText();
        } else
            GUIUtils.startWorker(new Runnable() {
                public void run() {
                    try {
                        setInProcess(true);
                        if (source == connectionsCombo) {
                            connectionChanged();
                        }
                    } finally {
                        setInProcess(false);
                    }
                }
            });
    }

    private void columnChangeConnection(DatabaseConnection dc) {
        Vector<ColumnData> cd = getTableColumnDataVector();
        for (ColumnData c : cd) {
            c.setDatabaseConnection(dc);
        }
    }

    private void connectionChanged() {
        // retrieve connection selection
        DatabaseConnection connection =
                (DatabaseConnection) connectionsCombo.getSelectedItem();

        // reset meta data
        metaData.setDatabaseConnection(connection);
        tablePanel.setDatabaseConnection(connection);
        columnChangeConnection(connection);

        // reset schema values

        // reset data types
        try {
            populateDataTypes(metaData.getDataTypesArray(), metaData.getIntDataTypesArray());
        } catch (DataSourceException e) {
            GUIUtilities.displayExceptionErrorDialog(
                    bundledString("error.retrieving", bundledString("data-types"), bundledString("selected-connection"), e.getExtendedMessage()),
                    e);
            populateDataTypes(new String[0], new int[0]);
        }
        populateTablespaces(connection);


    }

    private void populateTablespaces(DatabaseConnection connection) {
        List<NamedObject> tss = ConnectionsTreePanel.getPanelFromBrowser().getDefaultDatabaseHostFromConnection(connection)
                .getDatabaseObjectsForMetaTag(NamedObject.META_TYPES[NamedObject.TABLESPACE]);
        if (tss == null)
            tablespacesCombo.setEnabled(false);
        else {
            Vector<NamedObject> vector = new Vector<>();
            vector.add(null);
            vector.addAll(tss);
            tablespaceComboModel.setElements(vector);
        }
    }

    private void populateDataTypes(final String[] dataTypes, final int[] intDataTypes) {
        GUIUtils.invokeAndWait(new Runnable() {
            public void run() {
                tablePanel.setDataTypes(dataTypes, intDataTypes);
                tablePanel.setDomains(getDomains());
                tablePanel.setGenerators(getGenerators());
            }
        });
    }

    public void setFocusComponent() {
        nameField.requestFocusInWindow();
        nameField.selectAll();
    }

    public void setSQLTextCaretPosition(int position) {
        sqlText.setCaretPosition(position);
    }

    protected void addButtonsPanel(JPanel buttonsPanel) {
        add(buttonsPanel, BorderLayout.SOUTH);
    }

    public void fireEditingStopped() {
        tablePanel.fireEditingStopped();
        consPanel.fireEditingStopped();
    }

    public void setColumnDataArray(ColumnData[] cda) {
        tablePanel.setColumnDataArray(cda, null);
    }

    public void setColumnConstraintVector(Vector ccv, boolean fillCombos) {
        consPanel.setData(ccv, fillCombos);
    }

    public void setColumnConstraintsArray(ColumnConstraint[] cca, boolean fillCombos) {
        Vector ccv = new Vector(cca.length);
        for (int i = 0; i < cca.length; i++) {
            ccv.add(cca[i]);
        }
        consPanel.setData(ccv, fillCombos);
    }

    /**
     * Indicates that a [long-running] process has begun or ended
     * as specified. This may trigger the glass pane on or off
     * or set the cursor appropriately.
     *
     * @param inProcess - true | false
     */
    public void setInProcess(boolean inProcess) {
    }

    // -----------------------------------------------
    // --- TableConstraintFunction implementations ---
    // -----------------------------------------------

    public abstract Vector<String> getHostedSchemasVector();

    public abstract Vector<String> getSchemaTables(String schemaName);

    public abstract Vector<String> getColumnNamesVector(String tableName, String schemaName);

    public void resetSQLText() {
        tablePanel.resetSQLText();
        consPanel.resetSQLText();
    }

    public void setSQLText() {
        if (getSelectedConnection().isNamesToUpperCase())
            nameField.setText(nameField.getText().toUpperCase());
        String tablespace = null;
        if (tablespacesCombo.getSelectedItem() != null)
            tablespace = ((NamedObject) tablespacesCombo.getSelectedItem()).getName();
        setSQLText(SQLUtils.generateCreateTable(nameField.getText(), tablePanel.getTableColumnDataVector(), consPanel.getKeys(), false, temporary, "ON COMMIT " + typeTemporaryBox.getSelectedItem(), null, null, tablespace));
    }

    private void setSQLText(final String text) {
        GUIUtils.invokeLater(new Runnable() {
            public void run() {
                sqlText.setSQLText(text);
            }
        });
    }

    public String getSQLText() {
        return sqlText.getSQLText();
    }

    public String getTableName() {
        return nameField.getText();
    }

    // -----------------------------------------------


    // constraints panel only
    public void updateCellEditor(int col, int row, String value) {
    }

    public void columnValuesChanging(int col, int row, String value) {
    }

    public Vector getTableColumnDataVector() {
        return tablePanel.getTableColumnDataVector();
    }

    public void stateChanged(ChangeEvent e) {
        if (tableTabs.getSelectedIndex() == 1) {
            tools.enableButtons(false);
            checkFullType();
        } else {
            tools.enableButtons(true);
        }
    }

    protected boolean checkFullType() {
        for (int i = 0; i < getTableColumnData().length; i++) {
            if (getTableColumnData()[i].getColumnType() == null) {
                GUIUtilities.displayErrorMessage(bundledString("error.select-type"));
                tableTabs.setSelectedIndex(0);
                return false;
            }
        }
        return true;
    }

    /*
    private void tableTabs_changed() {
        
        if (tableTabs.getSelectedIndex() == 1) {
            tools.enableButtons(false);
            
            //          if (table.isEditing())
            //            table.removeEditor();
            
        }
        else {
            tools.enableButtons(true);
        }
        
    }
    */

    public ColumnData[] getTableColumnDataAndConstraints() {
        String tableName = null;
        ColumnData[] cda = tablePanel.getTableColumnData();
        ColumnConstraint[] cca = consPanel.getColumnConstraintArray();

        for (int i = 0; i < cda.length; i++) {

            // reset the keys
            cda[i].setPrimaryKey(false);
            cda[i].setForeignKey(false);
            cda[i].resetConstraints();

            tableName = cda[i].getTableName();

            String columnName = cda[i].getColumnName();

            for (int j = 0; j < cca.length; j++) {

                String constraintColumn = cca[j].getColumn();

                if (constraintColumn != null
                        && constraintColumn.equalsIgnoreCase(columnName)) {

                    if (cca[j].isPrimaryKey()) {
                        cda[i].setPrimaryKey(true);
                    } else if (cca[j].isForeignKey()) {
                        cda[i].setForeignKey(true);
                    }

                    cca[j].setTable(tableName);
                    cca[j].setNewConstraint(true);
                    cda[i].addConstraint(cca[j]);
                }

            }

        }

        return cda;

    }

    public void columnValuesChanging() {
    }

    public ColumnData[] getTableColumnData() {
        return tablePanel.getTableColumnData();
    }

    // -----------------------------------------------
    // -------- TableFunction implementations --------
    // -----------------------------------------------

    public void moveColumnUp() {
        int index = tableTabs.getSelectedIndex();
        if (index == 0) {
            tablePanel.moveColumnUp();
        }
    }

    public void moveColumnDown() {
        int index = tableTabs.getSelectedIndex();
        if (index == 0) {
            tablePanel.moveColumnDown();
        }
    }

    public void deleteRow() {
        if (tableTabs.getSelectedIndex() == 0) {
            tablePanel.deleteRow();
        } else if (tableTabs.getSelectedIndex() == 1) {
            consPanel.deleteSelectedRow();
        }
    }

    public void insertBefore() {
        tablePanel.insertBefore();
    }

    public void insertAfter() {
        if (tableTabs.getSelectedIndex() == 0) {
            tablePanel.insertAfter();
        } else if (tableTabs.getSelectedIndex() == 1) {
            consPanel.insertRowAfter();
        }
    }

    // -----------------------------------------------

    public String getDisplayName() {
        return "";
    }

    // ------------------------------------------------
    // ----- TextEditorContainer implementations ------
    // ------------------------------------------------

    /**
     * Returns the SQL text pane as the TextEditor component
     * that this container holds.
     */
    public TextEditor getTextEditor() {
        return sqlText;
    }

    protected String bundleString(String key) {
        return Bundles.get(getClass(), key);
    }

    protected String bundleString(String key, Object... args) {
        return Bundles.get(getClass(), key, args);
    }

    private String bundledString(String key) {
        return Bundles.get(CreateTableFunctionPanel.class, key);
    }

    private String bundledString(String key, Object... args) {
        return Bundles.get(CreateTableFunctionPanel.class, key, args);
    }

}















