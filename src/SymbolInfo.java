import java.util.*;

public class SymbolInfo {
    public enum SymbolType {
        CLASS, INTERFACE, METHOD, CONSTRUCTOR, VARIABLE, FIELD, PARAMETER
    }

    public enum AccessModifier {
        PUBLIC, PRIVATE, PROTECTED, INTERNAL, DEFAULT
    }

    public static class ParameterInfo {
        public String name;
        public String type;

        public ParameterInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return name + ":" + type;
        }
    }

    public String name;
    public SymbolType symbolType;
    public String dataType;
    public AccessModifier accessModifier;

    public String parentClass;
    public List<String> implementedInterfaces;
    public boolean isAbstract;

    public List<ParameterInfo> parameters;
    public boolean isOverride;
    public boolean isStatic;
    public boolean isFinal;

    public String scopeLevel;
    public String initialValue;

    public int lineNumber;
    public int columnNumber;

    public List<SymbolInfo> overloads;

    public SymbolInfo(String name, SymbolType type) {
        this.name = name;
        this.symbolType = type;
        this.parameters = new ArrayList<>();
        this.implementedInterfaces = new ArrayList<>();
        this.accessModifier = AccessModifier.DEFAULT;
        this.isAbstract = false;
        this.isOverride = false;
        this.isStatic = false;
        this.isFinal = false;
        this.scopeLevel = "unknown";
        this.initialValue = null;
        this.parentClass = null;
        this.lineNumber = -1;
        this.columnNumber = -1;
        this.overloads = null;
    }

    public boolean sameSignature(SymbolInfo other) {
        if (this.symbolType != other.symbolType)
            return false;
        if (this.parameters.size() != other.parameters.size())
            return false;
        for (int i = 0; i < parameters.size(); i++) {
            if (!this.parameters.get(i).type.equals(other.parameters.get(i).type))
                return false;
        }
        return true;
    }

    public void addOverload(SymbolInfo overload) {
        if (overloads == null) {
            overloads = new ArrayList<>();
        }
        overloads.add(overload);
    }

    public void setLocation(int line, int column) {
        this.lineNumber = line;
        this.columnNumber = column;
    }

    public boolean isTypeDeclaration() {
        return symbolType == SymbolType.CLASS || symbolType == SymbolType.INTERFACE;
    }

    public boolean isCallable() {
        return symbolType == SymbolType.METHOD || symbolType == SymbolType.CONSTRUCTOR;
    }

    public boolean isDataMember() {
        return symbolType == SymbolType.FIELD || symbolType == SymbolType.VARIABLE
                || symbolType == SymbolType.PARAMETER;
    }

    public String getParameterString() {
        if (parameters.isEmpty())
            return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(parameters.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(symbolType).append(": ").append(name);
        if (dataType != null && !dataType.isEmpty() && symbolType != SymbolType.CLASS
                && symbolType != SymbolType.INTERFACE) {
            sb.append(" : ").append(dataType);
        }
        if (accessModifier != AccessModifier.DEFAULT) {
            sb.append(" [").append(accessModifier.toString().toLowerCase()).append("]");
        }
        if (isStatic && (symbolType == SymbolType.METHOD || symbolType == SymbolType.FIELD)) {
            sb.append(" static");
        }
        if (isFinal && symbolType == SymbolType.FIELD) {
            sb.append(" final");
        }
        if (isAbstract && (symbolType == SymbolType.CLASS || symbolType == SymbolType.METHOD)) {
            sb.append(" (abstract)");
        }
        if (isOverride && symbolType == SymbolType.METHOD) {
            sb.append(" @Override");
        }
        if (symbolType == SymbolType.CLASS) {
            if (parentClass != null && !parentClass.isEmpty()) {
                sb.append(" extends ").append(parentClass);
            }
            if (implementedInterfaces != null && !implementedInterfaces.isEmpty()) {
                sb.append(" implements ").append(String.join(", ", implementedInterfaces));
            }
        }
        if (isCallable() && !parameters.isEmpty()) {
            sb.append(getParameterString());
        }
        if (initialValue != null && !initialValue.isEmpty() && isDataMember()) {
            sb.append(" = ").append(initialValue);
        }
        if (scopeLevel != null && scopeLevel.equals("class") && symbolType == SymbolType.FIELD) {
            sb.append(" [class field]");
        }
        if (lineNumber != -1) {
            sb.append(" (line ").append(lineNumber).append(")");
        }

        if (overloads != null && !overloads.isEmpty()) {
            for (SymbolInfo ov : overloads) {
                sb.append("\n          └─ overload: ").append(ov.toStringWithoutOverloads());
            }
        }
        return sb.toString();
    }

    private String toStringWithoutOverloads() {
        StringBuilder sb = new StringBuilder();
        sb.append(symbolType).append(": ").append(name);
        if (dataType != null && !dataType.isEmpty() && symbolType != SymbolType.CLASS
                && symbolType != SymbolType.INTERFACE) {
            sb.append(" : ").append(dataType);
        }
        if (accessModifier != AccessModifier.DEFAULT) {
            sb.append(" [").append(accessModifier.toString().toLowerCase()).append("]");
        }
        if (isStatic && (symbolType == SymbolType.METHOD || symbolType == SymbolType.FIELD)) {
            sb.append(" static");
        }
        if (isAbstract && (symbolType == SymbolType.CLASS || symbolType == SymbolType.METHOD)) {
            sb.append(" (abstract)");
        }
        if (isCallable() && !parameters.isEmpty()) {
            sb.append(getParameterString());
        }
        return sb.toString();
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Symbol: ").append(name).append(" ===\n");
        sb.append("  Type: ").append(symbolType).append("\n");
        if (dataType != null)
            sb.append("  Data Type: ").append(dataType).append("\n");
        if (accessModifier != AccessModifier.DEFAULT)
            sb.append("  Access: ").append(accessModifier).append("\n");
        if (parentClass != null)
            sb.append("  Parent Class: ").append(parentClass).append("\n");
        if (!implementedInterfaces.isEmpty())
            sb.append("  Implements: ").append(implementedInterfaces).append("\n");
        if (isAbstract)
            sb.append("  Abstract: yes\n");
        if (isOverride)
            sb.append("  Override: yes\n");
        if (isStatic)
            sb.append("  Static: yes\n");
        if (isFinal)
            sb.append("  Final: yes\n");
        if (!parameters.isEmpty()) {
            sb.append("  Parameters: ");
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(parameters.get(i));
            }
            sb.append("\n");
        }
        if (initialValue != null)
            sb.append("  Initial Value: ").append(initialValue).append("\n");
        if (scopeLevel != null)
            sb.append("  Scope Level: ").append(scopeLevel).append("\n");
        if (lineNumber != -1)
            sb.append("  Location: line ").append(lineNumber).append(", column ").append(columnNumber).append("\n");
        return sb.toString();
    }
}