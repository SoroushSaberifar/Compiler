import java.util.*;

public class SymbolTable {
    public enum ScopeType {
        GLOBAL, CLASS, METHOD, BLOCK, INTERFACE
    }

    private Map<String, SymbolInfo> symbols;
    private SymbolTable parentScope;
    private List<SymbolTable> childScopes;
    private ScopeType scopeType;
    private String scopeName;
    private int scopeId;
    private static int nextScopeId = 0;

    private List<String> semanticErrors;
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

    public void disableErrorReporting() {
        this.errorReportingEnabled = false;
    }

    public void enableErrorReporting() {
        this.errorReportingEnabled = true;
    }

    public void addSemanticError(String error) {
        if (errorReportingEnabled) {
            semanticErrors.add(error);
            System.err.println("Semantic error: " + error);

            // هدایت خطا به اسکوپ ریشه (Global) برای دسترسی و گزارش‌گیری راحت در کلاس Main
            SymbolTable root = this;
            while (root.parentScope != null) {
                root = root.parentScope;
            }
            if (root != this && !root.semanticErrors.contains(error)) {
                root.semanticErrors.add(error);
            }
        }
    }

    public List<String> getSemanticErrors() {
        return semanticErrors;
    }

    public void insert(SymbolInfo info) {
        String name = info.name;
        SymbolInfo existing = symbols.get(name);

        // اگر از قبل وجود نداشته باشد، به راحتی درج می‌شود
        if (existing == null) {
            symbols.put(name, info);
            return;
        }

        // اگر گزارش خطا غیرفعال باشد (پاس‌های پیش‌پردازش اختیاری)
        if (!errorReportingEnabled) {
            if (info.symbolType == SymbolInfo.SymbolType.METHOD ||
                    info.symbolType == SymbolInfo.SymbolType.CONSTRUCTOR) {
                String uniqueName = name + "_" + info.getParameterString();
                symbols.put(uniqueName, info);
            }
            return;
        }

        // مدیریت بازنویسی و خطای متدها و سازنده‌ها
        if (info.symbolType == SymbolInfo.SymbolType.METHOD ||
                info.symbolType == SymbolInfo.SymbolType.CONSTRUCTOR) {

            if (existing.symbolType == SymbolInfo.SymbolType.METHOD ||
                    existing.symbolType == SymbolInfo.SymbolType.CONSTRUCTOR) {

                if (existing.sameSignature(info)) {
                    addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber +
                            " - Duplicate method/constructor: " + info.name + info.getParameterString());
                    return;
                }
                existing.addOverload(info);
                return;
            } else {
                addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber +
                        " - Name conflict: " + info.name + " cannot be declared as " + info.symbolType +
                        " because a " + existing.symbolType + " with the same name exists");
                return;
            }
        }

        // خطای بازتعریف متغیر، فیلد یا پارامتر در یک اسکوپ واحد (بند اول فاز ۲)
        addSemanticError("Line " + info.lineNumber + ":" + info.columnNumber +
                " - Redeclaration of '" + name + "' as " + info.symbolType +
                " (previous declaration was " + existing.symbolType + ")");
    }

    public boolean insertIfAbsent(SymbolInfo info) {
        if (symbols.containsKey(info.name))
            return false;
        symbols.put(info.name, info);
        return true;
    }

    public void insertForce(SymbolInfo info) {
        String key = info.name;
        if (info.symbolType == SymbolInfo.SymbolType.METHOD ||
                info.symbolType == SymbolInfo.SymbolType.CONSTRUCTOR) {
            key = info.name + "_" + info.getParameterString();
        }
        symbols.put(key, info);
    }

    public SymbolInfo lookup(String name) {
        if (symbols.containsKey(name))
            return symbols.get(name);

        // اصلاح شرط برای جلوگیری از تطابق اشتباه نام‌های مشابه
        for (Map.Entry<String, SymbolInfo> entry : symbols.entrySet()) {
            if (entry.getKey().equals(name) || entry.getKey().startsWith(name + "._")
                    || entry.getKey().startsWith(name + "_")) {
                // یک بررسی دقیق‌تر: مطمئن شویم کاراکتر بعد از نام متد، جداکننده است نه ادامه
                // نام یک متد دیگر
                if (entry.getKey().startsWith(name + "_")) {
                    return entry.getValue();
                }
            }
        }

        if (parentScope != null)
            return parentScope.lookup(name);
        return null;
    }

    public SymbolInfo lookupCurrentScopeOnly(String name) {
        if (symbols.containsKey(name))
            return symbols.get(name);

        // اصلاح شرط در اسکوپ فعلی
        for (Map.Entry<String, SymbolInfo> entry : symbols.entrySet()) {
            if (entry.getKey().equals(name) || entry.getKey().startsWith(name + "_")) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean contains(String name) {
        return lookup(name) != null;
    }

    public boolean containsInCurrentScope(String name) {
        return symbols.containsKey(name);
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

    public Map<String, SymbolInfo> getAllSymbols() {
        return Collections.unmodifiableMap(symbols);
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
        SymbolTable root = this;
        while (root.parentScope != null)
            root = root.parentScope;
        return root;
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
        System.out.print(indentStr + "┌─ Scope: " + scopeType);
        if (scopeName != null && !scopeName.isEmpty())
            System.out.print(" \"" + scopeName + "\"");
        System.out.println(" [id=" + scopeId + "]");
        if (symbols.isEmpty()) {
            System.out.println(indentStr + "│  (no symbols)");
        } else {
            for (SymbolInfo sym : symbols.values()) {
                if (!sym.name.startsWith("_")) {
                    System.out.println(indentStr + "│  • " + sym.toString());
                }
            }
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
        if (!symbols.isEmpty() || !childScopes.isEmpty()) {
            System.out.println(indentStr + "[" + scopeType + (scopeName != null ? ": " + scopeName : "") + "]");
            for (SymbolInfo sym : symbols.values()) {
                if (!sym.name.startsWith("_")) {
                    String typeStr = sym.symbolType.toString().substring(0,
                            Math.min(3, sym.symbolType.toString().length()));
                    System.out.println(indentStr + "  " + typeStr + " " + sym.name
                            + (sym.dataType != null ? " : " + sym.dataType : ""));
                    if (sym.overloads != null) {
                        for (SymbolInfo ov : sym.overloads) {
                            System.out.println(indentStr + "    overload: " + ov.name + ov.getParameterString());
                        }
                    }
                }
            }
            for (SymbolTable child : childScopes) {
                child.printCompactRecursive(indent + 1);
            }
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
            if (!sym.name.startsWith("_")) {
                sb.append(indentStr).append("  - ").append(sym).append("\n");
            }
        }
        for (SymbolTable child : childScopes) {
            child.toStringRecursive(sb, indent + 1);
        }
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

    // متد جدید برای دریافت تمامی اورلودهای یک متد جهت بررسی معنایی آرگومان‌ها
    public List<SymbolInfo> lookupMethodOverloads(String name) {
        List<SymbolInfo> overloads = new ArrayList<>();
        SymbolInfo baseMethod = lookup(name); // از متد لوکاپ اصلاح شده استفاده می‌کنیم

        if (baseMethod != null && (baseMethod.symbolType == SymbolInfo.SymbolType.METHOD
                || baseMethod.symbolType == SymbolInfo.SymbolType.CONSTRUCTOR)) {
            overloads.add(baseMethod);
            if (baseMethod.overloads != null) {
                overloads.addAll(baseMethod.overloads);
            }
        }
        return overloads;
    }

    public void clear() {
        symbols.clear();
        for (SymbolTable child : childScopes)
            child.clear();
        childScopes.clear();
    }

    public boolean hasSemanticErrors() {
        return this.semanticErrors != null && !this.semanticErrors.isEmpty();
    }
}