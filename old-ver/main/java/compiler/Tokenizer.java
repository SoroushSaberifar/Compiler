package CompilerProject.src.main.java.compiler;

import compiler.gen.JavaLexer;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    public static List<TokenInfo> tokenize(String filePath) throws IOException {
        CharStream codeCharStream = CharStreams.fromFileName(filePath);
        JavaLexer lexer = new JavaLexer(codeCharStream);
        List<TokenInfo> tokens = new ArrayList<>();

        for (Token token : lexer.getAllTokens()) {
            String tokenName = JavaLexer.VOCABULARY.getSymbolicName(token.getType());
            String text = token.getText();
            int line = token.getLine();
            int col = token.getCharPositionInLine();

            // نادیده‌گرفتن فاصله و کامنت‌ها
            if (tokenName.equals("WS") || tokenName.equals("COMMENT") || tokenName.equals("LINE_COMMENT"))
                continue;

            // تشخیص دسته
            String category = getCategory(tokenName, text);

            tokens.add(new TokenInfo(line, col, category, text));
        }

        return tokens;
    }

    private static String getCategory(String tokenName, String lexeme) {
        if (tokenName == null) return "Invalid";

        if (tokenName.equals("Identifier")) return "Identifier";
        if (tokenName.contains("LITERAL")) return "Literal";

        // Keywords
        if (tokenName.startsWith("ABSTRACT") || tokenName.startsWith("CLASS") ||
                tokenName.startsWith("PUBLIC") || tokenName.startsWith("STATIC") ||
                tokenName.startsWith("VOID") || tokenName.startsWith("INT") ||
                tokenName.startsWith("BOOLEAN"))
            return "Keyword";

        // Operators
        if (lexeme.matches("[+\\-*/%=!<>|&]+"))
            return "Operator";

        // Delimiters
        if (lexeme.matches("[;,.{}()\\[\\]]"))
            return "Delimiter";

        // Number
        if (lexeme.matches("\\d+(\\.\\d+)?"))
            return "Number";

        return "Unknown";
    }
}

