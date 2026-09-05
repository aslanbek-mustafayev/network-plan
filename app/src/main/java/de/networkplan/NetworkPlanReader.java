package de.networkplan;

import de.networkplan.ast.NetworkPlan;
import de.networkplan.lexer.NetworkLexer;
import de.networkplan.parser.NetworkParser;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkPlanReader {
    public NetworkPlan read(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return read(reader);
        }
    }

    /**
     * Tokenizes the input with the generated JFlex lexer and builds the AST
     * with the generated Beaver parser.
     *
     * @param reader source of the network plan text
     * @return the parsed plan; semantic validity is a separate concern, see
     *         {@code NetworkPlan.semanticErrors()}
     * @throws Exception if the input does not match the grammar
     */
    public NetworkPlan read(Reader reader) throws Exception {
        NetworkLexer lexer = new NetworkLexer(reader);
        NetworkParser parser = new NetworkParser();
        return (NetworkPlan) parser.parse(lexer);
    }
}
