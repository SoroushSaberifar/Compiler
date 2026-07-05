import org.antlr.v4.runtime.Token;

import java.util.*;
import grammar.javaMinusMinus2Parser;
import grammar.javaMinusMinus2BaseListener;

public class SemanticAnalyzer extends javaMinusMinus2BaseListener {

    private final SymbolTable globalTable;
    private SymbolTable currentScope;

    private final Deque<SymbolTable> scopeStack = new ArrayDeque<>();
    private final Deque<Integer> childIndexStack = new ArrayDeque<>();

    private final TypeChecker typeChecker;
    private final List<String> errors = new ArrayList<>();

    private int loopDepth = 0;

    private String currentClassName = null;

    public SemanticAnalyzer(SymbolTable globalTable) {
        this.globalTable = globalTable;
        this.currentScope = globalTable;
        this.typeChecker = new TypeChecker(globalTable);
        childIndexStack.push(0);

        checkInheritanceCycles();
        validateImports();
    }

    private void pushChildScope() {
        List<SymbolTable> children = currentScope.getChildScopes();
        int idx = childIndexStack.pop();
        if (idx < children.size()) {
            childIndexStack.push(idx + 1);
            scopeStack.push(currentScope);
            currentScope = children.get(idx);
            childIndexStack.push(0);
        } else {
            childIndexStack.push(idx);
            errors.add("Internal error: scope tree mismatch at "
                    + currentScope);
        }
    }

    private void popScope() {
        childIndexStack.pop();
        currentScope = scopeStack.pop();
    }

    @Override
    public void enterMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        currentClassName = ctx.Identifier(0).getText();
        pushChildScope();
        pushChildScope();
    }

    @Override
    public void exitMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        popScope();
        popScope();
        currentClassName = null;
    }

    @Override
    public void enterClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        currentClassName = ctx.Identifier(0).getText();
        pushChildScope();
    }

    @Override
    public void exitClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        popScope();
        currentClassName = null;
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
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        validateMethodReturn(ctx);
        popScope();
    }

    @Override
    public void enterCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        String ctorName = ctx.Identifier().getText();
        if (currentClassName != null && !ctorName.equals(currentClassName)) {
            addError("Constructor name '" + ctorName
                    + "' does not match class name '" + currentClassName + "'",
                    ctx.Identifier().getSymbol());
        }
        pushChildScope();
    }

    @Override
    public void exitCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        validateConstructorReturn(ctx);
        popScope();
    }

    @Override
    public void enterBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        pushChildScope();
    }

    @Override
    public void exitBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        popScope();
    }

    @Override
    public void enterForStatement(javaMinusMinus2Parser.ForStatementContext ctx) {
        pushChildScope();
        loopDepth++;
        if (ctx.forStmt().expression() != null) {
            String condType = typeChecker.getExpressionType(ctx.forStmt().expression(), currentScope);
            if (!condType.equals("boolean") && !condType.equals("unknown")) {
                addError("For condition must be boolean, found '" + condType + "'",
                        ctx.getStart());
            }
        }
    }

    @Override
    public void exitForStatement(javaMinusMinus2Parser.ForStatementContext ctx) {
        loopDepth--;
        popScope();
    }

    @Override
    public void enterWhileStmt(javaMinusMinus2Parser.WhileStmtContext ctx) {
        loopDepth++;
        String condType = typeChecker.getExpressionType(ctx.expression(), currentScope);
        if (!condType.equals("boolean") && !condType.equals("unknown")) {
            addError("While condition must be boolean, found '" + condType + "'",
                    ctx.getStart());
        }
    }

    @Override
    public void exitWhileStmt(javaMinusMinus2Parser.WhileStmtContext ctx) {
        loopDepth--;
    }

    @Override
    public void enterIfElseStmt(javaMinusMinus2Parser.IfElseStmtContext ctx) {
        String condType = typeChecker.getExpressionType(ctx.expression(), currentScope);
        if (!condType.equals("boolean") && !condType.equals("unknown")) {
            addError("If condition must be boolean, found '" + condType + "'",
                    ctx.getStart());
        }
    }

    @Override
    public void enterBreakStmt(javaMinusMinus2Parser.BreakStmtContext ctx) {
        if (loopDepth == 0) {
            addError("'break' outside of loop", ctx.getStart());
        }
    }

    @Override
    public void enterContinueStmt(javaMinusMinus2Parser.ContinueStmtContext ctx) {
        if (loopDepth == 0) {
            addError("'continue' outside of loop", ctx.getStart());
        }
    }

    @Override
    public void enterAssignment(javaMinusMinus2Parser.AssignmentContext ctx) {
        String designatorText = ctx.designator().getText();
        String baseName = baseIdentifier(designatorText);

        SymbolInfo lhs = currentScope.Lookup(baseName);
        if (lhs == null) {
            addError("Undeclared variable '" + baseName + "'", ctx.getStart());
            return;
        }

        String lhsType = resolveDesignatorType(designatorText, lhs);
        String rhsType = typeChecker.getExpressionType(ctx.expression(), currentScope);

        if (!rhsType.equals("unknown")
                && !typeChecker.isTypeCompatible(lhsType, rhsType, currentScope)) {
            addError("Type mismatch in assignment: cannot assign '" + rhsType
                    + "' to '" + lhsType + "' (variable '" + baseName + "')",
                    ctx.getStart());
        }

        lhs.isInitialized = true;
    }

    @Override
    public void enterLocalDecl(javaMinusMinus2Parser.LocalDeclContext ctx) {
        if (ctx.expression() == null)
            return;

        String declaredType = ctx.type().getText();
        String initType = typeChecker.getExpressionType(ctx.expression(), currentScope);

        if (!initType.equals("unknown")
                && !typeChecker.isTypeCompatible(declaredType, initType, currentScope)) {
            addError("Type mismatch in initialization of '"
                    + ctx.Identifier().getText() + "': cannot assign '"
                    + initType + "' to '" + declaredType + "'",
                    ctx.getStart());
        }

        SymbolInfo sym = currentScope.Lookup(ctx.Identifier().getText());
        if (sym != null)
            sym.isInitialized = true;
    }

    @Override
    public void enterLocalDeclNoSemi(javaMinusMinus2Parser.LocalDeclNoSemiContext ctx) {
        if (ctx.expression() == null)
            return;
        String declaredType = ctx.type().getText();
        String initType = typeChecker.getExpressionType(ctx.expression(), currentScope);

        if (!initType.equals("unknown")
                && !typeChecker.isTypeCompatible(declaredType, initType, currentScope)) {
            addError("Type mismatch in for-init of '" + ctx.Identifier().getText()
                    + "': cannot assign '" + initType + "' to '" + declaredType + "'",
                    ctx.getStart());
        }

        SymbolInfo sym = currentScope.Lookup(ctx.Identifier().getText());
        if (sym != null)
            sym.isInitialized = true;
    }

    @Override
    public void enterExprOnlyStmt(javaMinusMinus2Parser.ExprOnlyStmtContext ctx) {
        if (ctx.exprStmt().assignment() != null &&
                ctx.exprStmt().assignment().expression() != null) {

            typeChecker.getExpressionType(ctx.exprStmt().assignment().expression(), currentScope);
        }
    }

    @Override
    public void enterPrintStatement(javaMinusMinus2Parser.PrintStatementContext ctx) {
        if (ctx.printStmt().expressionOrString() != null &&
                ctx.printStmt().expressionOrString().expression() != null) {

            typeChecker.getExpressionType(ctx.printStmt().expressionOrString().expression(), currentScope);
        }
    }

    private void validateMethodReturn(javaMinusMinus2Parser.MethodDeclContext ctx) {
        String declaredReturn = (ctx.type() != null) ? ctx.type().getText() : "void";
        javaMinusMinus2Parser.MethodBodyContext body = ctx.methodBody();

        boolean hasReturn = body != null && body.RETURN() != null;
        javaMinusMinus2Parser.ExpressionContext retExpr = (body != null) ? body.expression() : null;

        if (declaredReturn.equals("void")) {
            if (retExpr != null) {
                addError("Void method '" + ctx.Identifier().getText()
                        + "' cannot return a value",
                        retExpr.getStart());
            }
        } else {
            if (!hasReturn || retExpr == null) {
                addError("Method '" + ctx.Identifier().getText()
                        + "' must return a value of type '" + declaredReturn + "'",
                        ctx.getStart());
            } else {
                String actual = typeChecker.getExpressionType(retExpr, currentScope);
                if (!actual.equals("unknown")
                        && !typeChecker.isTypeCompatible(declaredReturn, actual, currentScope)) {
                    addError("Return type mismatch in method '"
                            + ctx.Identifier().getText() + "': expected '"
                            + declaredReturn + "', found '" + actual + "'",
                            retExpr.getStart());
                }
            }
        }
    }

    private void validateConstructorReturn(javaMinusMinus2Parser.CtorDeclContext ctx) {
        javaMinusMinus2Parser.MethodBodyContext body = ctx.methodBody();
        if (body != null && body.expression() != null) {
            addError("Constructor '" + ctx.Identifier().getText()
                    + "' cannot return a value",
                    body.expression().getStart());
        }
    }

    private void checkInheritanceCycles() {
        for (SymbolInfo info : globalTable.getAllSymbols()) {
            if (info.symbolType != SymbolInfo.SymbolType.CLASS)
                continue;

            Set<String> visited = new HashSet<>();
            String current = info.name;
            while (current != null) {
                if (!visited.add(current)) {
                    addError("Inheritance cycle detected involving class '"
                            + info.name + "'", null);
                    break;
                }
                current = getParentOf(current);
            }
        }
    }

    private String getParentOf(String className) {
        SymbolInfo cls = globalTable.Lookup(className);
        return (cls != null) ? cls.parentClass : null;
    }

    private void validateImports() {
        Set<String> seen = new HashSet<>();
        for (SymbolInfo info : globalTable.getAllSymbols()) {
            if (info.symbolType != SymbolInfo.SymbolType.IMPORT)
                continue;
            if (!seen.add(info.name)) {
                addError("Duplicate import '" + info.name + "'", null);
            }
        }
    }

    private String baseIdentifier(String designatorText) {
        int dot = designatorText.indexOf('.');
        int bracket = designatorText.indexOf('[');
        int cut = designatorText.length();
        if (dot >= 0)
            cut = Math.min(cut, dot);
        if (bracket >= 0)
            cut = Math.min(cut, bracket);
        return designatorText.substring(0, cut);
    }

    private String resolveDesignatorType(String designatorText, SymbolInfo base) {
        if (designatorText.endsWith(".length"))
            return "int";
        if (designatorText.contains("[") && base.dataType != null
                && base.dataType.endsWith("[]")) {
            return base.dataType.substring(0, base.dataType.length() - 2);
        }
        return base.dataType;
    }

    private void addError(String message, Token token) {
        if (token != null) {
            errors.add("Line " + token.getLine() + ":"
                    + token.getCharPositionInLine() + " - " + message);
        } else {
            errors.add(message);
        }
    }

    public List<String> getErrors() {
        List<String> all = new ArrayList<>(errors);
        all.addAll(typeChecker.getErrors());
        return all;
    }

    public boolean hasErrors() {
        return !errors.isEmpty() || !typeChecker.getErrors().isEmpty();
    }

    public void printErrors() {
        for (String e : getErrors()) {
            System.err.println("Semantic Error: " + e);
        }
    }
}
