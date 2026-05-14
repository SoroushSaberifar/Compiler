import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;
import grammar.javaMinusMinus2Lexer;
import grammar.javaMinusMinus2Parser;

public class Main {
    private static List<String> errors = new ArrayList<>();
    private static SymbolTable globalTable;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <input-file>");
            return;
        }
        String fileName = args[0];
        String input = readFile(fileName);
        System.out.println("===== TOKEN OUTPUT =====");
        tokenizeAndCollect(input);
        if (!errors.isEmpty()) {
            System.out.println("\n===== ERRORS =====");
            for (String err : errors)
                System.out.println(err);
            return;
        }
        System.out.println("\n===== SYMBOL TABLE =====");
        globalTable = new SymbolTable(SymbolTable.ScopeType.GLOBAL, null, null);
        buildSymbolTable(input);
        globalTable.printTree(0);
    }

    private static void tokenizeAndCollect(String input) {
        CharStream stream = CharStreams.fromString(input);
        javaMinusMinus2Lexer lexer = new javaMinusMinus2Lexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                    int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add(String.format("Lexical error at %d:%d - %s", line, charPositionInLine, msg));
            }
        });
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        for (Token token : tokenStream.getTokens()) {
            if (token.getType() != Token.EOF) {
                int type = token.getType();
                String tokenName = lexer.getVocabulary().getDisplayName(type);
                if (tokenName.startsWith("'"))
                    tokenName = token.getText();
                String text = token.getText().replace("\n", "\\n").replace("\r", "\\r");
                System.out.println(tokenName + " : " + text);
            }
        }
    }

    private static void buildSymbolTable(String input) {
        CharStream stream = CharStreams.fromString(input);
        javaMinusMinus2Lexer lexer = new javaMinusMinus2Lexer(stream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        javaMinusMinus2Parser parser = new javaMinusMinus2Parser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                    int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add(String.format("Syntax error at %d:%d - %s", line, charPositionInLine, msg));
            }
        });
        ParseTree tree = parser.program();
        CompleteSymbolTableBuilder builder = new CompleteSymbolTableBuilder(globalTable);
        globalTable.disableErrorReporting();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(builder, tree);
    }

    private static String readFile(String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line).append("\n");
        }
        return sb.toString();
    }
}