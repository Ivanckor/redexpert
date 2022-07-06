package org.underworldlabs.sqlLexer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.misc.Triple;
import org.fife.ui.rsyntaxtextarea.Token;
import org.underworldlabs.antrlExtentionRsyntxtextarea.AntlrTokenMaker;
import org.underworldlabs.antrlExtentionRsyntxtextarea.MultiLineTokenInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;


public class SqlLexerTokenMaker extends AntlrTokenMaker {

    public final static int DB_OBJECT = 999;
    public final static int PAIR_OBJECT = 998;


    public SqlLexerTokenMaker() {
        super(new MultiLineTokenInfo(0, Token.COMMENT_MULTILINE, "/*", "*/"),
                new MultiLineTokenInfo(0, Token.LITERAL_STRING_DOUBLE_QUOTE, "'", "'"));
    }

    TreeSet<String> dbobjects;

    @Override
    protected int convertType(int i) {
        switch (i) {
            case SqlLexer.KEYWORD:
                return Token.RESERVED_WORD;
            case SqlLexer.DATATYPE_SQL:
                return Token.DATA_TYPE;
            case SqlLexer.MULTILINE_COMMENT:
                return Token.COMMENT_MULTILINE;
            case SqlLexer.SINGLE_LINE_COMMENT:
                return Token.COMMENT_EOL;
            case SqlLexer.OPERATOR:
            case SqlLexer.UNARY_OPERATOR:
                return Token.OPERATOR;
            case SqlLexer.STRING_LITERAL:
                return Token.LITERAL_STRING_DOUBLE_QUOTE;
            case SqlLexer.PART_OBJECT:
                return Token.VARIABLE;
            case SqlLexer.LINTERAL_VALUE:
                return Token.LITERAL_BOOLEAN;
            case DB_OBJECT:
                return Token.PREPROCESSOR;
            case PAIR_OBJECT:
                return Token.MARKUP_CDATA;
            case SqlLexer.NUMERIC_LITERAL:
                return Token.LITERAL_NUMBER_DECIMAL_INT;
            case SqlLexer.ERROR_CHAR:
                return Token.ERROR_IDENTIFIER;
            default:
                return Token.IDENTIFIER;
        }
    }

    @Override
    protected org.antlr.v4.runtime.Token convertToken(org.antlr.v4.runtime.Token token,int startOffset) {
        if(token.getType()==SqlLexer.IDENTIFIER)
        {
            if (dbobjects != null) {
                String x = token.getText();
                if (x.length() > 0 && x.charAt(0) > 'A' && x.charAt(0) < 'z')
                    x = x.toUpperCase();
                if (x.startsWith("\"") && x.endsWith("\"") && x.length() > 1)
                    x = x.substring(1, x.length() - 1);
                if (dbobjects.contains(x)) {
                    CustomToken customToken = new CustomToken(token);
                    customToken.setType(DB_OBJECT);
                    return customToken;
                }
            }
        }

        //The following condition transfers variables to the original form
        if(startOffset==0&& token.getStartIndex()==0){
            firstObjectCount =0;
            //SecondObjectCount =0;
            show1 = false;
            //show2 = false;
            //show3 = true;
            //tr1 = new ArrayList<>();
            maxx=0;
            NumberOfSelectedObject=0;

        }

        //Search for paired objects
        if("(".compareToIgnoreCase(token.getText())==0){
            firstObjectCount++;
            CustomToken customToken = new CustomToken(token);
            //tr1.add(customToken);
            if(token.getStartIndex() + startOffset <= caretPosition && token.getStopIndex() + startOffset+1 >= caretPosition){
                firstObjectCount=1;
                show1=true;
                customToken.setType(PAIR_OBJECT);
                return customToken;
            }
        }
        if(")".compareToIgnoreCase(token.getText())==0){
            firstObjectCount--;
            CustomToken customToken = new CustomToken(token);
            //tr1.add(customToken);
            if(token.getStartIndex() + startOffset <= caretPosition && token.getStopIndex() + startOffset+1 >= caretPosition){

                customToken.setType(PAIR_OBJECT);
                return customToken;
            }
            if(firstObjectCount ==0&& show1){
                show1=false;
                customToken.setType(PAIR_OBJECT);
                return customToken;
            }
        }
        //
        /*The next part of the code should check the inverted list of processed
        objects and select a pair if the carriage is on the closing object,
        but it is not possible to return the desired token from the cycle
        */
        /*
        if(maxx<=token.getStopIndex()&& startOffset!=0)
        {maxx=token.getStopIndex();
            show3=false;
        }
        else {
            show3=true;
        }
        CustomToken customToken = new CustomToken( tr1.get(0));
        if (show3) {
            show3=false;
            Collections.reverse(tr1);

            for (int l = 0; l < tr1.size(); l++) {
                customToken = new CustomToken( tr1.get(l));
                if ("end".compareToIgnoreCase(tr1.get(l).getText()) == 0) {
                    SecondObjectCount++;
                    if (tr1.get(l).getStartIndex()  <= caretPosition && tr1.get(l).getStopIndex() >= caretPosition) {
                        SecondObjectCount = 1;
                        show2 = true;
                    }
                }
                if ("begin".compareToIgnoreCase(tr1.get(l).getText()) == 0) {
                    SecondObjectCount--;

                    if (SecondObjectCount == 0 && show2) {
                        show2 = false;
                        customToken.setType(PAIR_OBJECT);
                        return customToken;// footnote: use it
                    }
                }

            }

        }
        if(show2==false){
            return customToken; // footnote: or use it
        }
        */

        return token;
    }


    public boolean show1 =true;
    public boolean show2=true;
    public boolean show3=true;
    public int firstObjectCount =0;
    //public int SecondObjectCount =0;
    public Triple<String,Integer,Integer> uo;
    public List<Triple<String,Integer,Integer>> pp;
    public List<CustomToken> tr1;
    public int NumberOfSelectedObject =0;
    public int maxx;

    public TreeSet<String> getDbobjects() {
        return dbobjects;
    }

    public void setDbobjects(TreeSet<String> dbobjects) {
        this.dbobjects = dbobjects;
    }

    @Override
    protected Lexer createLexer(String s) {
        return new SqlLexer(CharStreams.fromString(s));
    }
    int caretPosition;

    public int getCaretPosition() {
        return caretPosition;
    }

    public void setCaretPosition(int caretPosition) {
        this.caretPosition = caretPosition;
    }


}
