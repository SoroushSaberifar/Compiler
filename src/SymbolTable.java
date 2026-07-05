import java.util.*;

public class SymbolTable {

    public enum ScopeType {
        GLOBAL, CLASS, METHOD, BLOCK, INTERFACE
    }

    private final Map<String, SymbolInfo> symbols;
    private final SymbolTable parentScope;
    private final List<SymbolTable> childScopes;
    private final ScopeType scopeType;
    private final String scopeName;
    private final int scopeId;
    private static int nextScopeId = 0;

    private final List<String> semanticErrors;
    private boolean errorReportingEnabled;

    public SymbolTable(ScopeType type, String name, SymbolTable parent) {
        this.symbols = new LinkedHashMap<>();
        this.childScopes = new ArrayList<>();
        this.scopeType = type;
        this.scopeName = name;
        this.parentScope = parent;
        this.scopeId = nextScopeId++;
        this.semanticErrors = new ArrayList<>();
        this.errorReportingEnabled = true;
        if (parent != null) {
            parent.childScopes.add(this);
        }
    }

    public String getFullScopeName() {
        if (scopeType == ScopeType.GLOBAL) {
            return "GLOBAL";
        }

        StringBuilder sb = new StringBuilder(scopeName != null ? scopeName : "block");
        SymbolTable current = this.parentScope;

        while (current != null && current.scopeType != ScopeType.GLOBAL) {
            String pName = current.scopeName != null ? current.scopeName : "block";
            sb.insert(0, pName + "::");
            current = current.parentScope;
        }
        return sb.toString();
    }

    public static void resetScopeIds() {
        nextScopeId = 0;
    }

    public void disableErrorReporting() {
        this.errorReportingEnabled = false;
    }

    public void enableErrorReporting() {
        this.errorReportingEnabled = true;
    }

    public void addSemanticError(String error) {
        if (!errorReportingEnabled)
            return;
        SymbolTable root = getRootScope();
        if (!root.semanticErrors.contains(error)) {
            root.semanticErrors.add(error);
        }
    }

    public List<String> getSemanticErrors() {
        return Collections.unmodifiableList(getRootScope().semanticErrors);
    }

    public boolean hasSemanticErrors() {
        return !getRootScope().semanticErrors.isEmpty();
    }

    public void clearSemanticErrors() {
        getRootScope().semanticErrors.clear();
    }

    public void Insert(String identName, SymbolInfo attributes) {
        attributes.name = identName;
        insert(attributes);
    }

    public void insert(SymbolInfo info) {
        SymbolInfo existing = symbols.get(info.name);

        if (existing == null) {
            symbols.put(info.name, info);
            return;
        }

        if (info.isCallable() && existing.isCallable()) {
            if (findSameSignature(existing, info) != null) {
                addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                        + " - Duplicate method/constructor: "
                        + info.name + info.getParameterString());
                return;
            }
            existing.addOverload(info);
            return;
        }

        if (info.isCallable() != existing.isCallable()) {
            addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                    + " - Name conflict: '" + info.name + "' cannot be declared as "
                    + info.symbolType + " because a " + existing.symbolType
                    + " with the same name exists in this scope");
            return;
        }

        addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                + " - Redeclaration of '" + info.name + "' as " + info.symbolType
                + " (previous declaration was " + existing.symbolType
                + " in the same scope)");
    }

    private SymbolInfo findSameSignature(SymbolInfo base, SymbolInfo candidate) {
        for (SymbolInfo v : base.getAllVersions()) {
            if (v.sameSignature(candidate))
                return v;
        }
        return null;
    }

    public boolean insertIfAbsent(SymbolInfo info) {
        if (symbols.containsKey(info.name))
            return false;
        symbols.put(info.name, info);
        return true;
    }

    public void insertForce(SymbolInfo info) {
        SymbolInfo existing = symbols.get(info.name);
        if (existing != null && existing.isCallable() && info.isCallable()
                && findSameSignature(existing, info) == null) {
            existing.addOverload(info);
        } else {
            symbols.put(info.name, info);
        }
    }

    public SymbolInfo Lookup(String identName) {
        return lookup(identName);
    }

    public SymbolInfo lookup(String name) {
        SymbolInfo found = symbols.get(name);
        if (found != null)
            return found;
        return (parentScope != null) ? parentScope.lookup(name) : null;
    }

    public SymbolInfo lookupCurrentScopeOnly(String name) {
        return symbols.get(name);
    }

    public boolean contains(String name) {
        return lookup(name) != null;
    }

    public boolean containsInCurrentScope(String name) {
        return lookupCurrentScopeOnly(name) != null;
    }

    public List<SymbolInfo> lookupMethodOverloads(String name) {
        SymbolTable searchStart = getCurrentClassScope();
        if (searchStart == null)
            searchStart = getEnclosingScope(ScopeType.INTERFACE);
        if (searchStart == null)
            searchStart = this;

        SymbolInfo base = searchStart.lookup(name);
        if (base != null && base.isCallable()) {
            return base.getAllVersions();
        }
        return new ArrayList<>();
    }

    public SymbolInfo lookupInClassHierarchy(String className, String memberName) {
        Set<String> visited = new HashSet<>();
        String current = className;
        while (current != null && visited.add(current)) {
            SymbolTable typeScope = findTypeScope(current);
            if (typeScope == null)
                return null;
            SymbolInfo member = typeScope.lookupCurrentScopeOnly(memberName);
            if (member != null)
                return member;
            SymbolInfo classInfo = getRootScope().lookupCurrentScopeOnly(current);
            current = (classInfo != null) ? classInfo.parentClass : null;
        }
        return null;
    }

    public SymbolTable getParent() {
        return parentScope;
    }

    public List<SymbolTable> getChildScopes() {
        return Collections.unmodifiableList(childScopes);
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeName() {
        return scopeName;
    }

    public int getScopeId() {
        return scopeId;
    }

    public Map<String, SymbolInfo> getSymbols() {
        return Collections.unmodifiableMap(symbols);
    }

    public Collection<SymbolInfo> getAllSymbols() {
        return this.symbols.values();
    }

    public List<SymbolInfo> getSymbolsList() {
        return new ArrayList<>(symbols.values());
    }

    public List<SymbolInfo> getSymbolsByType(SymbolInfo.SymbolType type) {
        List<SymbolInfo> result = new ArrayList<>();
        for (SymbolInfo sym : symbols.values()) {
            if (sym.symbolType == type)
                result.add(sym);
        }
        return result;
    }

    public SymbolTable getRootScope() {
        SymbolTable current = this;
        while (current.parentScope != null)
            current = current.parentScope;
        return current;
    }

    public SymbolTable findClassScope(String className) {
        for (SymbolTable child : getRootScope().childScopes) {
            if (child.scopeType == ScopeType.CLASS && Objects.equals(child.scopeName, className)) {
                return child;
            }
        }
        return null;
    }

    public SymbolTable findTypeScope(String typeName) {
        for (SymbolTable child : getRootScope().childScopes) {
            if ((child.scopeType == ScopeType.CLASS || child.scopeType == ScopeType.INTERFACE)
                    && Objects.equals(child.scopeName, typeName)) {
                return child;
            }
        }
        return null;
    }

    public SymbolTable getEnclosingScope(ScopeType type) {
        SymbolTable current = this;
        while (current != null) {
            if (current.scopeType == type)
                return current;
            current = current.parentScope;
        }
        return null;
    }

    public SymbolTable getCurrentClassScope() {
        return getEnclosingScope(ScopeType.CLASS);
    }

    public SymbolTable getCurrentMethodScope() {
        return getEnclosingScope(ScopeType.METHOD);
    }

    public void printTree(int indent) {
        String indentStr = "  ".repeat(indent);
        StringBuilder header = new StringBuilder(indentStr + "┌─ Scope: " + scopeType);
        if (scopeName != null && !scopeName.isEmpty())
            header.append(" \"").append(scopeName).append("\"");
        header.append(" [id=").append(scopeId).append("]");
        System.out.println(header);

        boolean printedAny = false;
        for (SymbolInfo sym : symbols.values()) {
            if (!sym.isInternalSymbol) {
                System.out.println(indentStr + "│  • " + sym.toString(indentStr + "│ "));
                printedAny = true;
            }
        }
        if (!printedAny) {
            System.out.println(indentStr + "│  (no symbols)");
        }
        for (SymbolTable child : childScopes) {
            child.printTree(indent + 1);
        }
    }

    public void printCompact() {
        System.out.println("=== Symbol Table (Compact View) ===");
        printCompactRecursive(0);
    }

    private void printCompactRecursive(int indent) {
        String indentStr = "  ".repeat(indent);
        System.out.println(indentStr + "[" + scopeType
                + (scopeName != null ? ": " + scopeName : "") + "]");
        for (SymbolInfo sym : symbols.values()) {
            if (sym.isInternalSymbol)
                continue;
            System.out.println(indentStr + "  " + sym.getKindString() + " " + sym.name
                    + (sym.isCallable() ? sym.getParameterString() : "")
                    + (sym.dataType != null ? " : " + sym.dataType : ""));
            if (sym.overloads != null) {
                for (SymbolInfo ov : sym.overloads) {
                    System.out.println(indentStr + "    overload: " + ov.name + ov.getParameterString());
                }
            }
        }
        for (SymbolTable child : childScopes) {
            child.printCompactRecursive(indent + 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toStringRecursive(sb, 0);
        return sb.toString();
    }

    private void toStringRecursive(StringBuilder sb, int indent) {
        String indentStr = "  ".repeat(indent);
        sb.append(indentStr).append("Scope: ").append(scopeType);
        if (scopeName != null)
            sb.append(" (").append(scopeName).append(")");
        sb.append("\n");
        for (SymbolInfo sym : symbols.values()) {
            if (!sym.isInternalSymbol) {
                sb.append(indentStr).append("  - ").append(sym.toString(indentStr + "  ")).append("\n");
            }
        }
        for (SymbolTable child : childScopes) {
            child.toStringRecursive(sb, indent + 1);
        }
    }

    private void collectAllSymbols(SymbolTable table, List<SymbolInfo> targetList) {
        for (SymbolInfo sym : table.symbols.values()) {
            if (sym.isInternalSymbol)
                continue;

            if (sym.scope == null || sym.scope.isEmpty()) {
                sym.scope = table.getFullScopeName();
            }

            targetList.add(sym);

            if (sym.overloads != null) {
                for (SymbolInfo ov : sym.overloads) {
                    if (ov.scope == null || ov.scope.isEmpty()) {
                        ov.scope = table.getFullScopeName();
                    }
                    targetList.add(ov);
                }
            }
        }

        for (SymbolTable child : table.childScopes) {
            collectAllSymbols(child, targetList);
        }
    }

    public void printFlatTable() {
        System.out.println("Symbol Table");
        System.out.println(
                "---------------------------------------------------------------------------------------------------------");
        System.out.printf("%-5s | %-20s | %-15s | %-15s | %-25s | %-15s%n",
                "Index", "Name", "Kind", "Type", "Scope", "Initial Value");
        System.out.println(
                "---------------------------------------------------------------------------------------------------------");

        List<SymbolInfo> allSymbols = new ArrayList<>();
        collectAllSymbols(this, allSymbols);

        int index = 0;
        for (SymbolInfo sym : allSymbols) {
            if (sym.isInternalSymbol)
                continue;

            String kind = sym.getKindString();
            String type = sym.dataType != null ? sym.dataType : "N/A";

            if (sym.symbolType == SymbolInfo.SymbolType.CLASS || sym.symbolType == SymbolInfo.SymbolType.INTERFACE) {
                type = "N/A";
            }

            String initVal = sym.initialValue != null ? sym.initialValue : "$N/A$";
            String scopePath = sym.scope != null ? sym.scope : "GLOBAL";

            System.out.printf("%-5d | %-20s | %-15s | %-15s | %-25s | %-15s%n",
                    index++,
                    sym.name,
                    kind,
                    type,
                    scopePath,
                    initVal);
        }
        System.out.println(
                "---------------------------------------------------------------------------------------------------------");
    }

    public int getTotalSymbolCount() {
        int count = symbols.size();
        for (SymbolTable child : childScopes)
            count += child.getTotalSymbolCount();
        return count;
    }

    public int getDepth() {
        int depth = 0;
        SymbolTable current = this;
        while (current.parentScope != null) {
            depth++;
            current = current.parentScope;
        }
        return depth;
    }

    public void clear() {
        for (SymbolTable child : childScopes)
            child.clear();
        childScopes.clear();
        symbols.clear();
        semanticErrors.clear();
    }
}
