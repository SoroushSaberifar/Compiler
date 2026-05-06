package CompilerProject.src.main.java.compiler;


import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java compiler.Main <input_file>");
            return;
        }
        String filePath = args[0];
        try {
            List<TokenInfo> tokens = Tokenizer.tokenize(filePath);
            for (TokenInfo tk : tokens)
                System.out.println(tk);

        } catch (Exception e) {
            System.err.println("Lexical error: " + e.getMessage());
        }
    }
}
