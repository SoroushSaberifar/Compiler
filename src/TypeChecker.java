import grammar.javaMinusMinus2Parser;
import java.util.*;
import org.antlr.v4.runtime.ParserRuleContext;

public class TypeChecker {

    public String getExpressionType(javaMinusMinus2Parser.ExpressionContext ctx, SymbolTable currentScope) {
        if (ctx == null)
            return "unknown";

        String leftType = getPrimaryExpressionType(ctx.primaryExpression(), currentScope);

        if (ctx.expressionPrime() == null || ctx.expressionPrime().getText().isEmpty()) {
            return leftType;
        }

        return getExpressionPrimeType(ctx.expressionPrime(), leftType, currentScope);
    }

    private String getExpressionPrimeType(javaMinusMinus2Parser.ExpressionPrimeContext ctx, String leftType,
            SymbolTable currentScope) {
        if (ctx == null || ctx.getText().isEmpty())
            return leftType;

        if (ctx instanceof javaMinusMinus2Parser.ArrayAccessExprContext) {
            javaMinusMinus2Parser.ArrayAccessExprContext arrayCtx = (javaMinusMinus2Parser.ArrayAccessExprContext) ctx;
            javaMinusMinus2Parser.ExpressionContext indexCtx = arrayCtx.expression();

            String indexType = getExpressionType(indexCtx, currentScope);
            if (!indexType.equals("int")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Array index must be an integer, found: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), indexType));
            } else {

                checkArrayBounds(indexCtx, currentScope);
            }

            String resultType = "unknown";
            if (leftType.endsWith("[]")) {
                resultType = leftType.substring(0, leftType.length() - 2);
            } else if (!leftType.equals("unknown")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Subscript operator [ ] cannot be applied to non-array type: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), leftType));
            }

            return getExpressionPrimeType(arrayCtx.expressionPrime(), resultType, currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.ArrayLengthExprContext) {
            javaMinusMinus2Parser.ArrayLengthExprContext lenCtx = (javaMinusMinus2Parser.ArrayLengthExprContext) ctx;
            if (!leftType.endsWith("[]") && !leftType.equals("unknown")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Length property is only applicable to arrays, found in: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), leftType));
            }
            return getExpressionPrimeType(lenCtx.expressionPrime(), "int", currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.MethodCallExprContext) {
            javaMinusMinus2Parser.MethodCallExprContext callCtx = (javaMinusMinus2Parser.MethodCallExprContext) ctx;
            String methodName = callCtx.Identifier().getText();

            List<String> actualArgTypes = new ArrayList<>();
            if (callCtx.expression() != null) {
                for (javaMinusMinus2Parser.ExpressionContext expr : callCtx.expression()) {
                    actualArgTypes.add(getExpressionType(expr, currentScope));
                }
            }

            String returnType = resolveMethodCall(leftType, methodName, actualArgTypes, callCtx, currentScope);

            return getExpressionPrimeType(callCtx.expressionPrime(), returnType, currentScope);
        }

        return leftType;
    }

    public String getPrimaryExpressionType(javaMinusMinus2Parser.PrimaryExpressionContext ctx,
            SymbolTable currentScope) {
        if (ctx == null)
            return "unknown";

        if (ctx instanceof javaMinusMinus2Parser.IntLitExprContext)
            return "int";
        if (ctx instanceof javaMinusMinus2Parser.BoolLitExprContext)
            return "boolean";
        if (ctx instanceof javaMinusMinus2Parser.CharLitExprContext)
            return "char";
        if (ctx instanceof javaMinusMinus2Parser.StringLitExprContext)
            return "String";
        if (ctx instanceof javaMinusMinus2Parser.NullLitExprContext)
            return "null";

        if (ctx instanceof javaMinusMinus2Parser.ThisExprContext) {
            SymbolTable classScope = currentScope.getCurrentClassScope();
            return (classScope != null) ? classScope.getScopeName() : "unknown";
        }

        if (ctx instanceof javaMinusMinus2Parser.IdentExprContext) {
            String name = ctx.getText();
            SymbolInfo sym = currentScope.lookup(name);
            if (sym != null) {

                if (sym.symbolType == SymbolInfo.SymbolType.VARIABLE && !sym.isInitialized) {

                }
                return sym.dataType;
            }
            currentScope.addSemanticError(String.format(
                    "Line %d:%d - Variable '%s' is undeclared in this scope",
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), name));
            return "unknown";
        }

        if (ctx instanceof javaMinusMinus2Parser.NewArrayExprContext) {
            javaMinusMinus2Parser.NewArrayExprContext newArrCtx = (javaMinusMinus2Parser.NewArrayExprContext) ctx;
            String baseType = newArrCtx.type().getText();

            String sizeType = getExpressionType(newArrCtx.expression(), currentScope);
            if (!sizeType.equals("int")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Array allocation size must be an integer, found: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), sizeType));
            }
            return baseType + "[]";
        }

        if (ctx instanceof javaMinusMinus2Parser.ParenExprContext) {
            return getExpressionType(((javaMinusMinus2Parser.ParenExprContext) ctx).expression(), currentScope);
        }

        return "unknown";
    }

    private String resolveMethodCall(String targetType, String methodName, List<String> actualArgTypes,
            ParserRuleContext ctx, SymbolTable currentScope) {

        if (targetType.equals("unknown")) {
            return "unknown";
        }

        SymbolTable classScope = null;

        if (targetType.equals("this") || (currentScope.getCurrentClassScope() != null
                && targetType.equals(currentScope.getCurrentClassScope().getScopeName()))) {
            classScope = currentScope.getCurrentClassScope();
        } else {

            SymbolInfo targetClassInfo = currentScope.lookup(targetType);
            if (targetClassInfo != null && targetClassInfo.symbolType == SymbolInfo.SymbolType.CLASS) {
                classScope = currentScope.findClassScope(targetType);
            } else {

                SymbolInfo globalImport = currentScope.lookup(targetType);
                if (globalImport != null && globalImport.symbolType == SymbolInfo.SymbolType.IMPORT) {
                    return "unknown";
                }
            }
        }

        if (classScope == null) {
            currentScope.addSemanticError(String.format(
                    "Line %d:%d - Cannot resolve class type for object: %s",
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), targetType));
            return "unknown";
        }

        SymbolTable currentSearchScope = classScope;
        while (currentSearchScope != null) {
            List<SymbolInfo> overloads = currentSearchScope.lookupMethodOverloads(methodName);
            if (!overloads.isEmpty()) {
                for (SymbolInfo method : overloads) {
                    if (method.parameters.size() == actualArgTypes.size()) {
                        boolean match = true;
                        for (int i = 0; i < actualArgTypes.size(); i++) {
                            String paramType = method.parameters.get(i).type;
                            String argType = actualArgTypes.get(i);
                            if (!isTypeCompatible(paramType, argType, currentScope)) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            return method.dataType;
                        }
                    }
                }
            }

            SymbolInfo classInfo = currentScope.lookup(currentSearchScope.getScopeName());
            if (classInfo != null && classInfo.parentClass != null) {
                currentSearchScope = currentScope.findClassScope(classInfo.parentClass);
            } else {
                currentSearchScope = null;
            }
        }

        currentScope.addSemanticError(String.format(
                "Line %d:%d - No method '%s' in class '%s' matches the arguments: %s",
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), methodName, classScope.getScopeName(),
                actualArgTypes));
        return "unknown";
    }

    public boolean isTypeCompatible(String expected, String actual, SymbolTable currentScope) {
        if (expected.equals(actual))
            return true;

        if (actual.equals("null")) {
            return !expected.equals("int") && !expected.equals("boolean") && !expected.equals("char");
        }

        SymbolInfo actualClass = currentScope.lookup(actual);
        if (actualClass != null && actualClass.symbolType == SymbolInfo.SymbolType.CLASS) {
            String parent = actualClass.parentClass;

            Set<String> visited = new HashSet<>();
            visited.add(actual);
            while (parent != null) {
                if (visited.contains(parent)) {

                    return false;
                }
                if (parent.equals(expected)) {
                    return true;
                }
                visited.add(parent);
                SymbolInfo parentClass = currentScope.lookup(parent);
                parent = (parentClass != null) ? parentClass.parentClass : null;
            }
        }

        return false;
    }

    private void checkArrayBounds(javaMinusMinus2Parser.ExpressionContext indexCtx, SymbolTable currentScope) {
        if (indexCtx == null)
            return;

        if (indexCtx.primaryExpression() instanceof javaMinusMinus2Parser.IntLitExprContext) {
            try {
                int indexVal = Integer.parseInt(indexCtx.primaryExpression().getText().trim());
                if (indexVal < 0) {
                    currentScope.addSemanticError(String.format(
                            "Line %d:%d - Array index cannot be negative: %d",
                            indexCtx.getStart().getLine(), indexCtx.getStart().getCharPositionInLine(), indexVal));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}