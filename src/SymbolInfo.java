import java.util.*;

public class SymbolInfo {

    public enum SymbolType {
        CLASS, INTERFACE, METHOD, CONSTRUCTOR, FIELD, VARIABLE, PARAMETER, IMPORT
    }

    public enum AccessModifier {
        DEFAULT, PUBLIC, PRIVATE, PROTECTED, INTERNAL
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

    public String name;
    public SymbolType symbolType;
    public String dataType;
    public String parentClass;
    public List<String> interfaces = new ArrayList<>();
    public boolean isAbstract = false;
    public boolean isStatic = false;
    public boolean isOverride = false;
    public AccessModifier accessModifier = AccessModifier.DEFAULT;
    public String initialValue;
    public String scope;
    public boolean isInitialized = false;
    public int lineNumber = -1;
    public int columnNumber = -1;
    public List<ParameterInfo> parameters = new ArrayList<>();
    public List<SymbolInfo> overloads;
    public int arraySize = -1;
    public boolean isInternalSymbol = false;

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

    public static String normalizeType(String t) {
        return (t == null) ? null : t.replaceAll("\\s+", "");
    }

    public void setLocation(int line, int column) {
        this.lineNumber = line;
        this.columnNumber = column;
    }

    public void setDataType(String t) {
        this.dataType = normalizeType(t);
    }

    public boolean isArrayType() {
        String t = normalizeType(dataType);
        return t != null && t.endsWith("[]");
    }

    public String getElementType() {
        if (!isArrayType())
            return null;
        String t = normalizeType(dataType);
        return t.substring(0, t.length() - 2);
    }

    public boolean isCallable() {
        return symbolType == SymbolType.METHOD || symbolType == SymbolType.CONSTRUCTOR;
    }

    public void addParameter(String pName, String pType) {
        parameters.add(new ParameterInfo(pName, pType));
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

    public String getSignature() {
        StringBuilder sb = new StringBuilder(name).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(normalizeType(parameters.get(i).type));
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

    private String accessString() {
        return "[" + accessModifier.toString().toLowerCase() + "]";
    }

    @Override
    public String toString() {
        return toString("");
    }

    public String toString(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getKindString()).append(": ").append(name);

        if (isCallable()) {
            sb.append(getParameterString());
        }

        if (dataType != null && symbolType != SymbolType.CLASS
                && symbolType != SymbolType.INTERFACE
                && symbolType != SymbolType.CONSTRUCTOR) {
            sb.append(" : ").append(dataType);
        }

        if (symbolType == SymbolType.CLASS) {
            if (isAbstract)
                sb.append(" [abstract]");
            if (parentClass != null)
                sb.append(" extends ").append(parentClass);
            if (!interfaces.isEmpty())
                sb.append(" implements ").append(String.join(", ", interfaces));
        }

        if (symbolType == SymbolType.METHOD || symbolType == SymbolType.FIELD) {
            sb.append(" ").append(accessString());
        } else if (accessModifier != AccessModifier.DEFAULT) {
            sb.append(" ").append(accessString());
        }

        if (symbolType != SymbolType.CLASS && isAbstract)
            sb.append(" [abstract]");
        if (isStatic)
            sb.append(" [static]");
        if (isOverride)
            sb.append(" [override]");

        if (initialValue != null)
            sb.append(" = ").append(initialValue);
        if (isArrayType() && arraySize >= 0)
            sb.append(" [size=").append(arraySize).append("]");
        if (scope != null)
            sb.append(" (scope: ").append(scope).append(")");
        if (lineNumber >= 0)
            sb.append(" @").append(lineNumber).append(":").append(columnNumber);

        if (overloads != null) {
            for (SymbolInfo ov : overloads) {
                sb.append("\n").append(indent).append("   └─ overload: ")
                        .append(ov.name).append(ov.getParameterString());
                if (ov.dataType != null)
                    sb.append(" : ").append(ov.dataType);
            }
        }
        return sb.toString();
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Identifier Kind : ").append(getKindString()).append("\n");
        sb.append("Name            : ").append(name).append("\n");
        if (dataType != null)
            sb.append(symbolType == SymbolType.METHOD ? "Return Type     : " : "Data Type       : ")
                    .append(dataType).append("\n");
        if (symbolType == SymbolType.CLASS) {
            sb.append("Parent Class    : ").append(parentClass != null ? parentClass : "-").append("\n");
            sb.append("Implements      : ")
                    .append(interfaces.isEmpty() ? "-" : String.join(", ", interfaces)).append("\n");
            sb.append("Abstract        : ").append(isAbstract).append("\n");
        }
        if (isCallable()) {
            sb.append("Parameters      : ").append(getParameterString()).append("\n");
            if (symbolType == SymbolType.METHOD) {
                sb.append("Access Level    : ").append(accessModifier.toString().toLowerCase()).append("\n");
                sb.append("Override        : ").append(isOverride).append("\n");
                sb.append("Abstract        : ").append(isAbstract).append("\n");
            }
        }
        if (symbolType == SymbolType.FIELD) {
            sb.append("Access Level    : ").append(accessModifier.toString().toLowerCase()).append("\n");
        }
        if (symbolType == SymbolType.FIELD || symbolType == SymbolType.VARIABLE) {
            sb.append("Initial Value   : ").append(initialValue != null ? initialValue : "-").append("\n");
        }
        if (scope != null)
            sb.append("Defined In      : ").append(scope).append("\n");
        if (lineNumber >= 0)
            sb.append("Position        : line ").append(lineNumber)
                    .append(", col ").append(columnNumber).append("\n");
        return sb.toString();
    }
}
