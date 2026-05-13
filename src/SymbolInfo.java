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
    public boolean isAbstractMethod;


    public String scopeLevel;  
    public String initialValue;

    public SymbolInfo(String name, SymbolType type) {
        this.name = name;
        this.symbolType = type;
        this.parameters = new ArrayList<>();
        this.implementedInterfaces = new ArrayList<>();
        this.accessModifier = AccessModifier.DEFAULT;
        this.isAbstract = false;
        this.isOverride = false;
        this.isAbstractMethod = false;
        this.scopeLevel = "unknown";
        this.initialValue = null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(name).append(", Type: ").append(symbolType);
        if (dataType != null) sb.append(", DataType: ").append(dataType);
        if (accessModifier != null && accessModifier != AccessModifier.DEFAULT) 
            sb.append(", Access: ").append(accessModifier);
        if (parentClass != null) sb.append(", Parent: ").append(parentClass);
        if (!implementedInterfaces.isEmpty()) sb.append(", Implements: ").append(implementedInterfaces);
        if (isAbstract) sb.append(", Abstract: yes");
        if (!parameters.isEmpty()) sb.append(", Params: ").append(parameters);
        if (scopeLevel != null && !scopeLevel.equals("unknown")) sb.append(", ScopeLevel: ").append(scopeLevel);
        if (initialValue != null) sb.append(", Init: ").append(initialValue);
        return sb.toString();
    }
}