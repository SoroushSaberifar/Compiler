import java.util.*;

public class SymbolTable {
    public enum ScopeType {
        GLOBAL, CLASS, METHOD, BLOCK
    }

    private Map<String, SymbolInfo> symbols;
    private SymbolTable parentScope;
    private List<SymbolTable> childScopes;
    private ScopeType scopeType;
    private String scopeName;

    public SymbolTable(ScopeType type, String name, SymbolTable parent) {
        this.symbols = new LinkedHashMap<>();
        this.childScopes = new ArrayList<>();
        this.scopeType = type;
        this.scopeName = name;
        this.parentScope = parent;
        if (parent != null) parent.childScopes.add(this);
    }

    public void insert(SymbolInfo info) {
        if (symbols.containsKey(info.name)) {
            System.err.println("Warning: Redeclaration of " + info.name + " in same scope");
        }
        symbols.put(info.name, info);
    }

    public SymbolInfo lookup(String name) {
        if (symbols.containsKey(name)) return symbols.get(name);
        if (parentScope != null) return parentScope.lookup(name);
        return null;
    }

    public SymbolInfo lookupCurrentScopeOnly(String name) {
        return symbols.get(name);
    }

    public SymbolTable getParent() {
        return parentScope;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeName() {
        return scopeName;
    }

    public List<SymbolTable> getChildScopes() {
        return childScopes;
    }

    public Map<String, SymbolInfo> getAllSymbols() {
        return symbols;
    }

    public void printTree(int indent) {
        String indentStr = "  ".repeat(indent);
        System.out.println(indentStr + "┌─ Scope: " + scopeType + (scopeName != null ? " (" + scopeName + ")" : ""));
        for (SymbolInfo sym : symbols.values()) {
            System.out.println(indentStr + "│  " + sym);
        }
        for (SymbolTable child : childScopes) {
            child.printTree(indent + 1);
        }
    }
}