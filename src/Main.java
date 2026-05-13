import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.CharStreams;

import java.io.*;
import java.util.*;

import grammar.javaMinusMinus2Lexer;
import grammar.javaMinusMinus2Parser;
import grammar.javaMinusMinus2BaseListener;

public class Main {
    private static List<String> errors = new ArrayList<>();
    private static SymbolTable globalTable;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <input-file>");
            return;
        }

        String fileName = args[0];
        String input = readFile(fileName);

        System.out.println("===== TOKEN OUTPUT =====");
        tokenizeAndCollect(input);

        if (!errors.isEmpty()) {
            System.out.println("\n===== ERRORS =====");
            for (String err : errors) {
                System.out.println(err);
            }
            return;
        }

        System.out.println("\n===== SYMBOL TABLE =====");
        globalTable = new SymbolTable(SymbolTable.ScopeType.GLOBAL, null, null);
        buildSymbolTable(input);
        globalTable.printTree(0);
    }

    private static void tokenizeAndCollect(String input) {
        CharStream stream = CharStreams.fromString(input);
        javaMinusMinus2Lexer lexer = new javaMinusMinus2Lexer(stream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        
        tokenStream.fill();
        
        for (Token token : tokenStream.getTokens()) {
            if (token.getType() != Token.EOF) {
                int type = token.getType();
                String tokenName = lexer.getVocabulary().getDisplayName(type);
                if (tokenName.startsWith("'")) {
                    tokenName = token.getText();
                }
                String text = token.getText().replace("\n", "\\n").replace("\r", "\\r");
                System.out.println(tokenName + " : " + text);
            }
        }
    }

    private static void buildSymbolTable(String input) {
        CharStream stream = CharStreams.fromString(input);
        javaMinusMinus2Lexer lexer = new javaMinusMinus2Lexer(stream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        javaMinusMinus2Parser parser = new javaMinusMinus2Parser(tokenStream);
        
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, 
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add("Line " + line + ":" + charPositionInLine + " - " + msg);
            }
        });
        
        ParseTree tree = parser.program();
        SimpleSymbolTableBuilder builder = new SimpleSymbolTableBuilder(globalTable);
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(builder, tree);
    }

    private static String readFile(String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}

class SimpleSymbolTableBuilder extends javaMinusMinus2BaseListener {
    private SymbolTable currentScope;
    private Stack<SymbolTable> scopeStack;
    
    public SimpleSymbolTableBuilder(SymbolTable global) {
        this.currentScope = global;
        this.scopeStack = new Stack<>();
        scopeStack.push(global);
    }
    
    @Override
    public void enterClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        String className = ctx.Identifier(0).getText();
        System.out.println("DEBUG: Found class: " + className);
        
        SymbolInfo classInfo = new SymbolInfo(className, SymbolInfo.SymbolType.CLASS);
        classInfo.dataType = "class";
        currentScope.insert(classInfo);
        
        SymbolTable classScope = new SymbolTable(SymbolTable.ScopeType.CLASS, className, currentScope);
        scopeStack.push(classScope);
        currentScope = classScope;
    }
    
    @Override
    public void exitClassDecl(javaMinusMinus2Parser.ClassDeclContext ctx) {
        scopeStack.pop();
        currentScope = scopeStack.peek();
    }
    
    @Override
    public void enterMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        String methodName = ctx.Identifier().getText();
        System.out.println("DEBUG: Found method: " + methodName);
        
        SymbolInfo methodInfo = new SymbolInfo(methodName, SymbolInfo.SymbolType.METHOD);
        
        if (ctx.type() != null) {
            methodInfo.dataType = ctx.type().getText();
        } else if (ctx.getChild(0) != null && ctx.getChild(0).getText().equals("void")) {
            methodInfo.dataType = "void";
        } else {
            methodInfo.dataType = "void";
        }
        
        if (ctx.accessModifier() != null) {
            String access = ctx.accessModifier().getText();
            if (access.equals("public")) methodInfo.accessModifier = SymbolInfo.AccessModifier.PUBLIC;
            else if (access.equals("private")) methodInfo.accessModifier = SymbolInfo.AccessModifier.PRIVATE;
            else if (access.equals("protected")) methodInfo.accessModifier = SymbolInfo.AccessModifier.PROTECTED;
        }
        
        currentScope.insert(methodInfo);
        
        SymbolTable methodScope = new SymbolTable(SymbolTable.ScopeType.METHOD, methodName, currentScope);
        scopeStack.push(methodScope);
        currentScope = methodScope;
        
        if (ctx.parameterList() != null) {
            for (javaMinusMinus2Parser.ParameterContext param : ctx.parameterList().parameter()) {
                String paramType = param.type().getText();
                String paramName = param.Identifier().getText();
                SymbolInfo paramInfo = new SymbolInfo(paramName, SymbolInfo.SymbolType.PARAMETER);
                paramInfo.dataType = paramType;
                currentScope.insert(paramInfo);
                methodInfo.parameters.add(new SymbolInfo.ParameterInfo(paramName, paramType));
            }
        }
    }
    
    @Override
    public void exitMethodDecl(javaMinusMinus2Parser.MethodDeclContext ctx) {
        scopeStack.pop();
        currentScope = scopeStack.peek();
    }
    
    @Override
    public void enterFieldDecl(javaMinusMinus2Parser.FieldDeclContext ctx) {
        if (ctx.varDecl() != null) {
            String fieldType = ctx.varDecl().type().getText();
            String fieldName = ctx.varDecl().Identifier().getText();
            System.out.println("DEBUG: Found field: " + fieldName);
            
            SymbolInfo fieldInfo = new SymbolInfo(fieldName, SymbolInfo.SymbolType.FIELD);
            fieldInfo.dataType = fieldType;
            fieldInfo.scopeLevel = "class";
            currentScope.insert(fieldInfo);
        }
    }
    
    @Override
    public void enterLocalDeclStmt(javaMinusMinus2Parser.LocalDeclStmtContext ctx) {
        if (ctx.localDecl() != null) {
            String varType = ctx.localDecl().type().getText();
            String varName = ctx.localDecl().Identifier().getText();
            System.out.println("DEBUG: Found local var: " + varName);
            
            SymbolInfo varInfo = new SymbolInfo(varName, SymbolInfo.SymbolType.VARIABLE);
            varInfo.dataType = varType;
            varInfo.scopeLevel = "method";
            currentScope.insert(varInfo);
        }
    }
}