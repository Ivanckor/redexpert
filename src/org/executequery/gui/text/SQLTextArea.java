package org.executequery.gui.text;

import org.executequery.Constants;
import org.executequery.GUIUtilities;
import org.executequery.actions.searchcommands.FindAction;
import org.executequery.actions.searchcommands.ReplaceAction;
import org.executequery.databasemediators.DatabaseConnection;
import org.executequery.gui.BaseDialog;
import org.executequery.gui.browser.ConnectionsTreePanel;
import org.executequery.gui.browser.TreeFindAction;
import org.executequery.gui.browser.tree.SchemaTree;
import org.executequery.gui.editor.QueryEditorSettings;
import org.executequery.gui.editor.autocomplete.DefaultAutoCompletePopupProvider;
import org.executequery.gui.text.syntax.SQLSyntaxDocument;
import org.executequery.print.TextPrinter;
import org.executequery.repository.KeywordRepository;
import org.executequery.repository.RepositoryCache;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RecordableTextAction;
import org.underworldlabs.sqlLexer.CustomTokenMakerFactory;
import org.underworldlabs.sqlLexer.SqlLexerTokenMaker;
import org.underworldlabs.util.SystemProperties;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.Printable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLTextArea extends RSyntaxTextArea implements TextEditor {

    private static final String AUTO_COMPLETE_POPUP_ACTION_KEY = "autoCompletePopupActionKey";
    private static final String FIND_ACTION_KEY = "findActionKey";
    private static final String REPLACE_ACTION_KEY = "replaceActionKey";
    private static final String SQL_COMMENT_REGEX = "^\\s*--";
    private static final String SQL_COMMENT = "--";

    private final CustomTokenMakerFactory tokenMakerFactory = new CustomTokenMakerFactory();
    protected DatabaseConnection databaseConnection;
    protected SQLSyntaxDocument document;
    boolean changed = false;
    boolean autocompleteOnlyHotKey = true;

    private boolean doCaretUpdate;

    /**
     * The current font width for painting
     */
    protected int fontWidth;

    /**
     * The current font height for painting
     */
    protected int fontHeight;
    private DefaultAutoCompletePopupProvider autoCompletePopup;

    protected void setEditorPreferences() {

        setSelectionColor(QueryEditorSettings.getSelectionColour());
        setSelectedTextColor(QueryEditorSettings.getSelectedTextColour());
        setBackground(QueryEditorSettings.getEditorBackground());

        Font font = QueryEditorSettings.getEditorFont();
        setFont(font);

        FontMetrics fm = getFontMetrics(font);
        fontWidth = fm.charWidth('w');
        fontHeight = fm.getHeight();

        boolean tabsToSpaces = QueryEditorSettings.isTabsToSpaces();
        int tabSize = QueryEditorSettings.getTabSize();

        if (!tabsToSpaces) {

            setTabs(tabSize);
        }

        document.setTabsToSpaces(tabsToSpaces);
        setCaretColor(QueryEditorSettings.getCaretColour());
    }

    private void setTabs(int charactersPerTab) {

        int tabWidth = fontWidth * charactersPerTab;

        TabStop[] tabs = new TabStop[10];

        for (int j = 0; j < tabs.length; j++) {

            int tab = j + 1;
            tabs[j] = new TabStop(tab * tabWidth);
        }

        TabSet tabSet = new TabSet(tabs);

        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setTabSet(attributes, tabSet);

        //document.setParagraphAttributes(0, document.getLength(), attributes, true);
    }

    private TokenImpl cloneTokenList(Token t) {

        if (t==null) {
            return null;
        }

        TokenImpl clone = new TokenImpl(t);
        TokenImpl cloneEnd = clone;

        while ((t=t.getNextToken())!=null) {
            TokenImpl temp = new TokenImpl(t);
            cloneEnd.setNextToken(temp);
            cloneEnd = temp;
        }

        return clone;

    }

    Token getTokenForPosition(int cursor)
    {
        TokenImpl tokenList = null;
        TokenImpl lastToken = null;
        Element map = getDocument().getDefaultRootElement();
        int line = map.getElementIndex(cursor);
        Token token = getTokenListForLine(line);
        TokenImpl t = (TokenImpl)getTokenListForLine(line);
        t = cloneTokenList(t);
        if (tokenList==null) {
            tokenList = t;
            lastToken = tokenList;
        }
        else {
            lastToken.setNextToken(t);
        }
        while (lastToken.getNextToken()!=null &&
                lastToken.getNextToken().isPaintable()) {
            lastToken = (TokenImpl)lastToken.getNextToken();
        }

            // Document offset MUST be correct to prevent exceptions
            // in getTokenListFor()
            int docOffs = map.getElement(line).getEndOffset()-1;
            t = new TokenImpl(new char[] { '\n' }, 0,0, docOffs,
                    Token.WHITESPACE, 0);
            lastToken.setNextToken(t);
            lastToken = t;
        if (cursor>=tokenList.getOffset()) {
            while (!tokenList.containsPosition(cursor)) {
                tokenList = (TokenImpl)tokenList.getNextToken();
            }
        }
        // Be careful to check temp for null here.  It is possible that no
        // token contains endOffs, if endOffs is at the end of a line

        return tokenList;
    }

    private void createStyle(int type, Color fcolor,
                             Color bcolor,String fontname,int style,int fontSize,boolean underline) {
        SyntaxScheme syntaxScheme = getSyntaxScheme();
        if(syntaxScheme!=null) {
            syntaxScheme.getStyle(type).foreground = fcolor;
            syntaxScheme.getStyle(type).background = bcolor;
            syntaxScheme.getStyle(type).underline = underline;
            syntaxScheme.getStyle(type).font = new Font(fontname, style, fontSize);
        }
    }

    private void initialiseStyles() {
        int fontSize = SystemProperties.getIntProperty("user", "sqlsyntax.font.size");
        String fontName = SystemProperties.getProperty("user", "sqlsyntax.font.name");

        createStyle(Token.ERROR_CHAR, Color.red, null,fontName,Font.PLAIN,fontSize,false);
        createStyle(Token.RESERVED_WORD_2, Color.blue, null,fontName,Font.PLAIN,fontSize,false);

        // -----------------------------
        // user defined styles
        int fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.multicomment");
        Color color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.multicomment");
        createStyle(Token.COMMENT_MULTILINE, color,  null,fontName,fontStyle,fontSize,false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.normal");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.normal");
        createStyle(Token.IDENTIFIER, color,  null,fontName,fontStyle,fontSize,false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.singlecomment");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.singlecomment");
        createStyle(Token.COMMENT_EOL, color,  null,fontName,fontStyle,fontSize,false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.keyword");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.keyword");
        createStyle(Token.RESERVED_WORD, color,  null,fontName,fontStyle,fontSize,false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.quote");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.quote");
        createStyle(Token.LITERAL_STRING_DOUBLE_QUOTE, color,  null,fontName,fontStyle,fontSize,false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.number");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.number");
        createStyle(Token.LITERAL_BOOLEAN, color,  null,fontName,fontStyle,fontSize,false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.literal");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.literal");
        createStyle(Token.LITERAL_BOOLEAN, color, null, fontName, fontStyle, fontSize, false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.operator");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.operator");
        createStyle(Token.OPERATOR, color, null, fontName, fontStyle, fontSize, false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.dbobjects");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.dbobjects");
        createStyle(Token.PREPROCESSOR, color, null, fontName, fontStyle, fontSize, true);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.number");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.number");
        createStyle(Token.LITERAL_NUMBER_DECIMAL_INT, color, null, fontName, fontStyle, fontSize, false);

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.dbobjects");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.dbobjects");
        createStyle(Token.VARIABLE, color, null, fontName, fontStyle, fontSize, false);
        //setCurrentLineHighlightColor(SystemProperties.getColourProperty("user", "editor.display.linehighlight.colour"));
        setCurrentLineHighlightColor(SystemProperties.getColourProperty("user", "editor.display.linehighlight.colour"));

        color = SystemProperties.getColourProperty("user", "sqlsyntax.colour.datatype");
        fontStyle = SystemProperties.getIntProperty("user", "sqlsyntax.style.datatype");
        createStyle(Token.DATA_TYPE, color, null, fontName, fontStyle, fontSize, false);


    }

    public SQLTextArea(boolean autocompleteOnlyHotKey) {
        this();
        this.autocompleteOnlyHotKey = autocompleteOnlyHotKey;
    }

    public SQLTextArea() {
        super();
        document = new SQLSyntaxDocument(null, tokenMakerFactory, "antlr/sql");
        setDocument(document);
        setSyntaxEditingStyle("antlr/sql");
        initialiseStyles();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isControlDown() || e.getClickCount() > 1) {
                    int cursor = getCaretPosition();
                    Token token = getTokenForPosition(cursor);
                    if (token.getType() == Token.PREPROCESSOR) {
                        String s = token.getLexeme();
                        if (s != null) {
                            s = s.replace("$", "\\$");
                            if (s.startsWith("\"") && s.endsWith("\""))
                                s = s.substring(1, s.length() - 1);
                            TreeFindAction action = new TreeFindAction();
                            SchemaTree tree = ((ConnectionsTreePanel) GUIUtilities.getDockedTabComponent(ConnectionsTreePanel.PROPERTY_KEY)).getTree();
                            action.install(tree);
                            action.findString(tree, s, ((ConnectionsTreePanel) GUIUtilities.getDockedTabComponent(ConnectionsTreePanel.PROPERTY_KEY)).getHostNode(databaseConnection));
                            BaseDialog dialog = new BaseDialog("find", false);
                            JPanel panel = new JPanel();
                            JList jList = action.getResultsList();
                            if (jList.getModel().getSize() == 1) {
                                jList.setSelectedIndex(0);
                                action.listValueSelected((TreePath) jList.getSelectedValue());
                            } else {
                                jList.addPropertyChangeListener(new PropertyChangeListener() {
                                    @Override
                                    public void propertyChange(PropertyChangeEvent evt) {
                                        if (jList.getModel().getSize() == 0)
                                            dialog.finished();
                                    }
                                });
                                JScrollPane scrollPane = new JScrollPane(jList);
                                panel.add(scrollPane);
                                dialog.addDisplayComponent(panel);
                                dialog.display();
                            }
                        }
                    }
                }
            }
        });
        this.autoCompletePopup = new DefaultAutoCompletePopupProvider(databaseConnection, this);
        registerAutoCompletePopup();
        registerFindAction();
        registerReplaceAction();
        registerCommentAction();
    }

    protected void registerCommentAction() {
        InputMap inputMap = getInputMap();

        Object ks = inputMap.get(KeyStroke.getKeyStroke("control SLASH"));
        while (ks != null) {
            inputMap.remove(KeyStroke.getKeyStroke("control SLASH"));
            inputMap = inputMap.getParent();
            if (inputMap == null)
                break;
            ks = inputMap.get(KeyStroke.getKeyStroke("control SLASH"));
        }
        inputMap = getInputMap();
        ks = inputMap.get(KeyStroke.getKeyStroke("typed /"));
        while (ks != null) {
            inputMap.remove(KeyStroke.getKeyStroke("typed /"));
            inputMap = inputMap.getParent();
            if (inputMap == null)
                break;
            ks = inputMap.get(KeyStroke.getKeyStroke("typed /"));
        }
    }

    private void registerFindAction() {
        FindAction findAction = new FindAction(this);

        getActionMap().put(FIND_ACTION_KEY, findAction);
        getInputMap().put((KeyStroke)
                        findAction.getValue(Action.ACCELERATOR_KEY),
                FIND_ACTION_KEY);
    }

    private void registerReplaceAction() {
        ReplaceAction replaceAction = new ReplaceAction(this);

        getActionMap().put(REPLACE_ACTION_KEY, replaceAction);
        getInputMap().put((KeyStroke)
                        replaceAction.getValue(Action.ACCELERATOR_KEY),
                REPLACE_ACTION_KEY);
    }

    private void registerAutoCompletePopup() {


        Action autoCompletePopupAction = autoCompletePopup.getPopupAction();

        getActionMap().put(AUTO_COMPLETE_POPUP_ACTION_KEY, autoCompletePopupAction);
        getInputMap().put((KeyStroke)
                        autoCompletePopupAction.getValue(Action.ACCELERATOR_KEY),
                AUTO_COMPLETE_POPUP_ACTION_KEY);
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {

            }

            @Override
            public void removeUpdate(DocumentEvent e) {

            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed = true;
            }
        });
        addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                if (changed && !autocompleteOnlyHotKey)
                    autoCompletePopupAction.actionPerformed(null);
                changed = false;
            }
        });

    }

    public void deregisterAutoCompletePopup() {

        if (autoCompletePopup != null) {

            Action autoCompletePopupAction = autoCompletePopup.getPopupAction();

            getActionMap().remove(AUTO_COMPLETE_POPUP_ACTION_KEY);
            getInputMap().remove((KeyStroke)
                    autoCompletePopupAction.getValue(Action.ACCELERATOR_KEY));

            autoCompletePopup = null;
        }

    }

    public void resetAutocomplete() {
        autoCompletePopup.reset();
    }

    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    public void setDatabaseConnection(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
        if (databaseConnection != null)
            setDbobjects(databaseConnection.getListObjectsDB());
        else setDbobjects(new TreeSet<>());
        if (autoCompletePopup != null)
            autoCompletePopup.connectionChanged(databaseConnection);
    }

    protected void setDbobjects(TreeSet<String> dbobjects) {
        SqlLexerTokenMaker maker = (SqlLexerTokenMaker) tokenMakerFactory.getTokenMaker("antlr/sql");
        maker.setDbobjects(dbobjects);
    }
    private KeywordRepository keywords() {

        return (KeywordRepository) RepositoryCache.load(KeywordRepository.REPOSITORY_ID);
    }

    public void deleteAll() {

        try {

            RSyntaxDocument document = (RSyntaxDocument) getDocument();
            document.replace(0, document.getLength(), "", null);

        } catch (BadLocationException badLoc) {
        }

    }

    @Override
    public String getEditorText() {
        return getText();
    }

    @Override
    public JTextComponent getEditorTextComponent() {
        return this;
    }

    public void disableUpdates(boolean disable) {

    }

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public void changeSelectionCase(boolean upper) {
        TextUtilities.changeSelectionCase(this, upper);
    }

    @Override
    public void changeSelectionToCamelCase() {
        TextUtilities.changeSelectionToCamelCase(this);
    }

    @Override
    public void changeSelectionToUnderscore() {
        TextUtilities.changeSelectionToUnderscore(this);
    }

    @Override
    public void deleteLine() {
        TextUtilities.deleteLine(this);
    }

    @Override
    public void deleteWord() {
        TextUtilities.deleteWord(this);
    }

    @Override
    public void deleteSelection() {
        TextUtilities.deleteSelection(this);
    }

    @Override
    public void insertFromFile() {
        TextUtilities.insertFromFile(this);
    }

    @Override
    public void insertLineAfter() {
        TextUtilities.insertLineAfter(this);
    }

    @Override
    public void insertLineBefore() {
        TextUtilities.insertLineBefore(this);
    }

    @Override
    public void selectNone() {
        TextUtilities.selectNone(this);
    }

    public void setSQLKeywords(boolean reset) {
        document.setSQLKeywords(keywords().getSQLKeywords());
    }

    public SQLSyntaxDocument getSQLSyntaxDocument() {

        return document;
    }

    @Override
    public String getDisplayName() {
        return Constants.EMPTY;
    }

    @Override
    public int save(boolean saveAs) {
        return TextUtilities.save(this);
    }

    @Override
    public boolean contentCanBeSaved() {
        return false;
    }

    @Override
    public boolean canPrint() {
        return true;
    }

    @Override
    public Printable getPrintable() {
        return new TextPrinter(getText());
    }

    @Override
    public String getPrintJobName() {
        return "Red Expert";
    }

    protected Element getElementMap() {
        return getDocument().getDefaultRootElement();
    }

    protected int getRowAt(int position) {
        Element map = getElementMap();
        return map.getElementIndex(position);
    }


    /**
     * Returns the start offset of the specified row.
     *
     * @param row - the row
     * @return the start offset of row
     */
    protected int getRowStartOffset(int row) {
        try {
            return getElementMap().getElement(row).getStartOffset();
        } catch (Exception e) { // where row passed is dumb value
            return -1;
        }
    }

    /**
     * Returns the end offset of the specified row.
     *
     * @param row - the row
     * @return the end offset of row
     */
    protected int getRowEndOffset(int row) {
        try {
            return getElementMap().getElement(row).getEndOffset();
        } catch (Exception e) { // where row passed is dumb value
            return -1;
        }
    }

    /**
     * Returns the start offset of the specified row.
     *
     * @param row - a row in the editor
     * @return the start offset of row
     */
    protected int getRowPosition(int row) {
        try {
            return getElementMap().getElement(row).getStartOffset();
        } catch (NullPointerException nullExc) { // TODO: WTF????
            return -1;
        }
    }

    public String getTextAtRow(int rowNumber) {

        Element line = getElementMap().getElement(rowNumber);

        int startOffset = line.getStartOffset();
        int endOffset = line.getEndOffset();
        try {

            return getText(startOffset, (endOffset - startOffset));

        } catch (BadLocationException e) {

            e.printStackTrace();
            return null;
        }
    }


    private void fireTextUpdateStarting() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    private void fireTextUpdateFinished() {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    public void insertTextAtOffset(int offset, String text) {
        try {
            fireTextUpdateStarting();

            try {
                // clear the contents of we have any
                int length = document.getLength();

                if (offset > length || offset < 0) {
                    offset = 0;
                }

                document.insertString(offset, text, null);

            } catch (BadLocationException e) {
            }

            setDocument(document);

        } finally {

            fireTextUpdateFinished();
            setCaretPosition(offset);
        }
    }

    class CommentAction extends RecordableTextAction {


        public CommentAction() {
            super("RSTA.ToggleCommentAction");
        }

        @Override
        public void actionPerformedImpl(ActionEvent actionEvent, RTextArea rTextArea) {
            int selectionStart = getSelectionStart();
            int selectionEnd = getSelectionEnd();

            boolean singleRow = (selectionStart == selectionEnd);

            int startRow = getRowAt(selectionStart);
            int endRow = getRowAt(selectionEnd);

            int endRowStartIndex = getRowStartOffset(endRow);
            if (!singleRow && selectionEnd == endRowStartIndex) {

                endRow--;
            }

            try {

                if (rowsHaveComments(startRow, endRow, true)) {

                    removeCommentFromRows(startRow, endRow);

                } else if (rowsHaveComments(startRow, endRow, false)) {

                    if (singleRow) {

                        removeCommentFromRows(startRow, endRow);

                    } else {

                        // if any one row of a multi-row selection has
                        // a comment, comment the rest also

                        addCommentToRows(startRow, endRow);
                    }

                } else {

                    addCommentToRows(startRow, endRow);
                }

            } catch (BadLocationException e) {

                // nothing we can do here

                e.printStackTrace();
            }

            if (!singleRow) {

                setSelectionStart(getRowStartOffset(startRow));
                setSelectionEnd(getRowEndOffset(endRow));
            }

        }

        @Override
        public String getMacroID() {
            return null;
        }

        private boolean rowsHaveComments(int startRow, int endRow, boolean allRows) {

            Matcher matcher = sqlCommentMatcher();

            for (int i = startRow; i <= endRow; i++) {

                String text = getTextAtRow(i);

                matcher.reset(text);

                if (matcher.find()) {

                    if (!allRows) {

                        return true;

                    }

                } else if (allRows) {

                    return false;
                }

            }

            return allRows;
        }

        private Matcher sqlCommentMatcher;

        private Matcher sqlCommentMatcher() {

            if (sqlCommentMatcher == null) {

                sqlCommentMatcher = Pattern.compile(SQL_COMMENT_REGEX).matcher("");
            }

            return sqlCommentMatcher;
        }

        private void removeCommentFromRows(int startRow, int endRow) throws BadLocationException {

            Document document = getDocument();

            Matcher matcher = sqlCommentMatcher();

            for (int i = startRow; i <= endRow; i++) {

                String text = getTextAtRow(i);

                matcher.reset(text);

                if (matcher.find()) {

                    // retrieve the exact index of '--' since
                    // matcher will return first whitespace

                    int index = text.indexOf(SQL_COMMENT);
                    int startOffset = getRowPosition(i);

                    document.remove(startOffset + index, 2);
                }

            }

        }

        private void addCommentToRows(int startRow, int endRow) {

            Matcher matcher = sqlCommentMatcher();
            for (int i = startRow; i <= endRow; i++) {

                String text = getTextAtRow(i);
                matcher.reset(text);

                if (!matcher.find()) {

                    int index = getRowStartOffset(i);
                    insertTextAtOffset(index, SQL_COMMENT);
                }

            }

        }
    }
}
