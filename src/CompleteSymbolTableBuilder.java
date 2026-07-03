import grammar.javaMinusMinus2BaseListener;
import grammar.javaMinusMinus2Parser;
import java.util.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;

public class CompleteSymbolTableBuilder extends javaMinusMinus2BaseListener {
    private SymbolTable currentScope;
    private Stack<SymbolTable> scopeStack;

    public CompleteSymbolTableBuilder(SymbolTable global) {
        this.currentScope = global;
        this.scopeStack = new Stack<>();
        scopeStack.push(global);
    }

    private void pushScope(SymbolTable scope) {
        scopeStack.push(scope);
        currentScope = scope;
    }

    private void popScope() {
        if (scopeStack.isEmpty()) {
            System.err.println("ERROR: Attempted to pop from empty stack!");
            return;
        }
        scopeStack.pop();
        if (!scopeStack.isEmpty()) {
            currentScope = scopeStack.peek();
        }
    }

    private void setLocationInfo(SymbolInfo info, ParserRuleContext ctx) {
        if (ctx != null && ctx.getStart() != null) {
            info.setLocation(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
    }

    private SymbolInfo.AccessModifier getAccessModifier(javaMinusMinus2Parser.AccessModifierContext ctx) {
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

    @Override
    public void enterImportDecl(javaMinusMinus2Parser.ImportDeclContext ctx) {
        StringBuilder importPath = new StringBuilder();
        for (int i = 0; i < ctx.Identifier().size(); i++) {
            if (i > 0)
                importPath.append(".");
            importPath.append(ctx.Identifier(i).getText());
        }

        if (ctx.getChildCount() > 0) {
            String lastChild = ctx.getChild(ctx.getChildCount() - 2).getText();
            if (lastChild.equals("*")) {
                importPath.append(".*");
            }
        }

        if (currentScope.getScopeType() == SymbolTable.ScopeType.GLOBAL) {
            SymbolInfo importInfo = new SymbolInfo(importPath.toString(), SymbolInfo.SymbolType.IMPORT);
            importInfo.dataType = "import";
            importInfo.scopeLevel = "global";
            setLocationInfo(importInfo, ctx);
            currentScope.insert(importInfo);
        }
    }

    // حذف اسکوپ‌های موازی برای جلوگیری از تداخل با enterBlockStmt
    @Override
    public void enterForStmt(javaMinusMinus2Parser.ForStmtContext ctx) {
    }

    @Override
    public void exitForStmt(javaMinusMinus2Parser.ForStmtContext ctx) {
    }

    @Override
    public void enterWhileStmt(javaMinusMinus2Parser.WhileStmtContext ctx) {
    }

    @Override
    public void exitWhileStmt(javaMinusMinus2Parser.WhileStmtContext ctx) {
    }

    @Override
    public void enterIfElseStmt(javaMinusMinus2Parser.IfElseStmtContext ctx) {
    }

    @Override
    public void exitIfElseStmt(javaMinusMinus2Parser.IfElseStmtContext ctx) {
    }

    @Override
    public void enterMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        if (ctx.Identifier() == null || ctx.Identifier().isEmpty())
            return;

        String className = ctx.Identifier(0).getText();

        if (currentScope.lookupCurrentScopeOnly(className) == null) {
            SymbolInfo classInfo = new SymbolInfo(className, SymbolInfo.SymbolType.CLASS);
            classInfo.dataType = "class";
            setLocationInfo(classInfo, ctx);
            currentScope.insert(classInfo);
        }

        SymbolTable classScope = new SymbolTable(SymbolTable.ScopeType.CLASS, className, currentScope);
        pushScope(classScope);

        String methodName = "main";
        SymbolInfo mainMethod = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        mainMethod.dataType = "void";
        mainMethod.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        mainMethod.isStatic = true;
        setLocationInfo(mainMethod, ctx);

        if (ctx.Identifier().size() > 1) {
            String paramName = ctx.Identifier(1).getText();
            mainMethod.parameters.add(new SymbolInfo.ParameterInfo(paramName, "String[]"));
        }

        currentScope.insert(mainMethod);

        SymbolTable methodScope = new SymbolTable(SymbolTable.ScopeType.METHOD, methodName, currentScope);
        pushScope(methodScope);

        for (SymbolInfo.ParameterInfo p : mainMethod.parameters) {
            SymbolInfo paramSym = new SymbolInfo(p.name, SymbolInfo.SymbolType.PARAMETER);
            paramSym.dataType = p.type;
            paramSym.scopeLevel = "method";
            setLocationInfo(paramSym, ctx);
            currentScope.insert(paramSym);
        }
    }

    @Override
    public void exitMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        if (ctx.Identifier() != null && !ctx.Identifier().isEmpty()) {
            popScope();
            popScope();
        }
    }

    @Override
    public void enterClassDecl(grammar.javaMinusMinus2Parser.ClassDeclContext ctx) {
        if (ctx.Identifier() == null || ctx.Identifier().isEmpty())
            return;

        String className = ctx.Identifier(0).getText();
        boolean isAbstract = false;
        if (ctx.getChildCount() > 0 && ctx.getChild(0).getText().equals("abstract")) {
            isAbstract = true;
        }

        String parent = null;
        List<String> interfaces = new ArrayList<>();
        int extendsIdx = -1, implementsIdx = -1, openBraceIdx = -1;

        for (int i = 0; i < ctx.getChildCount(); i++) {
            String childText = ctx.getChild(i).getText();
            if (childText.equals("extends"))
                extendsIdx = i;
            if (childText.equals("implements"))
                implementsIdx = i;
            if (childText.equals("{")) {
                openBraceIdx = i;
                break;
            }
        }

        for (int i = 1; i < ctx.Identifier().size(); i++) {
            org.antlr.v4.runtime.tree.TerminalNode idNode = ctx.Identifier(i);
            int currentChildPos = -1;
            for (int c = 0; c < ctx.getChildCount(); c++) {
                if (ctx.getChild(c) == idNode) {
                    currentChildPos = c;
                    break;
                }
            }

            if (openBraceIdx != -1 && currentChildPos >= openBraceIdx)
                break;

            if (extendsIdx != -1 && currentChildPos > extendsIdx
                    && (implementsIdx == -1 || currentChildPos < implementsIdx)) {
                parent = idNode.getText();
            } else if (implementsIdx != -1 && currentChildPos > implementsIdx) {
                interfaces.add(idNode.getText());
            }
        }

        if (currentScope.lookupCurrentScopeOnly(className) == null) {
            SymbolInfo info = new SymbolInfo(className, SymbolInfo.SymbolType.CLASS);
            info.dataType = "class";
            info.isAbstract = isAbstract;
            info.parentClass = parent;
            if (!interfaces.isEmpty())
                info.implementedInterfaces = interfaces;
            setLocationInfo(info, ctx);
            currentScope.insert(info);
        }

        SymbolTable classScope = new SymbolTable(SymbolTable.ScopeType.CLASS, className, currentScope);
        pushScope(classScope);
    }

    @Override
    public void exitClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        if (ctx.Identifier() != null && !ctx.Identifier().isEmpty()) {
            String className = ctx.Identifier(0).getText();
            if (currentScope.getScopeName().equals(className)) {
                popScope();
            }
        }
    }

    @Override
    public void enterInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        String interfaceName = ctx.Identifier().getText();

        if (currentScope.lookupCurrentScopeOnly(interfaceName) == null) {
            SymbolInfo info = new SymbolInfo(interfaceName, SymbolInfo.SymbolType.INTERFACE);
            info.dataType = "interface";
            setLocationInfo(info, ctx);
            currentScope.insert(info);
        }

        SymbolTable interfaceScope = new SymbolTable(SymbolTable.ScopeType.INTERFACE, interfaceName, currentScope);
        pushScope(interfaceScope);
    }

    @Override
    public void exitInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        if (ctx.Identifier() != null) {
            popScope();
        }
    }

    @Override
    public void enterInterfaceFieldDecl(javaMinusMinus2Parser.InterfaceFieldDeclContext ctx) {
        String fieldType = ctx.type().getText();
        String fieldName = ctx.Identifier().getText();
        String initValue = (ctx.expression() != null) ? ctx.expression().getText() : null;

        if (currentScope.lookupCurrentScopeOnly(fieldName) == null) {
            SymbolInfo field = new SymbolInfo(fieldName, SymbolInfo.SymbolType.FIELD);
            field.dataType = fieldType;
            field.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
            field.isStatic = true;
            field.isFinal = true;
            field.initialValue = initValue;
            field.scopeLevel = "interface";
            setLocationInfo(field, ctx);
            currentScope.insert(field);
        }
    }

    @Override
    public void enterInterfaceMethodDecl(javaMinusMinus2Parser.InterfaceMethodDeclContext ctx) {
        String methodName = ctx.Identifier().getText();
        String returnType = (ctx.type() != null) ? ctx.type().getText() : "void";

        SymbolInfo method = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        method.dataType = returnType;
        method.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        method.isAbstract = true;
        setLocationInfo(method, ctx);

        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                method.parameters
                        .add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
            }
        }
        currentScope.insert(method);
    }

    @Override
    public void enterMethodDecl(grammar.javaMinusMinus2Parser.MethodDeclContext ctx) {
        if (ctx == null || ctx.Identifier() == null)
            return;

        RuleContext parent = ctx.getParent();
        if (!(parent instanceof javaMinusMinus2Parser.ClassDeclContext
                || parent instanceof javaMinusMinus2Parser.InterfaceDeclContext)) {
            return;
        }

        String methodName = ctx.Identifier().getText();
        SymbolInfo existing = currentScope.lookupCurrentScopeOnly(methodName);
        if (existing != null && existing.symbolType == SymbolInfo.SymbolType.FIELD) {
            return;
        }

        String returnType = (ctx.type() != null) ? ctx.type().getText() : "void";

        boolean isOverride = false;
        boolean isAbstract = false;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            String txt = ctx.getChild(i).getText();
            if (txt.equals("@Override"))
                isOverride = true;
            if (txt.equals("abstract"))
                isAbstract = true;
        }

        SymbolInfo.AccessModifier access = getAccessModifier(ctx.accessModifier());

        SymbolInfo method = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        method.dataType = returnType;
        method.isOverride = isOverride;
        method.accessModifier = access;
        method.isAbstract = isAbstract;
        setLocationInfo(method, ctx);

        if (ctx.parameterList() != null) {
            for (grammar.javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                if (param.Identifier() != null && param.type() != null) {
                    method.parameters
                            .add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
                }
            }
        }

        currentScope.insert(method);
        if (isAbstract)
            return;

        SymbolTable methodScope = new SymbolTable(SymbolTable.ScopeType.METHOD, methodName, currentScope);
        pushScope(methodScope);

        for (SymbolInfo.ParameterInfo p : method.parameters) {
            if (currentScope.lookupCurrentScopeOnly(p.name) == null) {
                SymbolInfo paramSym = new SymbolInfo(p.name, SymbolInfo.SymbolType.PARAMETER);
                paramSym.dataType = p.type;
                paramSym.scopeLevel = "method";
                setLocationInfo(paramSym, ctx);
                currentScope.insert(paramSym);
            }
        }
    }

    @Override
    public void exitMethodDecl(grammar.javaMinusMinus2Parser.MethodDeclContext ctx) {
        if (ctx.Identifier() == null)
            return;

        String methodName = ctx.Identifier().getText();
        boolean isAbstract = false;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i).getText().equals("abstract"))
                isAbstract = true;
        }

        SymbolInfo existing = currentScope.lookupCurrentScopeOnly(methodName);
        if (existing != null && existing.symbolType == SymbolInfo.SymbolType.FIELD)
            return;

        if (!isAbstract)
            popScope();
    }

    @Override
    public void enterAbstractMethodDecl(grammar.javaMinusMinus2Parser.AbstractMethodDeclContext ctx) {
        if (ctx.Identifier() == null)
            return;

        String methodName = ctx.Identifier().getText();
        String returnType = (ctx.type() != null) ? ctx.type().getText() : "void";

        boolean isOverride = false;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i).getText().equals("@Override"))
                isOverride = true;
        }

        SymbolInfo.AccessModifier access = getAccessModifier(ctx.accessModifier());

        SymbolInfo method = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        method.dataType = returnType;
        method.isOverride = isOverride;
        method.isAbstract = true;
        method.accessModifier = access;
        setLocationInfo(method, ctx);

        if (ctx.parameterList() != null) {
            for (grammar.javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                if (param.Identifier() != null && param.type() != null) {
                    method.parameters
                            .add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
                }
            }
        }
        currentScope.insert(method);
    }

    @Override
    public void enterCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        String ctorName = ctx.Identifier().getText();
        SymbolInfo ctor = new SymbolInfo(ctorName, SymbolInfo.SymbolType.CONSTRUCTOR);
        ctor.dataType = ctorName;
        ctor.accessModifier = getAccessModifier(ctx.accessModifier());
        setLocationInfo(ctor, ctx);

        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                ctor.parameters.add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
            }
        }
        currentScope.insert(ctor);

        SymbolTable ctorScope = new SymbolTable(SymbolTable.ScopeType.METHOD, ctorName, currentScope);
        pushScope(ctorScope);

        for (SymbolInfo.ParameterInfo p : ctor.parameters) {
            if (currentScope.lookupCurrentScopeOnly(p.name) == null) {
                SymbolInfo paramSym = new SymbolInfo(p.name, SymbolInfo.SymbolType.PARAMETER);
                paramSym.dataType = p.type;
                paramSym.scopeLevel = "constructor";
                setLocationInfo(paramSym, ctx);
                currentScope.insert(paramSym);
            }
        }
    }

    @Override
    public void exitCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterFieldDecl(javaMinusMinus2Parser.FieldDeclContext ctx) {
        if (ctx.varDecl() != null) {
            String fieldType = ctx.varDecl().type().getText();
            String fieldName = ctx.varDecl().Identifier().getText();
            SymbolInfo.AccessModifier access = getAccessModifier(ctx.varDecl().accessModifier());

            if (currentScope.lookupCurrentScopeOnly(fieldName) == null) {
                SymbolInfo field = new SymbolInfo(fieldName, SymbolInfo.SymbolType.FIELD);
                field.dataType = fieldType;
                field.accessModifier = access;
                field.scopeLevel = "class";
                setLocationInfo(field, ctx);
                currentScope.insert(field);
            }
        }
    }

    @Override
    public void enterLocalDeclStmt(javaMinusMinus2Parser.LocalDeclStmtContext ctx) {
        if (ctx.localDecl() != null) {
            String varType = ctx.localDecl().type().getText();
            String varName = ctx.localDecl().Identifier().getText();

            if (currentScope.lookupCurrentScopeOnly(varName) == null) {
                SymbolInfo varInfo = new SymbolInfo(varName, SymbolInfo.SymbolType.VARIABLE);
                varInfo.dataType = varType;
                varInfo.scopeLevel = "local";
                setLocationInfo(varInfo, ctx); // رفع مشکل فراموشی تنظیم لوکیشن خط خطا
                currentScope.insert(varInfo);
            }
        }
    }

    @Override
    public void enterLocalDeclNoSemi(javaMinusMinus2Parser.LocalDeclNoSemiContext ctx) {
        if (ctx.Identifier() == null || ctx.type() == null)
            return;
        String varType = ctx.type().getText();
        String varName = ctx.Identifier().getText();

        if (currentScope.lookupCurrentScopeOnly(varName) == null) {
            SymbolInfo varInfo = new SymbolInfo(varName, SymbolInfo.SymbolType.VARIABLE);
            varInfo.dataType = varType;
            varInfo.scopeLevel = "local";
            setLocationInfo(varInfo, ctx);
            currentScope.insert(varInfo);
        }
    }

    @Override
    public void enterBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        SymbolTable blockScope = new SymbolTable(SymbolTable.ScopeType.BLOCK, "block", currentScope);
        pushScope(blockScope);
    }

    @Override
    public void exitBlockStmt(javaMinusMinus2Parser.BlockStmtContext ctx) {
        popScope();
    }
}