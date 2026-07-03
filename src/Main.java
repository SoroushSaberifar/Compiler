import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;
import grammar.javaMinusMinus2Lexer;
import grammar.javaMinusMinus2Parser;

public class Main {
    private static List<String> errors = new ArrayList<>();
    private static SymbolTable globalTable;

    // نگاشت نام توکن‌ها برای تطابق ۱۰۰٪ با نمونه پروژه
    private static final Map<String, String> TOKEN_NAME_MAP = new HashMap<>();
    static {
        TOKEN_NAME_MAP.put("LP", "LPAREN");
        TOKEN_NAME_MAP.put("RP", "RPAREN");
        TOKEN_NAME_MAP.put("LSB", "LBRACKET");
        TOKEN_NAME_MAP.put("RSB", "RBRACKET");
        TOKEN_NAME_MAP.put("{", "LBRACE");
        TOKEN_NAME_MAP.put("}", "RBRACE");
        TOKEN_NAME_MAP.put(";", "SEMICOLON");
        TOKEN_NAME_MAP.put(",", "COMMA");
        TOKEN_NAME_MAP.put(".", "DOT");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <input-file>");
            return;
        }
        String fileName = args[0];
        String input = readFile(fileName);

        // ۱. اجرای فاز تحلیل لغوی (Lexical Phase)
        tokenizeAndCollect(input);

        if (!errors.isEmpty()) {
            System.out.println("\n===== LEXICAL ERRORS =====");
            for (String err : errors)
                System.out.println(err);
            return;
        }

        // ۲. ساخت جدول نمادها (پاس اول آنالیز معنایی)
        globalTable = new SymbolTable(SymbolTable.ScopeType.GLOBAL, "GLOBAL", null);
        ParseTree tree = buildSymbolTable(input);

        if (!errors.isEmpty()) {
            System.out.println("\n===== SYNTAX ERRORS =====");
            for (String err : errors)
                System.out.println(err);
            return;
        }

        // ۳. اجرای فاز دوم آنالیز معنایی (تطابق انواع داده و شروط)
        globalTable.enableErrorReporting(); // فعال‌سازی ثبت خطاهای معنایی
        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(globalTable);
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(semanticAnalyzer, tree);

        // ۴. چاپ ساختار اسکوپ‌ها مطابق نمونه پروژه
        System.out.println("\nSymbol Table Scope Structure");
        printScopeStructure(globalTable, "GLOBAL");

        // ۵. چاپ جدول نمادهای نهایی به صورت فرمت‌شده
        System.out.println("\nSymbol Table");
        System.out.printf("%-6s %-15s | %-12s | %-10s | %-20s | %-15s%n", "Index", "Name", "Kind", "Type", "Scope",
                "Initial Value");
        System.out.println("-".repeat(85));
        printFlatSymbolTable(globalTable, new int[] { 0 }, "GLOBAL");

        // ۶. بررسی و چاپ نهایی خطاهای معنایی (Semantic Errors)
        if (globalTable.hasSemanticErrors()) {
            System.out.println("\n===== SEMANTIC ERRORS =====");
            for (String semanticErr : globalTable.getSemanticErrors()) {
                System.out.println(semanticErr);
            }
        } else {
            System.out.println("\nSemantic Analysis Passed Successfully (No Errors).");
        }
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

        long tokenCount = tokenStream.getTokens().stream().filter(t -> t.getType() != Token.EOF).count();
        System.out.println("Tokens found: " + tokenCount);

        for (Token token : tokenStream.getTokens()) {
            if (token.getType() != Token.EOF) {
                int type = token.getType();
                String tokenName = lexer.getVocabulary().getSymbolicName(type);

                if (tokenName == null || tokenName.startsWith("'")) {
                    tokenName = token.getText();
                }

                if (TOKEN_NAME_MAP.containsKey(tokenName)) {
                    tokenName = TOKEN_NAME_MAP.get(tokenName);
                } else if ("Identifier".equals(tokenName)) {
                    tokenName = "IDENTIFIER";
                } else if ("StringLiteral".equals(tokenName)) {
                    tokenName = "STRING_LITERAL";
                } else if ("IntegerLiteral".equals(tokenName)) {
                    tokenName = "INTEGER_LITERAL";
                } else if (tokenName != null) {
                    tokenName = tokenName.toUpperCase();
                }

                String text = token.getText().replace("\n", "\\n").replace("\r", "\\r");

                if ("LBRACE".equals(tokenName) || "RBRACE".equals(tokenName) ||
                        "LBRACKET".equals(tokenName) || "RBRACKET".equals(tokenName)) {
                    text = "";
                }

                int line = token.getLine();
                int column = token.getCharPositionInLine() + 1;

                System.out.printf("Token (%s, '%s', %d:%d)%n", tokenName, text, line, column);
            }
        }
    }

    private static ParseTree buildSymbolTable(String input) {
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

        // غیرفعال کردن گزارش خطا در پاس اول برای جلوگیری از تداخل ساخت اسکوپ‌ها
        globalTable.disableErrorReporting();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(builder, tree);

        return tree; // بازگرداندن درخت برای استفاده در پاس دوم (SemanticAnalyzer)
    }

    private static void printScopeStructure(SymbolTable table, String fullPath) {
        System.out.printf("Scope: %s (full: %s)%n", table.getScopeName(), fullPath);
        for (SymbolInfo sym : table.getSymbolsList()) {
            if (sym.symbolType == SymbolInfo.SymbolType.CLASS) {
                System.out.printf("[class] %s (scope: %s)%n", sym.name, fullPath);
            } else if (sym.symbolType == SymbolInfo.SymbolType.METHOD) {
                System.out.printf("[method] %s -> %s (scope: %s)%n", sym.name, sym.dataType, fullPath);
                if (!sym.parameters.isEmpty()) {
                    System.out.println("Parameters:");
                    for (SymbolInfo.ParameterInfo p : sym.parameters) {
                        System.out.printf("  %s: %s%n", p.name, p.type);
                    }
                }
            } else if (sym.symbolType == SymbolInfo.SymbolType.PARAMETER) {
                System.out.printf("[parameter] %s: %s (scope: %s) (local)%n", sym.name, sym.dataType, fullPath);
            }
        }

        for (SymbolTable child : table.getChildScopes()) {
            String childPath = fullPath.equals("GLOBAL") ? child.getScopeName()
                    : fullPath + "::" + child.getScopeName();
            printScopeStructure(child, childPath);
        }
    }

    private static void printFlatSymbolTable(SymbolTable table, int[] index, String fullPath) {
        for (SymbolInfo sym : table.getSymbolsList()) {
            if (sym.symbolType == SymbolInfo.SymbolType.IMPORT)
                continue;

            String kind = sym.getKindString();
            String type = sym.dataType != null ? sym.dataType : "N/A";
            String scope = fullPath;
            String initVal = sym.initialValue != null ? sym.initialValue : "N/A";

            System.out.printf("%-6d %-15s | %-12s | %-10s | %-20s | %-15s%n",
                    index[0]++, sym.name, kind, type, scope, initVal);
        }

        for (SymbolTable child : table.getChildScopes()) {
            String childPath = fullPath.equals("GLOBAL") ? child.getScopeName()
                    : fullPath + "::" + child.getScopeName();
            printFlatSymbolTable(child, index, childPath);
        }
    }

    private static String readFile(String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}