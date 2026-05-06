package CompilerProject.src.main.java.compiler;


public class TokenInfo {
    private final int line;
    private final int column;
    private final String category;
    private final String lexeme;

    public TokenInfo(int line, int column, String category, String lexeme) {
        this.line = line;
        this.column = column;
        this.category = category;
        this.lexeme = lexeme;
    }

    @Override
    public String toString() {
        return String.format("[%d:%d] <%s> %s", line, column, category, lexeme);
    }
}
