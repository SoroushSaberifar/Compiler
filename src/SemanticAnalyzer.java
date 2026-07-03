import grammar.javaMinusMinus2BaseListener;
import grammar.javaMinusMinus2Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class SemanticAnalyzer extends javaMinusMinus2BaseListener {

    private SymbolTable currentScope;
    private final Stack<SymbolTable> scopeStack = new Stack<>();
    private final Stack<Integer> childIndexStack = new Stack<>();
    private final TypeChecker typeChecker = new TypeChecker();

    private int currentChildScopeIndex = 0;

    public SemanticAnalyzer(SymbolTable globalTable) {
        this.currentScope = globalTable;
        this.scopeStack.push(globalTable);

        if (globalTable != null) {
            checkInheritanceCycles(globalTable);
            validateImports(globalTable);
        }
    }

    private void pushChildScope() {
        if (currentScope == null)
            return;

        List<SymbolTable> children = currentScope.getChildScopes();
        if (children == null || currentChildScopeIndex < 0 || currentChildScopeIndex >= children.size()) {
            return;
        }

        SymbolTable next = children.get(currentChildScopeIndex);
        scopeStack.push(next);
        childIndexStack.push(currentChildScopeIndex);
        currentScope = next;
        currentChildScopeIndex = 0;
    }

    private void popScope() {
        if (scopeStack.isEmpty())
            return;

        scopeStack.pop();

        if (!scopeStack.isEmpty()) {
            currentScope = scopeStack.peek();
            if (!childIndexStack.isEmpty()) {
                currentChildScopeIndex = childIndexStack.pop() + 1;
            } else {
                currentChildScopeIndex = 0;
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
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        validateMethodReturns(ctx);
        popScope();
    }

    @Override
    public void enterCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        pushChildScope();
    }

    @Override
    public void exitCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        validateConstructorReturns(ctx);
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
    public void enterIfElseStmt(javaMinusMinus2Parser.IfElseStmtContext ctx) {
        if (ctx == null || ctx.expression() == null)
            return;

        String condType = typeChecker.getExpressionType(ctx.expression(), currentScope);
        if (!"unknown".equals(condType) && !"boolean".equals(condType)) {
            addError(ctx.getStart(),
                    "Condition of 'if' must be boolean, found: " + condType);
        }
    }

    @Override
    public void enterWhileStmt(javaMinusMinus2Parser.WhileStmtContext ctx) {
        if (ctx == null || ctx.expression() == null)
            return;

        String condType = typeChecker.getExpressionType(ctx.expression(), currentScope);
        if (!"unknown".equals(condType) && !"boolean".equals(condType)) {
            addError(ctx.getStart(),
                    "Condition of 'while' must be boolean, found: " + condType);
        }
    }

    @Override
    public void enterAssignment(javaMinusMinus2Parser.AssignmentContext ctx) {
        if (ctx == null || ctx.designator() == null || ctx.expression() == null)
            return;

        String lhsName = ctx.designator().getText();
        SymbolInfo lhs = currentScope != null ? currentScope.lookup(lhsName) : null;

        if (lhs == null) {
            addError(ctx.getStart(), "Assignment to undeclared identifier: " + lhsName);
            return;
        }

        String leftType = resolveDesignatorType(ctx.designator(), lhs);
        String rightType = typeChecker.getExpressionType(ctx.expression(), currentScope);

        if (!"unknown".equals(leftType) && !"unknown".equals(rightType)) {
            if (!typeChecker.isTypeCompatible(leftType, rightType, currentScope)) {
                addError(ctx.getStart(),
                        "Type mismatch in assignment. Expected " + leftType + ", found " + rightType);
            }
        }

        lhs.isInitialized = true;
    }

    @Override
    public void enterExpression(javaMinusMinus2Parser.ExpressionContext ctx) {
        if (ctx != null) {
            typeChecker.getExpressionType(ctx, currentScope);
        }
    }

    private void validateMethodReturns(javaMinusMinus2Parser.MethodDeclContext ctx) {
        if (ctx == null)
            return;

        String expectedType = (ctx.type() != null) ? ctx.type().getText() : "void";
        List<ReturnInfo> returns = new ArrayList<>();
        collectReturnsRecursive(ctx.methodBody(), returns);

        if ("void".equals(expectedType)) {
            for (ReturnInfo r : returns) {
                if (r.hasExpression) {
                    addError(r.token, "Void method cannot return a value");
                }
            }
            return;
        }

        if (returns.isEmpty()) {
            addError(ctx.getStart(),
                    "Missing return statement in method '" + safeName(ctx.Identifier()) + "'");
            return;
        }

        for (ReturnInfo r : returns) {
            if (!r.hasExpression) {
                addError(r.token, "Missing return expression for non-void method");
                continue;
            }

            String actualType = typeChecker.getExpressionType(r.expressionCtx, currentScope);
            if (!"unknown".equals(actualType)
                    && !typeChecker.isTypeCompatible(expectedType, actualType, currentScope)) {
                addError(r.token,
                        "Return type mismatch. Expected " + expectedType + ", found " + actualType);
            }
        }
    }

    private void validateConstructorReturns(javaMinusMinus2Parser.CtorDeclContext ctx) {
        if (ctx == null)
            return;

        List<ReturnInfo> returns = new ArrayList<>();
        collectReturnsRecursive(ctx.methodBody(), returns);
        for (ReturnInfo r : returns) {
            if (r.hasExpression) {
                addError(r.token, "Constructor cannot return a value");
            }
        }
    }

    private static class ReturnInfo {
        final Token token;
        final boolean hasExpression;
        final javaMinusMinus2Parser.ExpressionContext expressionCtx;

        ReturnInfo(Token token, boolean hasExpression, javaMinusMinus2Parser.ExpressionContext expressionCtx) {
            this.token = token;
            this.hasExpression = hasExpression;
            this.expressionCtx = expressionCtx;
        }
    }

    private void collectReturnsRecursive(ParseTree node, List<ReturnInfo> returns) {
        if (node == null)
            return;

        if (node instanceof javaMinusMinus2Parser.MethodBodyContext) {
            javaMinusMinus2Parser.MethodBodyContext mb = (javaMinusMinus2Parser.MethodBodyContext) node;
            if (mb.RETURN() != null) {
                Token t = mb.RETURN().getSymbol();
                returns.add(new ReturnInfo(t, mb.expression() != null, mb.expression()));
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectReturnsRecursive(node.getChild(i), returns);
        }
    }

    private String resolveDesignatorType(javaMinusMinus2Parser.DesignatorContext ctx, SymbolInfo base) {
        if (ctx == null || base == null)
            return "unknown";
        if (base.dataType == null)
            return "unknown";
        return base.dataType;
    }

    private String safeName(Object node) {
        return node == null ? "<anonymous>" : node.toString();
    }

    private void addError(Token token, String message) {
        if (currentScope == null || token == null)
            return;
        currentScope.addSemanticError(
                "Line " + token.getLine() + ":" + token.getCharPositionInLine() + " - " + message);
    }

    private void checkInheritanceCycles(SymbolTable globalTable) {
        if (globalTable == null)
            return;

        Map<String, SymbolInfo> symbols = globalTable.getSymbols();
        if (symbols == null)
            return;

        for (SymbolInfo s : symbols.values()) {
            if (s == null || s.symbolType != SymbolInfo.SymbolType.CLASS)
                continue;

            Set<String> seen = new HashSet<>();
            String cur = s.name;

            while (cur != null) {
                if (seen.contains(cur)) {
                    globalTable.addSemanticError("Inheritance cycle detected involving class: " + cur);
                    break;
                }

                seen.add(cur);
                SymbolInfo cls = globalTable.lookup(cur);
                if (cls == null || cls.parentClass == null || cls.parentClass.isBlank())
                    break;

                cur = cls.parentClass;
            }
        }
    }

    private void validateImports(SymbolTable globalTable) {
        if (globalTable == null)
            return;

        Map<String, SymbolInfo> symbols = globalTable.getSymbols();
        if (symbols == null)
            return;

        Set<String> seenImports = new HashSet<>();
        for (SymbolInfo s : symbols.values()) {
            if (s == null || s.symbolType != SymbolInfo.SymbolType.IMPORT)
                continue;

            if (!seenImports.add(s.name)) {
                globalTable.addSemanticError("Duplicate import declaration: " + s.name);
            }
        }
    }
}