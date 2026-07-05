import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.IOException;
import java.util.*;

import grammar.javaMinusMinus2Lexer;
import grammar.javaMinusMinus2Parser;

public class Main {

    private static final List<String> errors = new ArrayList<>();

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

        // ==================== LEXICAL ANALYSIS ====================
        // خواندن مستقیم فایل با UTF-8، مستقل از سیستم‌عامل (جایگزین
        // readFile/FileReader)
        CharStream stream = CharStreams.fromFileName(args[0]);
        javaMinusMinus2Lexer lexer = new javaMinusMinus2Lexer(stream);

        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object off,
                    int line, int col, String msg, RecognitionException e) {
                // col+1: هماهنگ با شماره‌گذاری یک-مبنای printTokens
                errors.add("Lexical error at " + line + ":" + (col + 1) + " - " + msg);
            }
        });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        printTokens(lexer, tokens);

        // توقف در خطای لغوی — فاز ۱، ص ۴: «در صورت مشاهده توکن‌های نامعتبر پیغام خطای
        // مناسبی چاپ شود»
        if (!errors.isEmpty()) {
            System.out.println("\n===== LEXICAL ERRORS =====");
            errors.forEach(System.out::println);
            return;
        }

        // ==================== SYNTAX ANALYSIS ====================
        javaMinusMinus2Parser parser = new javaMinusMinus2Parser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object off,
                    int line, int col, String msg, RecognitionException e) {
                errors.add("Syntax error at " + line + ":" + (col + 1) + " - " + msg);
            }
        });

        ParseTree tree = parser.program();

        // دفاع دوم: شمارنده داخلی ANTLR هم بررسی می‌شود
        if (!errors.isEmpty() || parser.getNumberOfSyntaxErrors() > 0) {
            System.out.println("\n===== SYNTAX ERRORS =====");
            errors.forEach(System.out::println);
            return;
        }

        ParseTreeWalker walker = new ParseTreeWalker();

        // ==================== PHASE 1: SYMBOL TABLE ====================
        SymbolTable globalTable = new SymbolTable(SymbolTable.ScopeType.GLOBAL, "GLOBAL", null);
        globalTable.enableErrorReporting();

        CompleteSymbolTableBuilder builder = new CompleteSymbolTableBuilder(globalTable);
        walker.walk(builder, tree);

        // خروجی فاز ۱ همین‌جا و قبل از اجرای فاز ۲ چاپ می‌شود (جداسازی واقعی فازها)
        System.out.println("\n================ PHASE 1: SYMBOL TABLE ================");

        System.out.println("\nSymbol Table Scope Structure");
        printScopeStructure(globalTable, "GLOBAL");

        System.out.println("\nSymbol Table");
        System.out.printf("%-6s %-15s | %-12s | %-10s | %-20s | %-15s%n",
                "Index", "Name", "Kind", "Type", "Scope", "Initial");
        System.out.println("-".repeat(85));
        printFlatSymbolTable(globalTable, new int[] { 0 }, "GLOBAL");

        // ==================== PHASE 2: SEMANTIC ANALYSIS ====================
        System.out.println("\n================ PHASE 2: SEMANTIC ANALYSIS ================");

        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(globalTable);
        walker.walk(semanticAnalyzer, tree);

        // جمع‌آوری خطاهای معنایی از هر «سه» منبع
        // LinkedHashSet: حفظ ترتیب کشف + حذف پیام‌های تکراری
        Set<String> semanticErrors = new LinkedHashSet<>();

        if (globalTable.hasSemanticErrors())
            semanticErrors.addAll(globalTable.getSemanticErrors()); // خطاهای تعریف تکراری و... از فاز ساخت جدول

        semanticErrors.addAll(semanticAnalyzer.getErrors()); // خطاهای دسترسی، ارث‌بری، import و...
        semanticErrors.addAll(semanticAnalyzer.getTypeCheckerErrors()); // ❗ خطاهای نوع که قبلاً گم می‌شدند

        if (!semanticErrors.isEmpty()) {
            System.out.println("\n===== SEMANTIC ERRORS =====");
            semanticErrors.forEach(System.out::println);
        } else {
            System.out.println("\nSemantic Analysis Passed Successfully (No Errors).");
        }
    }

    // ==================== TOKEN OUTPUT ====================
    private static void printTokens(javaMinusMinus2Lexer lexer, CommonTokenStream tokens) {

        long count = tokens.getTokens().stream()
                .filter(t -> t.getType() != Token.EOF)
                .count();

        System.out.println("Tokens found: " + count);

        for (Token token : tokens.getTokens()) {
            if (token.getType() == Token.EOF)
                continue;

            String name = lexer.getVocabulary().getSymbolicName(token.getType());
            if (name == null || name.startsWith("'"))
                name = token.getText();

            if (TOKEN_NAME_MAP.containsKey(name)) {
                name = TOKEN_NAME_MAP.get(name);
            } else if ("Identifier".equals(name)) {
                name = "IDENTIFIER";
            } else if ("StringLiteral".equals(name)) {
                name = "STRING_LITERAL";
            } else if ("IntegerLiteral".equals(name)) {
                name = "INTEGER_LITERAL";
            } else {
                name = name.toUpperCase();
            }

            String text = token.getText()
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");

            // متن واقعی توکن‌های آکولاد/کروشه حفظ می‌شود
            // (فاز ۱، ص ۴: «خروجی به صورت خوانا و فرمت شده ارائه شود»)

            int line = token.getLine();
            int col = token.getCharPositionInLine() + 1;

            System.out.printf("Token (%s, '%s', %d:%d)%n", name, text, line, col);
        }
    }

    // ==================== PHASE 1 OUTPUT HELPERS ====================
    // چاپ ساختار درختی Scopeها (فاز ۱، ص ۲: رابطه درختی جداول علائم)
    private static void printScopeStructure(SymbolTable table, String path) {

        System.out.printf("Scope: %s (full: %s)%n", table.getScopeName(), path);

        for (SymbolInfo info : table.getAllSymbols()) {
            switch (info.symbolType) {
                case CLASS:
                    System.out.printf("[class] %s (scope: %s)%n", info.name, path);
                    break;
                case METHOD:
                    System.out.printf("[method] %s -> %s (scope: %s)%n",
                            info.name, info.dataType, path);
                    if (!info.parameters.isEmpty()) {
                        System.out.println("Parameters:");
                        for (SymbolInfo.ParameterInfo p : info.parameters)
                            System.out.printf("   %s: %s%n", p.name, p.type);
                    }
                    break;
                case PARAMETER:
                    System.out.printf("[parameter] %s: %s (scope: %s)%n",
                            info.name, info.dataType, path);
                    break;
                default:
                    break;
            }
        }

        for (SymbolTable child : table.getChildScopes()) {
            String childPath = path.equals("GLOBAL")
                    ? child.getScopeName()
                    : path + "::" + child.getScopeName();
            printScopeStructure(child, childPath);
        }
    }

    // جدول تخت با ستون‌های الزامی فاز ۱، ص ۳: نام، نوع شناسه، نوع داده، حوزه، مقدار
    // اولیه
    private static void printFlatSymbolTable(SymbolTable table, int[] idx, String path) {

        for (SymbolInfo info : table.getAllSymbols()) {
            if (info.symbolType == SymbolInfo.SymbolType.IMPORT)
                continue;

            String kind = info.getKindString();
            String type = info.dataType != null ? info.dataType : "N/A";
            String init = info.initialValue != null ? info.initialValue : "N/A";

            System.out.printf("%-6d %-15s | %-12s | %-10s | %-20s | %-15s%n",
                    idx[0]++, info.name, kind, type, path, init);
        }

        for (SymbolTable child : table.getChildScopes()) {
            String childPath = path.equals("GLOBAL")
                    ? child.getScopeName()
                    : path + "::" + child.getScopeName();
            printFlatSymbolTable(child, idx, childPath);
        }
    }
}
