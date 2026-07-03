import grammar.javaMinusMinus2Parser;

public class TypeChecker {

    public String getExpressionType(javaMinusMinus2Parser.ExpressionContext ctx, SymbolTable currentScope) {
        if (ctx == null)
            return "unknown";

        // ابتدا نوع عملوند سمت چپ را به دست می‌آوریم
        String leftType = getPrimaryExpressionType(ctx.primaryExpression(), currentScope);

        // اگر بخش دوم عبارت (Tail/Prime) خالی باشد، نوع عبارت همان نوع سمت چپ است
        if (ctx.expressionPrime() == null || ctx.expressionPrime().getText().isEmpty()) {
            return leftType;
        }

        return getExpressionPrimeType(ctx.expressionPrime(), leftType, currentScope);
    }

    private String getExpressionPrimeType(javaMinusMinus2Parser.ExpressionPrimeContext ctx, String leftType,
            SymbolTable currentScope) {
        if (ctx == null)
            return leftType;

        if (ctx instanceof javaMinusMinus2Parser.ArrayAccessExprContext) {
            javaMinusMinus2Parser.ArrayAccessExprContext arrayCtx = (javaMinusMinus2Parser.ArrayAccessExprContext) ctx;
            javaMinusMinus2Parser.ExpressionContext indexCtx = arrayCtx.expression();
            String indexType = getExpressionType(indexCtx, currentScope);

            if (!indexType.equals("int")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Array index must be an integer, found: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), indexType));
            }

            checkArrayBounds(indexCtx, leftType, currentScope);

            if (leftType.endsWith("[]")) {
                String resultType = leftType.substring(0, leftType.length() - 2);
                // فراخوانی متد روی گره فرزندِ واقعی موجود در این ساب‌کلاس
                return getExpressionPrimeType(arrayCtx.expressionPrime(), resultType, currentScope);
            }
            return "unknown";
        }

        if (ctx instanceof javaMinusMinus2Parser.ArrayLengthExprContext) {
            if (!leftType.endsWith("[]")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - .length can only be applied to arrays",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
            }
            return "int";
        }

        // عملیات‌های ریاضی
        if (ctx instanceof javaMinusMinus2Parser.AddExprContext || ctx instanceof javaMinusMinus2Parser.SubExprContext
                || ctx instanceof javaMinusMinus2Parser.MulExprContext
                || ctx instanceof javaMinusMinus2Parser.DivExprContext
                || ctx instanceof javaMinusMinus2Parser.ModExprContext
                || ctx instanceof javaMinusMinus2Parser.PowExprContext) {

            if (!leftType.equals("int")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Operator cannot be applied to type: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), leftType));
            }
            return "int";
        }

        // عملیات‌های مقایسه‌ای و منطقی
        if (ctx instanceof javaMinusMinus2Parser.LtExprContext || ctx instanceof javaMinusMinus2Parser.LeExprContext ||
                ctx instanceof javaMinusMinus2Parser.GtExprContext || ctx instanceof javaMinusMinus2Parser.GeExprContext
                ||
                ctx instanceof javaMinusMinus2Parser.EqExprContext
                || ctx instanceof javaMinusMinus2Parser.NeqExprContext ||
                ctx instanceof javaMinusMinus2Parser.AndExprContext
                || ctx instanceof javaMinusMinus2Parser.OrExprContext) {
            return "boolean";
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

            // بررسی اینکه اندیس سایز آرایه int باشد
            String sizeType = getExpressionType(newArrCtx.expression(), currentScope);
            if (!sizeType.equals("int")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Array allocation size must be an integer",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
            }
            return baseType + "[]";
        }

        if (ctx instanceof javaMinusMinus2Parser.ParenExprContext) {
            return getExpressionType(((javaMinusMinus2Parser.ParenExprContext) ctx).expression(), currentScope);
        }

        return "unknown";
    }

    private void checkArrayBounds(javaMinusMinus2Parser.ExpressionContext indexCtx, String leftType,
            SymbolTable currentScope) {
        if (indexCtx == null)
            return;

        // اگر اندیس یک عدد ثابت مستقیم بود
        if (indexCtx.primaryExpression() instanceof javaMinusMinus2Parser.IntLitExprContext) {
            try {
                int indexVal = Integer.parseInt(indexCtx.primaryExpression().getText().trim());
                if (indexVal < 0) {
                    currentScope.addSemanticError(String.format(
                            "Line %d:%d - Array index cannot be negative: %d",
                            indexCtx.getStart().getLine(), indexCtx.getStart().getCharPositionInLine(), indexVal));
                }
                // نکته: بررسی سقف آرایه (بزرگتر از سایز) را در فاز انتساب یا درخت‌پیمای اصلی
                // انجام خواهیم داد،
                // زیرا در این گره، نام فیلد آرایه را مستقیماً نداریم (فقط نوع داده را داریم).
            } catch (NumberFormatException ignored) {
            }
        }
    }
}