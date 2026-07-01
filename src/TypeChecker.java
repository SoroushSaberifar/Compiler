import grammar.javaMinusMinus2Parser;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.util.*;

public class TypeChecker {

    public String getExpressionType(javaMinusMinus2Parser.ExpressionContext ctx, SymbolTable currentScope) {
        if (ctx == null) return "unknown";

        String text = ctx.getText().trim();

        if (text.matches("\\d+")) return "int";
        if (text.equals("true") || text.equals("false")) return "boolean";
        if (text.startsWith("'") && text.endsWith("'")) return "char";
        if (text.startsWith("\"") && text.endsWith("\"")) return "String";
        if (text.equals("null")) return "null";
        if (text.equals("this")) {
            SymbolTable temp = currentScope;
            while (temp != null && temp.getScopeType() != SymbolTable.ScopeType.CLASS) {
                temp = temp.getParent(); 
            }
            return (temp != null) ? temp.getScopeName() : "unknown";
        }

        // ۲. عبارات ریاضی
        if (text.contains("+") || text.contains("-") || text.contains("*") || 
            text.contains("/") || text.contains("%") || text.contains("**")) {
            
            if (ctx.primaryExpression() != null && ctx.expressionPrime() != null) {
                String leftType = getPrimaryExpressionType(ctx.primaryExpression(), currentScope);
                if (!ctx.expressionPrime().getText().isEmpty() && !leftType.equals("int")) {
                    currentScope.addSemanticError(String.format(
                        "Line %d:%d - Type mismatch in arithmetic operation near: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), text
                    ));
                }
            }
            return "int";
        }

        if (text.contains("&&") || text.contains("||") || text.startsWith("!")) return "boolean";
        if (text.contains("<") || text.contains(">") || text.contains("==") || text.contains("!=")) return "boolean";

        if (text.contains(".length")) return "int";

        if (text.startsWith("new")) {
            if (text.contains("[") && text.contains("]")) {
                int firstBracket = text.indexOf("[");
                String baseType = text.substring(3, firstBracket).trim();
                return baseType + "[]";
            } else if (text.contains("(") && text.contains(")")) {
                int firstParen = text.indexOf("(");
                return text.substring(3, firstParen).trim();
            }
        }

        if (text.startsWith("(") && text.endsWith(")") && ctx.primaryExpression() != null) {
            return getPrimaryExpressionType(ctx.primaryExpression(), currentScope);
        }

        if (text.contains("[") && text.contains("]")) {
            int firstBracket = text.indexOf("[");
            int lastBracket = text.lastIndexOf("]");
            if (firstBracket != -1 && lastBracket > firstBracket) {
                String indexContent = text.substring(firstBracket + 1, lastBracket).trim();
                String arrayName = text.substring(0, firstBracket).trim();
                SymbolInfo arraySymbol = currentScope.lookup(arrayName);
                
                if (indexContent.matches("-?\\d+")) {
                    int indexVal = Integer.parseInt(indexContent);
                    if (arraySymbol != null && arraySymbol.isArray) {
                        if (indexVal < 0 || (arraySymbol.arraySize != -1 && indexVal >= arraySymbol.arraySize)) {
                            currentScope.addSemanticError(String.format(
                                "Line %d:%d - Array index out of bounds: %d (Array size: %d)",
                                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), indexVal, arraySymbol.arraySize
                            ));
                        }
                    }
                }
                if (arraySymbol != null && arraySymbol.dataType.endsWith("[]")) {
                    return arraySymbol.dataType.substring(0, arraySymbol.dataType.length() - 2);
                }
            }
        }

        if (ctx.primaryExpression() != null) {
            return getPrimaryExpressionType(ctx.primaryExpression(), currentScope);
        }

        SymbolInfo varSymbol = currentScope.lookup(text);
        if (varSymbol != null) {
            return varSymbol.dataType;
        }

        return "unknown";
    }

    private String getPrimaryExpressionType(javaMinusMinus2Parser.PrimaryExpressionContext ctx, SymbolTable currentScope) {
        if (ctx == null) return "unknown";
        String text = ctx.getText().trim();

        if (text.matches("\\d+")) return "int";
        if (text.equals("true") || text.equals("false")) return "boolean";
        if (text.startsWith("'")) return "char";
        if (text.startsWith("\"")) return "String";
        if (text.equals("null")) return "null";

        SymbolInfo sym = currentScope.lookup(text);
        if (sym != null) {
            return sym.dataType;
        }

        return "unknown";
    }
}