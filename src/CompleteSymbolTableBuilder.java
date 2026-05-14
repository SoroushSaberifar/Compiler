import grammar.javaMinusMinus2BaseListener;
import grammar.javaMinusMinus2Parser;
import java.util.*;

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
        scopeStack.pop();
        currentScope = scopeStack.peek();
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

    // ======================== حلقه for ========================
    @Override
    public void enterForStmt(javaMinusMinus2Parser.ForStmtContext ctx) {
        SymbolTable forScope = new SymbolTable(SymbolTable.ScopeType.BLOCK, "for-loop", currentScope);
        pushScope(forScope);
    }

    @Override
    public void exitForStmt(javaMinusMinus2Parser.ForStmtContext ctx) {
        popScope();
    }

    // ======================== mainClass ========================
    @Override
    public void enterMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        String className = ctx.Identifier(0).getText();
        addClassSymbol(className, false, null, null);
        SymbolTable classScope = new SymbolTable(SymbolTable.ScopeType.CLASS, className, currentScope);
        pushScope(classScope);

        String methodName = "main";
        SymbolInfo mainMethod = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        mainMethod.dataType = "void";
        mainMethod.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        mainMethod.isStatic = true;
        currentScope.insert(mainMethod);

        SymbolTable methodScope = new SymbolTable(SymbolTable.ScopeType.METHOD, methodName, currentScope);
        pushScope(methodScope);

        String paramName = ctx.Identifier(1).getText();
        SymbolInfo param = new SymbolInfo(paramName, SymbolInfo.SymbolType.PARAMETER);
        param.dataType = "String[]";
        param.scopeLevel = "method";
        currentScope.insert(param);
    }

    @Override
    public void exitMainClass(javaMinusMinus2Parser.MainClassContext ctx) {
        popScope();
        popScope();
    }

    // ======================== classDecl ========================
    @Override
    public void enterClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        // اطمینان از اینکه این Rule واقعاً یک کلاس است
        String firstChild = ctx.getChild(0).getText();
        if (!firstChild.equals("class") && !firstChild.equals("abstract"))
            return;
        if (firstChild.equals("abstract") && !ctx.getChild(1).getText().equals("class"))
            return;

        String className = ctx.Identifier(0).getText();
        boolean isAbstract = firstChild.equals("abstract");

        String parent = null;
        List<String> interfaces = new ArrayList<>();

        // جستجو برای "extends" و "implements" در فرزندان
        for (int i = 0; i < ctx.getChildCount(); i++) {
            String text = ctx.getChild(i).getText();
            if (text.equals("extends") && i + 1 < ctx.getChildCount()) {
                parent = ctx.getChild(i + 1).getText();
            } else if (text.equals("implements")) {
                for (int j = i + 1; j < ctx.getChildCount(); j++) {
                    String t = ctx.getChild(j).getText();
                    if (t.equals("{"))
                        break;
                    if (!t.equals(",") && !t.equals("implements")) {
                        interfaces.add(t);
                    }
                }
            }
        }

        addClassSymbol(className, isAbstract, parent, interfaces);
        SymbolTable classScope = new SymbolTable(SymbolTable.ScopeType.CLASS, className, currentScope);
        pushScope(classScope);
    }

    @Override
    public void exitClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        popScope();
    }

    private void addClassSymbol(String name, boolean isAbstract, String parent, List<String> interfaces) {
        SymbolInfo info = new SymbolInfo(name, SymbolInfo.SymbolType.CLASS);
        info.dataType = "class";
        info.isAbstract = isAbstract;
        info.parentClass = parent;
        if (interfaces != null && !interfaces.isEmpty())
            info.implementedInterfaces = interfaces;
        currentScope.insert(info);
    }

    // ======================== interface ========================
    @Override
    public void enterInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        String interfaceName = ctx.Identifier().getText();
        SymbolInfo info = new SymbolInfo(interfaceName, SymbolInfo.SymbolType.INTERFACE);
        info.dataType = "interface";
        currentScope.insert(info);
        SymbolTable interfaceScope = new SymbolTable(SymbolTable.ScopeType.INTERFACE, interfaceName, currentScope);
        pushScope(interfaceScope);
    }

    @Override
    public void exitInterfaceDecl(javaMinusMinus2Parser.InterfaceDeclContext ctx) {
        popScope();
    }

    @Override
    public void enterInterfaceFieldDecl(javaMinusMinus2Parser.InterfaceFieldDeclContext ctx) {
        String fieldType = ctx.type().getText();
        String fieldName = ctx.Identifier().getText();
        String initValue = ctx.expression().getText();
        SymbolInfo field = new SymbolInfo(fieldName, SymbolInfo.SymbolType.FIELD);
        field.dataType = fieldType;
        field.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        field.isStatic = true;
        field.isFinal = true;
        field.initialValue = initValue;
        field.scopeLevel = "interface";
        currentScope.insert(field);
    }

    @Override
    public void enterInterfaceMethodDecl(javaMinusMinus2Parser.InterfaceMethodDeclContext ctx) {
        String methodName = ctx.Identifier().getText();
        String returnType = (ctx.type() != null) ? ctx.type().getText() : "void";
        SymbolInfo method = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        method.dataType = returnType;
        method.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
        method.isAbstract = true;
        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                method.parameters
                        .add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
            }
        }
        currentScope.insert(method);
    }

    // ======================== method ========================
    @Override
    public void enterMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        String methodName = ctx.Identifier().getText();
        String returnType = (ctx.type() != null) ? ctx.type().getText() : "void";
        boolean isOverride = ctx.getText().contains("@Override");
        SymbolInfo.AccessModifier access = getAccessModifier(ctx.accessModifier());

        SymbolInfo method = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        method.dataType = returnType;
        method.isOverride = isOverride;
        method.accessModifier = access;
        method.isAbstract = false;

        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                method.parameters
                        .add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
            }
        }
        currentScope.insert(method);

        SymbolTable methodScope = new SymbolTable(SymbolTable.ScopeType.METHOD, methodName, currentScope);
        pushScope(methodScope);

        for (SymbolInfo.ParameterInfo p : method.parameters) {
            SymbolInfo paramSym = new SymbolInfo(p.name, SymbolInfo.SymbolType.PARAMETER);
            paramSym.dataType = p.type;
            paramSym.scopeLevel = "method";
            currentScope.insert(paramSym);
        }
    }

    @Override
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        popScope();
    }

    // ======================== abstract method ========================
    @Override
    public void enterAbstractMethodDecl(javaMinusMinus2Parser.AbstractMethodDeclContext ctx) {
        String methodName = ctx.Identifier().getText();
        String returnType = (ctx.type() != null) ? ctx.type().getText() : "void";
        boolean isOverride = ctx.getText().contains("@Override");
        SymbolInfo.AccessModifier access = getAccessModifier(ctx.accessModifier());

        SymbolInfo method = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        method.dataType = returnType;
        method.isOverride = isOverride;
        method.isAbstract = true;
        method.accessModifier = access;

        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                method.parameters
                        .add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
            }
        }
        currentScope.insert(method);
    }

    // ======================== constructor ========================
    @Override
    public void enterCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        String ctorName = ctx.Identifier().getText();
        SymbolInfo ctor = new SymbolInfo(ctorName, SymbolInfo.SymbolType.CONSTRUCTOR);
        ctor.dataType = ctorName;
        ctor.accessModifier = getAccessModifier(ctx.accessModifier());

        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                ctor.parameters.add(new SymbolInfo.ParameterInfo(param.Identifier().getText(), param.type().getText()));
            }
        }
        currentScope.insert(ctor);

        SymbolTable ctorScope = new SymbolTable(SymbolTable.ScopeType.METHOD, ctorName + "()", currentScope);
        pushScope(ctorScope);

        for (SymbolInfo.ParameterInfo p : ctor.parameters) {
            SymbolInfo paramSym = new SymbolInfo(p.name, SymbolInfo.SymbolType.PARAMETER);
            paramSym.dataType = p.type;
            paramSym.scopeLevel = "constructor";
            currentScope.insert(paramSym);
        }
    }

    @Override
    public void exitCtorDecl(javaMinusMinus2Parser.CtorDeclContext ctx) {
        popScope();
    }

    // ======================== field ========================
    @Override
    public void enterFieldDecl(javaMinusMinus2Parser.FieldDeclContext ctx) {
        if (ctx.varDecl() != null) {
            String fieldType = ctx.varDecl().type().getText();
            String fieldName = ctx.varDecl().Identifier().getText();
            SymbolInfo.AccessModifier access = getAccessModifier(ctx.varDecl().accessModifier());
            SymbolInfo field = new SymbolInfo(fieldName, SymbolInfo.SymbolType.FIELD);
            field.dataType = fieldType;
            field.accessModifier = access;
            field.scopeLevel = "class";
            currentScope.insert(field);
        }
    }

    // ======================== local variables ========================
    @Override
    public void enterLocalDeclStmt(javaMinusMinus2Parser.LocalDeclStmtContext ctx) {
        addLocalVariable(ctx.localDecl());
    }

    @Override
    public void enterLocalDeclNoSemi(javaMinusMinus2Parser.LocalDeclNoSemiContext ctx) {
        String varType = ctx.type().getText();
        String varName = ctx.Identifier().getText();
        String initValue = (ctx.expression() != null) ? ctx.expression().getText() : null;
        SymbolInfo varInfo = new SymbolInfo(varName, SymbolInfo.SymbolType.VARIABLE);
        varInfo.dataType = varType;
        varInfo.scopeLevel = currentScope.getScopeType().toString();
        varInfo.initialValue = initValue;
        currentScope.insert(varInfo);
    }

    private void addLocalVariable(javaMinusMinus2Parser.LocalDeclContext localCtx) {
        String varType = localCtx.type().getText();
        String varName = localCtx.Identifier().getText();
        String initValue = (localCtx.expression() != null) ? localCtx.expression().getText() : null;
        SymbolInfo varInfo = new SymbolInfo(varName, SymbolInfo.SymbolType.VARIABLE);
        varInfo.dataType = varType;
        varInfo.scopeLevel = currentScope.getScopeType().toString();
        varInfo.initialValue = initValue;
        currentScope.insert(varInfo);
    }

    // ======================== block ========================
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