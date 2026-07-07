import java.util.*;

public class SymbolInfo {

    public String name;
    public String dataType;
    public String parentClass;
    public String initialValue;
    public String scope;
    public SymbolType symbolType;
    public AccessModifier accessModifier = AccessModifier.DEFAULT;
    public List<String> interfaces = new ArrayList<>();
    public List<ParameterInfo> parameters = new ArrayList<>();
    public List<SymbolInfo> overloads;
    public int lineNumber = -1;
    public int columnNumber = -1;
    public int arraySize = -1;
    public boolean isAbstract = false;
    public boolean isStatic = false;
    public boolean isOverride = false;
    public boolean isInitialized = false;

    public enum SymbolType {
        CLASS, INTERFACE, METHOD, CONSTRUCTOR, FIELD, VARIABLE, PARAMETER, IMPORT
    }

    public enum AccessModifier {
        DEFAULT, PUBLIC, PRIVATE, PROTECTED, INTERNAL
    }

    public static String normalizeType(String t) {
        return (t == null) ? null : t.replaceAll("\\s+", "");
    }

    public static class ParameterInfo {
        public String name;
        public String type;

        public ParameterInfo(String name, String type) {
            this.name = name;
            this.type = normalizeType(type);
        }

        @Override
        public String toString() {
            return type + " " + name;
        }
    }

    public SymbolInfo(String name, SymbolType symbolType) {
        this.name = name;
        this.symbolType = symbolType;
    }

    public SymbolInfo(String name, SymbolType symbolType, String dataType) {
        this(name, symbolType);
        this.dataType = normalizeType(dataType);
    }

    public SymbolInfo(String name, SymbolType symbolType, String dataType, int line, int col) {
        this(name, symbolType, dataType);
        this.lineNumber = line;
        this.columnNumber = col;
    }

    public void setLocation(int line, int column) {
        this.lineNumber = line;
        this.columnNumber = column;
    }

    public boolean isArrayType() {
        return dataType != null && dataType.endsWith("[]");
    }

    public boolean isCallable() {
        return symbolType == SymbolType.METHOD || symbolType == SymbolType.CONSTRUCTOR;
    }

    public String getParameterString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(parameters.get(i).type).append(" ").append(parameters.get(i).name);
        }
        return sb.append(")").toString();
    }

    public boolean sameSignature(SymbolInfo other) {
        if (other == null)
            return false;
        if (parameters.size() != other.parameters.size())
            return false;
        for (int i = 0; i < parameters.size(); i++) {
            String a = normalizeType(parameters.get(i).type);
            String b = normalizeType(other.parameters.get(i).type);
            if (!Objects.equals(a, b))
                return false;
        }
        return true;
    }

    public boolean addOverload(SymbolInfo info) {
        if (this.sameSignature(info))
            return false;
        if (overloads == null)
            overloads = new ArrayList<>();
        for (SymbolInfo ov : overloads) {
            if (ov.sameSignature(info))
                return false;
        }
        overloads.add(info);
        return true;
    }

    public List<SymbolInfo> getAllVersions() {
        List<SymbolInfo> all = new ArrayList<>();
        all.add(this);
        if (overloads != null)
            all.addAll(overloads);
        return all;
    }

    public String getKindString() {
        switch (symbolType) {
            case CLASS:
                return "class";
            case INTERFACE:
                return "interface";
            case METHOD:
                return "method";
            case CONSTRUCTOR:
                return "constructor";
            case FIELD:
                return "field";
            case VARIABLE:
                return "variable";
            case PARAMETER:
                return "parameter";
            default:
                return symbolType.toString().toLowerCase();
        }
    }

}
