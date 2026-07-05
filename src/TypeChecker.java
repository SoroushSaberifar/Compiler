import java.util.*;
import org.antlr.v4.runtime.ParserRuleContext;
import grammar.javaMinusMinus2Parser;

public class TypeChecker {
    private final List<String> errors = new ArrayList<>();
    private final SymbolTable globalTable;

    public TypeChecker(SymbolTable globalTable) {
        this.globalTable = globalTable;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getExpressionType(javaMinusMinus2Parser.ExpressionContext ctx,
            SymbolTable currentScope) {
        if (ctx == null)
            return "unknown";

        String leftType = getPrimaryExpressionType(ctx.primaryExpression(), currentScope);
        return getExpressionPrimeType(ctx.expressionPrime(), leftType, currentScope);
    }

    private String getExpressionPrimeType(javaMinusMinus2Parser.ExpressionPrimeContext ctx,
            String leftType, SymbolTable currentScope) {
        if (ctx == null || ctx instanceof javaMinusMinus2Parser.EmptyExprTailContext)
            return leftType;

        if (ctx instanceof javaMinusMinus2Parser.ArrayAccessExprContext) {
            javaMinusMinus2Parser.ArrayAccessExprContext c = (javaMinusMinus2Parser.ArrayAccessExprContext) ctx;

            String indexType = getExpressionType(c.expression(), currentScope);
            if (!indexType.equals("int") && !indexType.equals("unknown")) {
                error(currentScope, ctx,
                        "Array index must be an integer, found: " + indexType);
            } else {
                checkArrayBounds(c.expression(), currentScope);
            }

            String resultType = "unknown";
            if (leftType.endsWith("[]")) {
                resultType = leftType.substring(0, leftType.length() - 2);
            } else if (!leftType.equals("unknown")) {
                error(currentScope, ctx,
                        "Subscript operator [ ] cannot be applied to non-array type: " + leftType);
            }
            return getExpressionPrimeType(c.expressionPrime(), resultType, currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.ArrayLengthExprContext) {
            javaMinusMinus2Parser.ArrayLengthExprContext c = (javaMinusMinus2Parser.ArrayLengthExprContext) ctx;
            if (!leftType.endsWith("[]") && !leftType.equals("unknown")) {
                error(currentScope, ctx,
                        "Length property is only applicable to arrays, found in: " + leftType);
            }
            return getExpressionPrimeType(c.expressionPrime(), "int", currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.MethodCallExprContext) {
            javaMinusMinus2Parser.MethodCallExprContext c = (javaMinusMinus2Parser.MethodCallExprContext) ctx;

            String methodName = c.getChild(1).getText();

            List<String> argTypes = new ArrayList<>();
            if (c.expression() != null) {
                for (javaMinusMinus2Parser.ExpressionContext e : c.expression()) {
                    argTypes.add(getExpressionType(e, currentScope));
                }
            }
            String returnType = resolveMethodCall(leftType, methodName, argTypes, c, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), returnType, currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.PowExprContext)
            return arithmetic(leftType, ((javaMinusMinus2Parser.PowExprContext) ctx).primaryExpression(), "^", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.MulExprContext)
            return arithmetic(leftType, ((javaMinusMinus2Parser.MulExprContext) ctx).primaryExpression(), "*", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.DivExprContext)
            return arithmetic(leftType, ((javaMinusMinus2Parser.DivExprContext) ctx).primaryExpression(), "/", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.ModExprContext)
            return arithmetic(leftType, ((javaMinusMinus2Parser.ModExprContext) ctx).primaryExpression(), "%", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.AddExprContext)
            return arithmetic(leftType, ((javaMinusMinus2Parser.AddExprContext) ctx).primaryExpression(), "+", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.SubExprContext)
            return arithmetic(leftType, ((javaMinusMinus2Parser.SubExprContext) ctx).primaryExpression(), "-", ctx,
                    currentScope);

        if (ctx instanceof javaMinusMinus2Parser.LtExprContext)
            return relational(leftType, ((javaMinusMinus2Parser.LtExprContext) ctx).primaryExpression(), "<", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.LeExprContext)
            return relational(leftType, ((javaMinusMinus2Parser.LeExprContext) ctx).primaryExpression(), "<=", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.GtExprContext)
            return relational(leftType, ((javaMinusMinus2Parser.GtExprContext) ctx).primaryExpression(), ">", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.GeExprContext)
            return relational(leftType, ((javaMinusMinus2Parser.GeExprContext) ctx).primaryExpression(), ">=", ctx,
                    currentScope);

        if (ctx instanceof javaMinusMinus2Parser.EqExprContext)
            return equality(leftType, ((javaMinusMinus2Parser.EqExprContext) ctx).primaryExpression(), "==", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.NeqExprContext)
            return equality(leftType, ((javaMinusMinus2Parser.NeqExprContext) ctx).primaryExpression(), "!=", ctx,
                    currentScope);

        if (ctx instanceof javaMinusMinus2Parser.AndExprContext)
            return logical(leftType, ((javaMinusMinus2Parser.AndExprContext) ctx).primaryExpression(), "&&", ctx,
                    currentScope);
        if (ctx instanceof javaMinusMinus2Parser.OrExprContext)
            return logical(leftType, ((javaMinusMinus2Parser.OrExprContext) ctx).primaryExpression(), "||", ctx,
                    currentScope);

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
        if (ctx instanceof javaMinusMinus2Parser.IntArrayLiteralExprContext)
            return "int[]";

        if (ctx instanceof javaMinusMinus2Parser.ThisExprContext) {
            SymbolTable classScope = currentScope.getCurrentClassScope();
            return (classScope != null) ? classScope.getScopeName() : "unknown";
        }

        if (ctx instanceof javaMinusMinus2Parser.IdentExprContext) {
            String name = ctx.getText();

            SymbolInfo sym = currentScope.Lookup(name);
            if (sym != null) {
                if (sym.symbolType == SymbolInfo.SymbolType.VARIABLE && !sym.isInitialized) {
                    error(currentScope, ctx, "Variable '" + name + "' may be used before initialization");
                }
                return (sym.dataType != null) ? sym.dataType : "unknown";
            }
            error(currentScope, ctx, "Variable '" + name + "' is undeclared in this scope");
            return "unknown";
        }

        if (ctx instanceof javaMinusMinus2Parser.NotExprContext) {
            String t = getPrimaryExpressionType(
                    ((javaMinusMinus2Parser.NotExprContext) ctx).primaryExpression(), currentScope);
            if (!t.equals("boolean") && !t.equals("unknown")) {
                error(currentScope, ctx, "Operator '!' requires boolean operand, found: " + t);
            }
            return "boolean";
        }
        if (ctx instanceof javaMinusMinus2Parser.UnaryMinusExprContext) {
            String t = getPrimaryExpressionType(
                    ((javaMinusMinus2Parser.UnaryMinusExprContext) ctx).primaryExpression(), currentScope);
            if (!t.equals("int") && !t.equals("unknown")) {
                error(currentScope, ctx, "Unary '-' requires int operand, found: " + t);
            }
            return "int";
        }

        if (ctx instanceof javaMinusMinus2Parser.NewObjectExprContext) {
            javaMinusMinus2Parser.NewObjectExprContext c = (javaMinusMinus2Parser.NewObjectExprContext) ctx;

            String className = c.getChild(1).getText();

            SymbolInfo classInfo = currentScope.Lookup(className);
            if (classInfo == null || classInfo.symbolType != SymbolInfo.SymbolType.CLASS) {
                error(currentScope, ctx, "Cannot instantiate unknown class: " + className);
                return "unknown";
            }
            if (classInfo.isAbstract) {
                error(currentScope, ctx, "Cannot instantiate abstract class: " + className);
            }

            List<String> argTypes = new ArrayList<>();
            if (c.expression() != null) {
                for (javaMinusMinus2Parser.ExpressionContext e : c.expression()) {
                    argTypes.add(getExpressionType(e, currentScope));
                }
            }
            checkConstructorCall(className, argTypes, c, currentScope);
            return className;
        }

        if (ctx instanceof javaMinusMinus2Parser.NewArrayExprContext) {
            javaMinusMinus2Parser.NewArrayExprContext c = (javaMinusMinus2Parser.NewArrayExprContext) ctx;
            String sizeType = getExpressionType(c.expression(), currentScope);
            if (!sizeType.equals("int") && !sizeType.equals("unknown")) {
                error(currentScope, ctx,
                        "Array allocation size must be an integer, found: " + sizeType);
            }
            return c.type().getText() + "[]";
        }

        if (ctx instanceof javaMinusMinus2Parser.ParenExprContext) {
            return getExpressionType(
                    ((javaMinusMinus2Parser.ParenExprContext) ctx).expression(), currentScope);
        }

        return "unknown";
    }

    private String arithmetic(String leftType,
            javaMinusMinus2Parser.PrimaryExpressionContext rightCtx,
            String op, ParserRuleContext ctx, SymbolTable currentScope) {
        String rightType = getPrimaryExpressionType(rightCtx, currentScope);
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            error(currentScope, ctx, String.format(
                    "Operator '%s' requires int operands, found: %s and %s",
                    op, leftType, rightType));
        }
        return "int";
    }

    private String relational(String leftType,
            javaMinusMinus2Parser.PrimaryExpressionContext rightCtx,
            String op, ParserRuleContext ctx, SymbolTable currentScope) {
        String rightType = getPrimaryExpressionType(rightCtx, currentScope);
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            error(currentScope, ctx, String.format(
                    "Operator '%s' requires int operands, found: %s and %s",
                    op, leftType, rightType));
        }
        return "boolean";
    }

    private String equality(String leftType,
            javaMinusMinus2Parser.PrimaryExpressionContext rightCtx,
            String op, ParserRuleContext ctx, SymbolTable currentScope) {
        String rightType = getPrimaryExpressionType(rightCtx, currentScope);
        boolean ok = leftType.equals("unknown") || rightType.equals("unknown")
                || isTypeCompatible(leftType, rightType, currentScope)
                || isTypeCompatible(rightType, leftType, currentScope);
        if (!ok) {
            error(currentScope, ctx, String.format(
                    "Operator '%s' cannot compare incompatible types: %s and %s",
                    op, leftType, rightType));
        }
        return "boolean";
    }

    private String logical(String leftType,
            javaMinusMinus2Parser.PrimaryExpressionContext rightCtx,
            String op, ParserRuleContext ctx, SymbolTable currentScope) {
        String rightType = getPrimaryExpressionType(rightCtx, currentScope);
        if ((!leftType.equals("boolean") && !leftType.equals("unknown"))
                || (!rightType.equals("boolean") && !rightType.equals("unknown"))) {
            error(currentScope, ctx, String.format(
                    "Operator '%s' requires boolean operands, found: %s and %s",
                    op, leftType, rightType));
        }
        return "boolean";
    }

    private boolean isNumeric(String t) {
        return t.equals("int") || t.equals("unknown");
    }

    private String resolveMethodCall(String targetType, String methodName,
            List<String> argTypes, ParserRuleContext ctx, SymbolTable currentScope) {

        if (targetType.equals("unknown"))
            return "unknown";

        SymbolTable classScope;
        SymbolTable myClass = currentScope.getCurrentClassScope();
        if (myClass != null && targetType.equals(myClass.getScopeName())) {
            classScope = myClass;
        } else {
            SymbolInfo targetInfo = currentScope.Lookup(targetType);
            if (targetInfo != null && targetInfo.symbolType == SymbolInfo.SymbolType.IMPORT) {
                return "unknown";
            }
            classScope = currentScope.findTypeScope(targetType);
        }

        if (classScope == null) {
            error(currentScope, ctx, "Cannot resolve class type for object: " + targetType);
            return "unknown";
        }

        Set<String> visited = new HashSet<>();
        SymbolTable search = classScope;
        while (search != null && visited.add(search.getScopeName())) {
            for (SymbolInfo m : search.lookupMethodOverloads(methodName)) {
                if (matchesArguments(m, argTypes, currentScope)) {
                    return (m.dataType != null) ? m.dataType : "void";
                }
            }
            SymbolInfo classInfo = currentScope.Lookup(search.getScopeName());
            search = (classInfo != null && classInfo.parentClass != null)
                    ? currentScope.findTypeScope(classInfo.parentClass)
                    : null;
        }

        error(currentScope, ctx, String.format(
                "No method '%s' in class '%s' matches the arguments: %s",
                methodName, classScope.getScopeName(), argTypes));
        return "unknown";
    }

    private void checkConstructorCall(String className, List<String> argTypes,
            ParserRuleContext ctx, SymbolTable currentScope) {
        SymbolTable classScope = currentScope.findTypeScope(className);
        if (classScope == null)
            return;

        List<SymbolInfo> ctors = new ArrayList<>();
        for (SymbolInfo s : classScope.lookupMethodOverloads(className)) {
            if (s.symbolType == SymbolInfo.SymbolType.CONSTRUCTOR)
                ctors.add(s);
        }

        if (ctors.isEmpty()) {
            if (!argTypes.isEmpty()) {
                error(currentScope, ctx, String.format(
                        "Class '%s' has no constructor matching the arguments: %s",
                        className, argTypes));
            }
            return;
        }
        for (SymbolInfo c : ctors) {
            if (matchesArguments(c, argTypes, currentScope))
                return;
        }
        error(currentScope, ctx, String.format(
                "No constructor of class '%s' matches the arguments: %s",
                className, argTypes));
    }

    private boolean matchesArguments(SymbolInfo callable, List<String> argTypes,
            SymbolTable currentScope) {
        if (callable.parameters.size() != argTypes.size())
            return false;
        for (int i = 0; i < argTypes.size(); i++) {
            if (!isTypeCompatible(callable.parameters.get(i).type, argTypes.get(i), currentScope))
                return false;
        }
        return true;
    }

    public boolean isTypeCompatible(String expected, String actual, SymbolTable currentScope) {
        if (expected == null || actual == null)
            return false;
        if (expected.equals(actual))
            return true;
        if (expected.equals("unknown") || actual.equals("unknown"))
            return true;

        if (actual.equals("null")) {
            return !expected.equals("int") && !expected.equals("boolean") && !expected.equals("char");
        }

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(actual);

        while (!queue.isEmpty()) {
            String currentType = queue.poll();

            if (currentType.equals(expected))
                return true;

            if (!visited.add(currentType))
                continue;

            SymbolInfo info = currentScope.Lookup(currentType);
            if (info != null) {
                if (info.parentClass != null) {
                    queue.add(info.parentClass);
                }
                if (info.implementedInterfaces != null) {
                    queue.addAll(info.implementedInterfaces);
                }
            }
        }

        return false;
    }

    private void checkArrayBounds(javaMinusMinus2Parser.ExpressionContext indexCtx,
            SymbolTable currentScope) {
        if (indexCtx == null)
            return;
        if (indexCtx.primaryExpression() instanceof javaMinusMinus2Parser.IntLitExprContext) {
            try {
                int v = Integer.parseInt(indexCtx.primaryExpression().getText().trim());
                if (v < 0) {
                    error(currentScope, indexCtx, "Array index cannot be negative: " + v);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void error(SymbolTable scope, ParserRuleContext ctx, String message) {
        scope.addSemanticError(String.format("Line %d:%d - %s",
                ctx.getStart().getLine(),
                ctx.getStart().getCharPositionInLine(),
                message));
    }
}
