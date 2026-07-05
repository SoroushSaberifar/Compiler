import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.Stack;
import grammar.javaMinusMinus2Parser;
import grammar.javaMinusMinus2BaseListener;

public class CompleteSymbolTableBuilder extends javaMinusMinus2BaseListener {

    private final SymbolTable globalScope;
    private SymbolTable currentScope;
    private final Stack<SymbolTable> scopeStack = new Stack<>();

    public CompleteSymbolTableBuilder(SymbolTable globalTable) {
        this.globalScope = globalTable;
        this.currentScope = globalTable;
    }

    public SymbolTable getGlobalScope() {
        return globalScope;
    }

    private void pushScope(SymbolTable.ScopeType type, String name) {
        SymbolTable newScope = new SymbolTable(type, name, currentScope);
        scopeStack.push(currentScope);
        currentScope = newScope;
    }

    private void popScope() {
        if (!scopeStack.isEmpty()) {
            currentScope = scopeStack.pop();
        }
    }

    private void setLocationInfo(SymbolInfo info, Token token) {
        if (token != null) {
            info.setLocation(token.getLine(), token.getCharPositionInLine());
        }
    }

    private boolean hasOverrideAnnotation(ParserRuleContext ctx) {
        if (ctx.getChildCount() == 0)
            return false;
        String first = ctx.getChild(0).getText();
        return "@Override".equals(first)
                || ("@".equals(first) && ctx.getChildCount() > 1
                        && "Override".equals(ctx.getChild(1).getText()));
    }

    private SymbolInfo.AccessModifier getAccessModifier(
            javaMinusMinus2Parser.AccessModifierContext ctx) {
        if (ctx == null)
            return SymbolInfo.AccessModifier.DEFAULT;
        switch (ctx.getText()) {
            case "public":
                return SymbolInfo.AccessModifier.PUBLIC;
            case "private":
                return SymbolInfo.AccessModifier.PRIVATE;
            case "protected":
                return SymbolInfo.AccessModifier.PROTECTED;
            case "internal":
                return SymbolInfo.AccessModifier.INTERNAL;
            default:
                return SymbolInfo.AccessModifier.DEFAULT;
        }
    }

    private String typeText(javaMinusMinus2Parser.TypeContext typeCtx) {
        if (typeCtx == null)
            return "void";
        return typeCtx.getText();
    }

    private void fillParameters(SymbolInfo info,
            javaMinusMinus2Parser.ParameterListContext plCtx) {
        if (plCtx == null)
            return;
        for (javaMinusMinus2Parser.ParameterContext p : plCtx.parameter()) {
            info.parameters.add(new SymbolInfo.ParameterInfo(
                    p.Identifier().getText(),
                    typeText(p.type())));
        }
    }

    private void insertParameterSymbols(
            javaMinusMinus2Parser.ParameterListContext plCtx) {
        if (plCtx == null)
            return;
        for (javaMinusMinus2Parser.ParameterContext p : plCtx.parameter()) {
            String p_name = p.Identifier().getText();
            SymbolInfo pInfo = new SymbolInfo(p_name, SymbolInfo.SymbolType.PARAMETER);
            pInfo.dataType = typeText(p.type());
            pInfo.isInitialized = true;
            setLocationInfo(pInfo, p.Identifier().getSymbol());
            currentScope.Insert(p_name, pInfo);
        }
    }

    private void extractArraySize(SymbolInfo info, String initText) {
        if (initText == null || !info.isArrayType())
            return;
        String txt = initText.trim();
        if (txt.startsWith("new")) {
            int lb = txt.indexOf('[');
            int rb = txt.indexOf(']');
            if (lb >= 0 && rb > lb + 1) {
                try {
                    info.arraySize = Integer.parseInt(txt.substring(lb + 1, rb).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (txt.startsWith("{") && txt.endsWith("}")) {
            String inner = txt.substring(1, txt.length() - 1).trim();
            info.arraySize = inner.isEmpty() ? 0 : inner.split(",").length;
        }
    }

    @Override
    public void enterImportDecl(javaMinusMinus2Parser.ImportDeclContext ctx) {
        int n = ctx.Identifier().size();
        String name = ctx.Identifier(n - 1).getText();
        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.IMPORT);
        info.dataType = ctx.getText()
                .replace("import", "")
                .replace(";", "");
        setLocationInfo(info, ctx.Identifier(0).getSymbol());
        currentScope.Insert(name, info);
    }

    @Override
    public void enterMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        String className = ctx.Identifier(0).getText();

        SymbolInfo classInfo = new SymbolInfo(className, SymbolInfo.SymbolType.CLASS);
        classInfo.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        setLocationInfo(classInfo, ctx.Identifier(0).getSymbol());
        currentScope.Insert(className, classInfo);

        pushScope(SymbolTable.ScopeType.CLASS, className);

        SymbolInfo mainInfo = new SymbolInfo("main", SymbolInfo.SymbolType.METHOD);
        mainInfo.dataType = "void";
        mainInfo.isStatic = true;
        mainInfo.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        String argsName = ctx.Identifier(1).getText();
        mainInfo.parameters.add(new SymbolInfo.ParameterInfo(argsName, "String[]"));
        setLocationInfo(mainInfo, ctx.Identifier(0).getSymbol());
        currentScope.Insert("main", mainInfo);

        pushScope(SymbolTable.ScopeType.METHOD, "main");

        SymbolInfo argsInfo = new SymbolInfo(argsName, SymbolInfo.SymbolType.PARAMETER);
        argsInfo.dataType = "String[]";
        argsInfo.isInitialized = true;
        setLocationInfo(argsInfo, ctx.Identifier(1).getSymbol());
        currentScope.Insert(argsName, argsInfo);
    }

    @Override
    public void exitMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        popScope();
        popScope();
    }

    @Override
    public void enterClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        String className = ctx.Identifier(0).getText();

        SymbolInfo info = new SymbolInfo(className, SymbolInfo.SymbolType.CLASS);
        info.isAbstract = "abstract".equals(ctx.getStart().getText());

        boolean hasExtends = false;
        boolean hasImplements = false;

        for (int i = 0; i < ctx.getChildCount(); i++) {
            String txt = ctx.getChild(i).getText();
            if ("extends".equals(txt))
                hasExtends = true;
            if ("implements".equals(txt))
                hasImplements = true;
        }

        int implementsStartIndex = 1;

        if (hasExtends) {
            info.parentClass = ctx.Identifier(1).getText();
            implementsStartIndex = 2;
        }

        if (hasImplements) {
            for (int k = implementsStartIndex; k < ctx.Identifier().size(); k++) {
                info.interfaces.add(ctx.Identifier(k).getText());
            }
        }

        setLocationInfo(info, ctx.Identifier(0).getSymbol());
        currentScope.Insert(className, info);

        pushScope(SymbolTable.ScopeType.CLASS, className);
    }

    @Override
    public void exitClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        String name = ctx.Identifier().getText();

        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.INTERFACE);

        for (int i = 0; i < ctx.getChildCount(); i++) {
            if ("extends".equals(ctx.getChild(i).getText())) {
                if (i + 1 < ctx.getChildCount()) {
                    info.parentClass = ctx.getChild(i + 1).getText();
                }
                break;
            }
        }

        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);

        pushScope(SymbolTable.ScopeType.INTERFACE, name);
    }

    @Override
    public void exitInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterInterfaceFieldDecl(javaMinusMinus2Parser.InterfaceFieldDeclContext ctx) {
        String name = ctx.Identifier().getText();

        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.FIELD);
        info.dataType = typeText(ctx.type());
        info.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        info.isStatic = true;
        info.isInitialized = true;

        if (ctx.expression() != null) {
            info.initialValue = ctx.expression().getText();
            extractArraySize(info, info.initialValue);
        }

        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);
    }

    @Override
    public void enterInterfaceMethodDecl(
            javaMinusMinus2Parser.InterfaceMethodDeclContext ctx) {
        String name = ctx.Identifier().getText();
        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.METHOD);
        info.dataType = typeText(ctx.type());
        info.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        info.isAbstract = true;
        fillParameters(info, ctx.parameterList());
        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);
    }

    @Override
    public void enterFieldDecl(javaMinusMinus2Parser.FieldDeclContext ctx) {
        javaMinusMinus2Parser.VarDeclContext v = ctx.varDecl();
        String name = v.Identifier().getText();

        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.FIELD);
        info.dataType = typeText(v.type());
        info.accessModifier = getAccessModifier(v.accessModifier());

        info.isInitialized = false;

        setLocationInfo(info, v.Identifier().getSymbol());
        currentScope.Insert(name, info);
    }

    @Override
    public void enterMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        String name = ctx.Identifier().getText();

        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.METHOD);
        info.dataType = typeText(ctx.type());
        info.accessModifier = getAccessModifier(ctx.accessModifier());
        info.isOverride = hasOverrideAnnotation(ctx);
        fillParameters(info, ctx.parameterList());
        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);

        pushScope(SymbolTable.ScopeType.METHOD, name);
        insertParameterSymbols(ctx.parameterList());
    }

    @Override
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterAbstractMethodDecl(
            javaMinusMinus2Parser.AbstractMethodDeclContext ctx) {
        String name = ctx.Identifier().getText();
        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.METHOD);
        info.dataType = typeText(ctx.type());
        info.accessModifier = getAccessModifier(ctx.accessModifier());
        info.isAbstract = true;
        info.isOverride = hasOverrideAnnotation(ctx);
        fillParameters(info, ctx.parameterList());
        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);
    }

    @Override
    public void enterCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        String name = ctx.Identifier().getText();

        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.CONSTRUCTOR);
        info.dataType = name;
        info.accessModifier = getAccessModifier(ctx.accessModifier());
        fillParameters(info, ctx.parameterList());
        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);

        pushScope(SymbolTable.ScopeType.METHOD, name + " (constructor)");
        insertParameterSymbols(ctx.parameterList());
    }

    @Override
    public void exitCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterLocalDecl(javaMinusMinus2Parser.LocalDeclContext ctx) {
        String name = ctx.Identifier().getText();
        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.VARIABLE);
        info.dataType = typeText(ctx.type());

        if (ctx.EQ() != null) {
            info.isInitialized = true;
            if (ctx.expression() != null) {
                info.initialValue = ctx.expression().getText();
                extractArraySize(info, info.initialValue);
            }
        } else {
            info.isInitialized = false;
        }

        setLocationInfo(info, ctx.Identifier().getSymbol());
        currentScope.Insert(name, info);
    }

    @Override
    public void enterBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        pushScope(SymbolTable.ScopeType.BLOCK,
                "block@" + ctx.getStart().getLine());
    }

    @Override
    public void exitBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        popScope();
    }

    @Override
    public void enterForStmt(javaMinusMinus2Parser.ForStmtContext ctx) {
        pushScope(SymbolTable.ScopeType.BLOCK,
                "for@" + ctx.getStart().getLine());
    }

    @Override
    public void exitForStmt(javaMinusMinus2Parser.ForStmtContext ctx) {
        popScope();
    }

}