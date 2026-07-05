import java.util.*;
import org.antlr.v4.runtime.ParserRuleContext;
import grammar.javaMinusMinus2Parser;

public class TypeChecker {
    private final List<String> errors = new ArrayList<>();
    private final SymbolTable globalTable;

    // اصلاح #۳: نماد آخرین شناسه‌ی resolve شده در primaryExpression
    // (برای دسترسی به arraySize هنگام چک کران آرایه)
    private SymbolInfo lastPrimarySymbol = null;

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

            // اصلاح #۳: capture نماد آرایه «قبل از» ارزیابی اندیس
            // (ارزیابی اندیس lastPrimarySymbol را بازنویسی می‌کند)
            SymbolInfo arraySym = lastPrimarySymbol;

            String indexType = getExpressionType(c.expression(), currentScope);
            if (!indexType.equals("int") && !indexType.equals("unknown")) {
                error(currentScope, ctx,
                        "Array index must be an integer, found: " + indexType);
            } else {
                checkArrayBounds(c.expression(), arraySym, currentScope);
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

        // اصلاح #۲: هر عملگر دودویی باید پس از محاسبه‌ی نوع نتیجه،
        // زنجیره‌ی expressionPrime داخلی خود را هم ادامه دهد؛ وگرنه
        // در «a + b + c» عملوند سوم هرگز بررسی نمی‌شود.

        if (ctx instanceof javaMinusMinus2Parser.PowExprContext) {
            javaMinusMinus2Parser.PowExprContext c = (javaMinusMinus2Parser.PowExprContext) ctx;
            String r = arithmetic(leftType, c.primaryExpression(), "^", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.MulExprContext) {
            javaMinusMinus2Parser.MulExprContext c = (javaMinusMinus2Parser.MulExprContext) ctx;
            String r = arithmetic(leftType, c.primaryExpression(), "*", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.DivExprContext) {
            javaMinusMinus2Parser.DivExprContext c = (javaMinusMinus2Parser.DivExprContext) ctx;
            String r = arithmetic(leftType, c.primaryExpression(), "/", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.ModExprContext) {
            javaMinusMinus2Parser.ModExprContext c = (javaMinusMinus2Parser.ModExprContext) ctx;
            String r = arithmetic(leftType, c.primaryExpression(), "%", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.AddExprContext) {
            javaMinusMinus2Parser.AddExprContext c = (javaMinusMinus2Parser.AddExprContext) ctx;
            String r = arithmetic(leftType, c.primaryExpression(), "+", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.SubExprContext) {
            javaMinusMinus2Parser.SubExprContext c = (javaMinusMinus2Parser.SubExprContext) ctx;
            String r = arithmetic(leftType, c.primaryExpression(), "-", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.LtExprContext) {
            javaMinusMinus2Parser.LtExprContext c = (javaMinusMinus2Parser.LtExprContext) ctx;
            String r = relational(leftType, c.primaryExpression(), "<", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.LeExprContext) {
            javaMinusMinus2Parser.LeExprContext c = (javaMinusMinus2Parser.LeExprContext) ctx;
            String r = relational(leftType, c.primaryExpression(), "<=", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.GtExprContext) {
            javaMinusMinus2Parser.GtExprContext c = (javaMinusMinus2Parser.GtExprContext) ctx;
            String r = relational(leftType, c.primaryExpression(), ">", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.GeExprContext) {
            javaMinusMinus2Parser.GeExprContext c = (javaMinusMinus2Parser.GeExprContext) ctx;
            String r = relational(leftType, c.primaryExpression(), ">=", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.EqExprContext) {
            javaMinusMinus2Parser.EqExprContext c = (javaMinusMinus2Parser.EqExprContext) ctx;
            String r = equality(leftType, c.primaryExpression(), "==", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.NeqExprContext) {
            javaMinusMinus2Parser.NeqExprContext c = (javaMinusMinus2Parser.NeqExprContext) ctx;
            String r = equality(leftType, c.primaryExpression(), "!=", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }

        if (ctx instanceof javaMinusMinus2Parser.AndExprContext) {
            javaMinusMinus2Parser.AndExprContext c = (javaMinusMinus2Parser.AndExprContext) ctx;
            String r = logical(leftType, c.primaryExpression(), "&&", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }
        if (ctx instanceof javaMinusMinus2Parser.OrExprContext) {
            javaMinusMinus2Parser.OrExprContext c = (javaMinusMinus2Parser.OrExprContext) ctx;
            String r = logical(leftType, c.primaryExpression(), "||", ctx, currentScope);
            return getExpressionPrimeType(c.expressionPrime(), r, currentScope);
        }

        return leftType;
    }

    public String getPrimaryExpressionType(javaMinusMinus2Parser.PrimaryExpressionContext ctx,
            SymbolTable currentScope) {
        lastPrimarySymbol = null; // اصلاح #۳

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
                lastPrimarySymbol = sym; // اصلاح #۳

                // اصلاح #۶: گیرنده‌ی استاتیک (Helper.help())
                if (sym.symbolType == SymbolInfo.SymbolType.CLASS
                        || sym.symbolType == SymbolInfo.SymbolType.INTERFACE) {
                    return sym.name;
                }
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

        // اصلاح #۷: الحاق رشته با «+» مجاز است
        if (op.equals("+") && (leftType.equals("String") || rightType.equals("String"))) {
            return "String";
        }

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

        // اصلاح #۵: BFS روی والد «و» اینترفیس‌ها (نه فقط زنجیره‌ی وراثت)
        Set<String> visited = new HashSet<>();
        Queue<SymbolTable> queue = new LinkedList<>();
        queue.add(classScope);

        while (!queue.isEmpty()) {
            SymbolTable search = queue.poll();
            if (!visited.add(search.getScopeName()))
                continue;

            for (SymbolInfo m : search.lookupMethodOverloads(methodName)) {
                if (matchesArguments(m, argTypes, currentScope)) {
                    return (m.dataType != null) ? m.dataType : "void";
                }
            }

            SymbolInfo typeInfo = currentScope.Lookup(search.getScopeName());
            if (typeInfo != null) {
                if (typeInfo.parentClass != null) {
                    SymbolTable p = currentScope.findTypeScope(typeInfo.parentClass);
                    if (p != null)
                        queue.add(p);
                }
                if (typeInfo.interfaces != null) {
                    for (String iface : typeInfo.interfaces) {
                        SymbolTable is = currentScope.findTypeScope(iface);
                        if (is != null)
                            queue.add(is);
                    }
                }
            }
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
                // اصلاح #۱: فیلد implementedInterfaces حذف شده است
                if (info.interfaces != null) {
                    queue.addAll(info.interfaces);
                }
            }
        }

        return false;
    }

    /**
     * اصلاح #۳: استخراج اندیس ثابت با پوشش عدد منفی
     * (که به‌صورت UnaryMinusExpr(IntLit) پارس می‌شود)
     * برمی‌گرداند null اگر اندیس، ثابت عددی خالص نباشد.
     */
    private Integer extractConstantIndex(javaMinusMinus2Parser.ExpressionContext indexCtx) {
        if (indexCtx == null)
            return null;
        // فقط عبارت‌های بدون دنباله (literal خالص)
        if (indexCtx.expressionPrime() != null
                && !(indexCtx.expressionPrime() instanceof javaMinusMinus2Parser.EmptyExprTailContext))
            return null;

        javaMinusMinus2Parser.PrimaryExpressionContext p = indexCtx.primaryExpression();
        try {
            if (p instanceof javaMinusMinus2Parser.IntLitExprContext) {
                return Integer.parseInt(p.getText().trim());
            }
            if (p instanceof javaMinusMinus2Parser.UnaryMinusExprContext) {
                javaMinusMinus2Parser.PrimaryExpressionContext inner = ((javaMinusMinus2Parser.UnaryMinusExprContext) p)
                        .primaryExpression();
                if (inner instanceof javaMinusMinus2Parser.IntLitExprContext) {
                    return -Integer.parseInt(inner.getText().trim());
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void checkArrayBounds(javaMinusMinus2Parser.ExpressionContext indexCtx,
            SymbolInfo arraySym, SymbolTable currentScope) {
        Integer v = extractConstantIndex(indexCtx);
        if (v == null)
            return;

        if (v < 0) {
            error(currentScope, indexCtx, "Array index cannot be negative: " + v);
            return;
        }

        // اصلاح #۳ (چک #۶ فاز ۲): اندیس ثابت خارج از محدوده‌ی size
        if (arraySym != null && arraySym.arraySize > 0 && v >= arraySym.arraySize) {
            error(currentScope, indexCtx, String.format(
                    "Array index %d is out of bounds for array '%s' of size %d",
                    v, arraySym.name, arraySym.arraySize));
        }
    }

    private void error(SymbolTable scope, ParserRuleContext ctx, String message) {
        String formatted = String.format("Line %d:%d - %s",
                ctx.getStart().getLine(),
                ctx.getStart().getCharPositionInLine(),
                message);
        errors.add(formatted); // اصلاح #۴
        scope.addSemanticError(formatted);
    }
}
