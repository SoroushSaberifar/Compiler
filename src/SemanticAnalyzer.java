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

    /**
     * بررسی تطابق نوع در بازگشت متد (Return Type Mismatch)
     */
    @Override
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        String expectedReturnType = ctx.type() != null ? ctx.type().getText() : "void";
        
        // استخراج تمام گره‌های دستور return از بدنه متد
        List<javaMinusMinus2Parser.StatementContext> returnStmts = findReturnStatements(ctx.methodBody());
        
        if (expectedReturnType.equals("void")) {
            for (javaMinusMinus2Parser.StatementContext retCtx : returnStmts) {
                // اگر عبارتی بعد از return وجود داشته باشد (طول رشته بیشتر از کلمه کلیدی خالی است)
                if (hasReturnExpression(retCtx)) {
                    currentScope.addSemanticError(String.format(
                        "Line %d:%d - Void method cannot return a value",
                        retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine()
                    ));
                }
            }
        } else {
            if (returnStmts.isEmpty()) {
                currentScope.addSemanticError(String.format(
                    "Line %d:%d - Missing return statement in method '%s'. Expected return type: %s",
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), ctx.Identifier().getText(), expectedReturnType
                ));
            } else {
                for (javaMinusMinus2Parser.StatementContext retCtx : returnStmts) {
                    if (!hasReturnExpression(retCtx)) {
                        currentScope.addSemanticError(String.format(
                            "Line %d:%d - Return statement missing expression. Expected return type: %s",
                            retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine(), expectedReturnType
                        ));
                    } else {
                        // پیدا کردن گره اکسپرشن در فرزندان دستور ریترن جهت ارسال به تایپ‌چکر
                        javaMinusMinus2Parser.ExpressionContext exprCtx = findExpressionInStatement(retCtx);
                        if (exprCtx != null) {
                            String actualType = typeChecker.getExpressionType(exprCtx, currentScope);
                            if (!actualType.equals("unknown") && !actualType.equals(expectedReturnType)) {
                                currentScope.addSemanticError(String.format(
                                    "Line %d:%d - Type mismatch in return statement. Expected: %s, Found: %s",
                                    retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine(), expectedReturnType, actualType
                                ));
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
        // سازنده‌ها نباید مقدار بازگردانند
        List<javaMinusMinus2Parser.StatementContext> returnStmts = findReturnStatements(ctx.methodBody());
        for (javaMinusMinus2Parser.StatementContext retCtx : returnStmts) {
            if (hasReturnExpression(retCtx)) {
                currentScope.addSemanticError(String.format(
                    "Line %d:%d - Constructor cannot return a value",
                    retCtx.getStart().getLine(), retCtx.getStart().getCharPositionInLine()
                ));
            }
        }
        popScope();
    }

    /**
     * بررسی شناسه‌ها، انتساب‌ها و فراخوانی متدها در عبارات
     */
    @Override
    public void enterExpression(javaMinusMinus2Parser.ExpressionContext ctx) {
        String text = ctx.getText().trim();
        
        // ۱. بررسی انتساب و تعریف متغیر
        if (text.contains("=")) {
            int eqIndex = text.indexOf("=");
            String targetVar = text.substring(0, eqIndex).trim();
            
            if (targetVar.contains("[")) {
                targetVar = targetVar.substring(0, targetVar.indexOf("[")).trim();
            }
            
            if (!targetVar.matches("\\d+") && !targetVar.equals("true") && !targetVar.equals("false") && !targetVar.contains(".")) {
                SymbolInfo sym = currentScope.lookup(targetVar);
                if (sym == null) {
                    currentScope.addSemanticError(String.format(
                        "Line %d:%d - Use of undeclared variable '%s'",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), targetVar
                    ));
                }
            }
        }

        // ۲. بررسی فراخوانی متدها و انطباق تعداد آرگومان‌ها
        if (text.contains("(") && text.contains(")")) {
            int openParen = text.indexOf("(");
            String prefix = text.substring(0, openParen).trim();
            String methodName = prefix;
            
            if (prefix.contains(".")) {
                methodName = prefix.substring(prefix.lastIndexOf(".") + 1).trim();
            }
            
            if (!methodName.equals("if") && !methodName.equals("while") && !methodName.equals("for") && !methodName.equals("new")) {
                SymbolInfo methodSym = currentScope.lookup(methodName);
                if (methodSym == null) {
                    currentScope.addSemanticError(String.format(
                        "Line %d:%d - Call to undeclared method '%s'",
                        ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), methodName
                    ));
                } else if (methodSym.symbolType == SymbolInfo.SymbolType.METHOD) {
                    int expectedCount = methodSym.parameters.size();
                    String argsText = text.substring(openParen + 1, text.lastIndexOf(")")).trim();
                    int actualCount = argsText.isEmpty() ? 0 : argsText.split(",").length;
                    
                    if (expectedCount != actualCount) {
                        currentScope.addSemanticError(String.format(
                            "Line %d:%d - Method '%s' expects %d arguments, but %d were provided",
                            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), methodName, expectedCount, actualCount
                        ));
                    }
                }
            }
        }

        // ۳. بررسی استفاده از متغیرهای اعلام نشده درون عبارات ساده
        if (!text.contains(" ") && !text.contains("+") && !text.contains("-") && !text.contains("*") && !text.contains("/")) {
            if (text.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) { 
                if (!text.equals("true") && !text.equals("false") && !text.equals("null") && !text.equals("this")) {
                    SymbolInfo sym = currentScope.lookup(text);
                    if (sym == null) {
                        currentScope.addSemanticError(String.format(
                            "Line %d:%d - Use of undeclared variable '%s'",
                            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), text
                        ));
                    }
                }
            }
        }
    }

    // --- متدهای امن کمکی برای پیمایش مستقل از ساختار جنریت‌شده کامپایلر ارور ---

    private List<javaMinusMinus2Parser.StatementContext> findReturnStatements(javaMinusMinus2Parser.MethodBodyContext ctx) {
        List<javaMinusMinus2Parser.StatementContext> list = new ArrayList<>();
        if (ctx == null || ctx.statement() == null) return list;
        
        for (javaMinusMinus2Parser.StatementContext stmt : ctx.statement()) {
            collectReturnStmtsRecursive(stmt, list);
        }
        return list;
    }

    private void collectReturnStmtsRecursive(javaMinusMinus2Parser.StatementContext stmt, List<javaMinusMinus2Parser.StatementContext> list) {
        if (stmt == null) return;
        
        String cleanText = stmt.getText().trim();
        if (cleanText.startsWith("return")) {
            list.add(stmt);
        }
        
        // پیمایش امن گره‌های فرزند بدون صدا زدن متدهای اختصاصی ناموجود
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
        // اگر بعد از کلمه return و فاصله‌ها، عبارتی قبل از سیمیکولن وجود داشته باشد
        return txt.length() > 7 && !txt.equals("return;");
    }

    private javaMinusMinus2Parser.ExpressionContext findExpressionInStatement(org.antlr.v4.runtime.tree.ParseTree stmtCtx) {
        if (stmtCtx == null) return null;
        for (int i = 0; i < stmtCtx.getChildCount(); i++) {
            org.antlr.v4.runtime.tree.ParseTree child = stmtCtx.getChild(i);
            if (child instanceof javaMinusMinus2Parser.ExpressionContext) {
                return (javaMinusMinus2Parser.ExpressionContext) child;
            }
            javaMinusMinus2Parser.ExpressionContext res = findExpressionInStatement(child);
            if (res != null) return res;
        }
        return null;
    }
}