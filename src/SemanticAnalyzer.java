import grammar.javaMinusMinus2BaseListener;
import grammar.javaMinusMinus2Parser;
import java.util.*;

public class SemanticAnalyzer extends javaMinusMinus2BaseListener {
    private SymbolTable currentScope;
    private Stack<SymbolTable> scopeStack;
    private TypeChecker typeChecker;
    private int currentChildScopeIndex = 0;
    private Stack<Integer> childIndexStack;

    public SemanticAnalyzer(SymbolTable globalTable) {
        this.currentScope = globalTable;
        this.scopeStack = new Stack<>();
        this.childIndexStack = new Stack<>();
        this.scopeStack.push(globalTable);
        this.typeChecker = new TypeChecker();
    }

    // --- مدیریت و همگام‌سازی اسکوپ‌ها در پاس دوم ---

    private void pushChildScope() {
        if (currentChildScopeIndex < currentScope.getChildScopes().size()) {
            SymbolTable nextScope = currentScope.getChildScopes().get(currentChildScopeIndex);
            scopeStack.push(nextScope);
            childIndexStack.push(currentChildScopeIndex);
            currentScope = nextScope;
            currentChildScopeIndex = 0;
        }
    }

    private void popScope() {
        if (!scopeStack.isEmpty()) {
            scopeStack.pop();
            if (!scopeStack.isEmpty()) {
                currentScope = scopeStack.peek();
                if (!childIndexStack.isEmpty()) {
                    currentChildScopeIndex = childIndexStack.pop() + 1;
                }
            }
        }
    }

    @Override
    public void enterClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        pushChildScope();
    }

    @Override
    public void exitClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        pushChildScope();
    }

    @Override
    public void exitInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        pushChildScope();
    }

    @Override
    public void enterCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        pushChildScope();
    }

    @Override
    public void enterBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        pushChildScope();
    }

    @Override
    public void exitBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        popScope();
    }

    // --- پیاده‌سازی اعتبارسنجی‌ها و قوانین معنایی فاز دوم ---
    @Override
    public void enterIfElseStmt(javaMinusMinus2Parser.IfElseStmtContext ctx) {
        if (ctx.expression() != null) {
            String type = typeChecker.getExpressionType(ctx.expression(), currentScope);
            if (!type.equals("boolean") && !type.equals("unknown")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Condition inside 'if' must be boolean, found: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), type));
            }
        }
    }

    @Override
    public void enterWhileStmt(javaMinusMinus2Parser.WhileStmtContext ctx) {
        if (ctx.expression() != null) {
            String type = typeChecker.getExpressionType(ctx.expression(), currentScope);
            if (!type.equals("boolean") && !type.equals("unknown")) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Condition inside 'while' must be boolean, found: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), type));
            }
        }
    }

    /**
     * بررسی دستورات انتساب (قانون assignment در گرامر)
     */
    @Override
    public void enterAssignment(javaMinusMinus2Parser.AssignmentContext ctx) {
        if (ctx.designator() == null || ctx.designator().primaryDesignator() == null)
            return;

        // استخراج نام متغیر از ابتدای دیزاینیتور
        String varName = ctx.designator().primaryDesignator().getText();
        SymbolInfo sym = currentScope.lookup(varName);

        if (sym == null) {
            currentScope.addSemanticError(String.format(
                    "Line %d:%d - Assignment to undeclared variable '%s'",
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), varName));
            return;
        }

        String rightType = typeChecker.getExpressionType(ctx.expression(), currentScope);
        String leftType = sym.dataType;

        // بررسی اینکه آیا دیزاینیتور شامل دسترسی به آرایه (مثل `[i]`) است یا خیر
        if (ctx.designator().designatorPrime() != null) {
            String designatorText = ctx.designator().designatorPrime().getText();
            if (designatorText.contains("[") && leftType.endsWith("[]")) {
                leftType = leftType.substring(0, leftType.length() - 2);
            }
        }

        if (!rightType.equals("unknown") && !leftType.equals("unknown") && !leftType.equals(rightType)) {
            // اجازه دادن به انتساب null به اشیاء غیر از انواع پایه
            if (rightType.equals("null") && !leftType.equals("int") && !leftType.equals("boolean")
                    && !leftType.equals("char")) {
                return;
            }
            currentScope.addSemanticError(String.format(
                    "Line %d:%d - Type mismatch in assignment to '%s'. Expected %s, found %s",
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), varName, leftType, rightType));
        }
    }

    /**
     * بررسی تطابق نوع در بازگشت متد (Return Type Mismatch)
     */
    @Override
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        String expectedReturnType = ctx.type() != null ? ctx.type().getText() : "void";
        List<javaMinusMinus2Parser.StatementContext> returnStmts = findReturnStatements(ctx.methodBody());

        if (expectedReturnType.equals("void")) {
            for (javaMinusMinus2Parser.StatementContext retCtx : returnStmts) {
                if (hasReturnExpression(retCtx)) {
                    currentScope.addSemanticError(String.format(
                            "Line %d:%d - Void method cannot return a value",
                            retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine()));
                }
            }
        } else {
            if (returnStmts.isEmpty()) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Missing return statement in method '%s'. Expected return type: %s",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), ctx.Identifier().getText(),
                        expectedReturnType));
            } else {
                for (javaMinusMinus2Parser.StatementContext retCtx : returnStmts) {
                    if (!hasReturnExpression(retCtx)) {
                        currentScope.addSemanticError(String.format(
                                "Line %d:%d - Return statement missing expression. Expected return type: %s",
                                retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine(),
                                expectedReturnType));
                    } else {
                        javaMinusMinus2Parser.ExpressionContext exprCtx = findExpressionInStatement(retCtx);
                        if (exprCtx != null) {
                            String actualType = typeChecker.getExpressionType(exprCtx, currentScope);
                            if (!actualType.equals("unknown") && !actualType.equals(expectedReturnType)) {
                                currentScope.addSemanticError(String.format(
                                        "Line %d:%d - Type mismatch in return statement. Expected: %s, Found: %s",
                                        retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine(),
                                        expectedReturnType, actualType));
                            }
                        }
                    }
                }
            }
        }
        popScope();
    }

    @Override
    public void exitCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        List<javaMinusMinus2Parser.StatementContext> returnStmts = findReturnStatements(ctx.methodBody());
        for (javaMinusMinus2Parser.StatementContext retCtx : returnStmts) {
            if (hasReturnExpression(retCtx)) {
                currentScope.addSemanticError(String.format(
                        "Line %d:%d - Constructor cannot return a value",
                        retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine()));
            }
        }
        popScope();
    }

    @Override
    public void enterExpression(javaMinusMinus2Parser.ExpressionContext ctx) {
        // برای گره‌های تکی فرعی چاره‌ای جز ارزیابی کلی نوع اکسپرشن و بازگذاری خطاهای
        // درونی آن نداریم
        typeChecker.getExpressionType(ctx, currentScope);
    }

    // --- متدهای امن کمکی برای پیمایش مستقل از ساختار جنریت‌شده کامپایلر ارور ---

    private List<javaMinusMinus2Parser.StatementContext> findReturnStatements(
            javaMinusMinus2Parser.MethodBodyContext ctx) {
        List<javaMinusMinus2Parser.StatementContext> list = new ArrayList<>();
        if (ctx == null || ctx.statement() == null)
            return list;

        for (javaMinusMinus2Parser.StatementContext stmt : ctx.statement()) {
            collectReturnStmtsRecursive(stmt, list);
        }
        return list;
    }

    private void collectReturnStmtsRecursive(javaMinusMinus2Parser.StatementContext stmt,
            List<javaMinusMinus2Parser.StatementContext> list) {
        if (stmt == null)
            return;

        String cleanText = stmt.getText().trim();
        if (cleanText.startsWith("return")) {
            list.add(stmt);
        }

        for (int i = 0; i < stmt.getChildCount(); i++) {
            org.antlr.v4.runtime.tree.ParseTree child = stmt.getChild(i);
            if (child instanceof javaMinusMinus2Parser.StatementContext) {
                collectReturnStmtsRecursive((javaMinusMinus2Parser.StatementContext) child, list);
            } else if (child instanceof javaMinusMinus2Parser.BlockStmtContext) {
                javaMinusMinus2Parser.BlockStmtContext bCtx = (javaMinusMinus2Parser.BlockStmtContext) child;
                if (bCtx.statement() != null) {
                    for (javaMinusMinus2Parser.StatementContext subStmt : bCtx.statement()) {
                        collectReturnStmtsRecursive(subStmt, list);
                    }
                }
            }
        }
    }

    private boolean hasReturnExpression(javaMinusMinus2Parser.StatementContext retCtx) {
        String txt = retCtx.getText().trim();
        return txt.length() > 7 && !txt.equals("return;");
    }

    private javaMinusMinus2Parser.ExpressionContext findExpressionInStatement(
            org.antlr.v4.runtime.tree.ParseTree stmtCtx) {
        if (stmtCtx == null)
            return null;
        for (int i = 0; i < stmtCtx.getChildCount(); i++) {
            org.antlr.v4.runtime.tree.ParseTree child = stmtCtx.getChild(i);
            if (child instanceof javaMinusMinus2Parser.ExpressionContext) {
                return (javaMinusMinus2Parser.ExpressionContext) child;
            }
            javaMinusMinus2Parser.ExpressionContext res = findExpressionInStatement(child);
            if (res != null)
                return res;
        }
        return null;
    }
}