import java.util.*;

public class SymbolTable {

    public enum ScopeType {
        GLOBAL, CLASS, METHOD, BLOCK, INTERFACE
    }

    private final Map<String, SymbolInfo> symbols;
    private final List<SymbolTable> childScopes;
    private final List<String> semanticErrors;
    private final SymbolTable parentScope;
    private final ScopeType scopeType;
    private final String scopeName;
    private boolean errorReportingEnabled;

    public SymbolTable(ScopeType type, String name, SymbolTable parent) {
        this.symbols = new LinkedHashMap<>();
        this.childScopes = new ArrayList<>();
        this.scopeType = type;
        this.scopeName = name;
        this.parentScope = parent;
        this.semanticErrors = new ArrayList<>();
        this.errorReportingEnabled = true;
        if (parent != null) {
            parent.childScopes.add(this);
        }
    }

    public void enableErrorReporting() {
        this.errorReportingEnabled = true;
    }

    public SymbolTable getRootScope() {
        SymbolTable current = this;
        while (current.parentScope != null)
            current = current.parentScope;
        return current;
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

    public void Insert(String identName, SymbolInfo attributes) {
        attributes.name = identName;
        insert(attributes);
    }

    private SymbolInfo findLocalShadowConflict(String name) {
        SymbolTable current = this.parentScope;
        while (current != null
                && (current.scopeType == ScopeType.BLOCK || current.scopeType == ScopeType.METHOD)) {
            SymbolInfo found = current.symbols.get(name);
            if (found != null && (found.symbolType == SymbolInfo.SymbolType.VARIABLE
                    || found.symbolType == SymbolInfo.SymbolType.PARAMETER)) {
                return found;
            }
            if (current.scopeType == ScopeType.METHOD)
                break;
            current = current.parentScope;
        }
        return null;
    }

    public void insert(SymbolInfo info) {
        SymbolInfo existing = symbols.get(info.name);

        if (existing != null) {
            boolean isCurrentClassOrImport = (info.symbolType == SymbolInfo.SymbolType.CLASS
                    || info.symbolType == SymbolInfo.SymbolType.IMPORT);
            boolean isExistingClassOrImport = (existing.symbolType == SymbolInfo.SymbolType.CLASS
                    || existing.symbolType == SymbolInfo.SymbolType.IMPORT);

            if (isCurrentClassOrImport && isExistingClassOrImport) {
                addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                        + " - Name conflict: " + info.getKindString() + " '" + info.name
                        + "' conflicts with an existing " + existing.getKindString() + " in the global scope");
                return;
            }
        }

        if (existing == null) {
            if ((info.symbolType == SymbolInfo.SymbolType.VARIABLE)
                    && findLocalShadowConflict(info.name) != null) {
                addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                        + " - Redeclaration of variable '" + info.name
                        + "' (already declared in an enclosing method/block scope)");
                return;
            }
            symbols.put(info.name, info);
            return;
        }

        if (info.isCallable() && existing.isCallable()) {
            if (!existing.addOverload(info)) {
                addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                        + " - Duplicate method/constructor: "
                        + info.name + info.getParameterString());
            }
            return;
        }

        if (info.isCallable() != existing.isCallable()) {
            addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                    + " - Name conflict: '" + info.name + "' cannot be declared as "
                    + info.getKindString() + " because a " + existing.getKindString()
                    + " with the same name exists in this scope");
            return;
        }

        addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber
                + " - Redeclaration of '" + info.name + "' as " + info.getKindString()
                + " (previous declaration was " + existing.getKindString()
                + " in the same scope)");
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

    public List<SymbolInfo> lookupMethodOverloads(String name) {
        SymbolTable classScope = getCurrentClassScope();
        if (classScope != null) {
            SymbolInfo base = lookupInClassHierarchy(classScope.scopeName, name);
            if (base != null && base.isCallable())
                return base.getAllVersions();
        }
        SymbolTable ifaceScope = getEnclosingScope(ScopeType.INTERFACE);
        if (ifaceScope != null) {
            SymbolInfo base = ifaceScope.lookupCurrentScopeOnly(name);
            if (base != null && base.isCallable())
                return base.getAllVersions();
        }
        SymbolInfo base = lookup(name);
        if (base != null && base.isCallable())
            return base.getAllVersions();
        return new ArrayList<>();
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

    private SymbolInfo lookupInHierarchyRec(String typeName, String memberName, Set<String> visited) {
        if (typeName == null || !visited.add(typeName))
            return null;
        SymbolTable typeScope = findTypeScope(typeName);
        if (typeScope == null)
            return null;
        SymbolInfo member = typeScope.lookupCurrentScopeOnly(memberName);
        if (member != null)
            return member;
        SymbolInfo typeInfo = getRootScope().lookupCurrentScopeOnly(typeName);
        if (typeInfo == null)
            return null;
        SymbolInfo fromParent = lookupInHierarchyRec(typeInfo.parentClass, memberName, visited);
        if (fromParent != null)
            return fromParent;
        for (String iface : typeInfo.interfaces) {
            SymbolInfo fromIface = lookupInHierarchyRec(iface, memberName, visited);
            if (fromIface != null)
                return fromIface;
        }
        return null;
    }

    public SymbolInfo lookupInClassHierarchy(String className, String memberName) {
        return lookupInHierarchyRec(className, memberName, new HashSet<>());
    }

    public List<SymbolTable> getChildScopes() {
        return Collections.unmodifiableList(childScopes);
    }

    public String getScopeName() {
        return scopeName;
    }

    public Collection<SymbolInfo> getAllSymbols() {
        return this.symbols.values();
    }

}
