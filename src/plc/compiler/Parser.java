package plc.compiler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the tokens and returns the parsed AST.
     */
    public static Ast parse(List<Token> tokens) throws ParseException {
        return new Parser(tokens).parseSource();
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Statement> ast_list = new ArrayList<>();
        while (tokens.has(0)) {
            ast_list.add(parseStatement());
        }
        return new Ast.Source(ast_list);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, assignment, if, or while
     * statement, then it is an expression statement. See these methods for
     * clarification on what starts each type of statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        if (peek(Token.Type.IDENTIFIER)) {
            if (match("IF")) {
                return parseIfStatement();
            } else if (match("WHILE")) {
                return parseWhileStatement();
            } else if (match("LET")) {
                return parseDeclarationStatement();
            } else if (peek(Token.Type.IDENTIFIER, "=")) {
                return parseAssignmentStatement();
            }
        }
        return parseExpressionStatement();
    }

    /**
     * Parses the {@code expression-statement} rule. This method is called if
     * the next tokens do not start another statement type, as explained in the
     * javadocs of {@link #parseStatement()}.
     */
    public Ast.Statement.Expression parseExpressionStatement() throws ParseException {
        Ast.Expression expression = parseExpression();
        if (match(";")) {
            return new Ast.Statement.Expression(expression);
        }
        throw new ParseException("Must have a semicolon at the end of a statement", tokens.index);
    }

    /**
     * Parses the {@code declaration-statement} rule. This method should only be
     * called if the next tokens start a declaration statement, aka {@code let}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Declaration must declare a variable name", tokens.index);
        }

        String name = tokens.get(0).getLiteral();
        tokens.advance();
        if (!match(":")) {
            throw new ParseException("Must have colon after name", tokens.index);
        }
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Must have name after colon", tokens.index);
        }
        String type = tokens.get(-1).getLiteral();
        if (!match("=")) {
            if (match(";")) {
                return new Ast.Statement.Declaration(name, type, Optional.empty());
            }
            throw new ParseException("Must have a semicolon at the end of a statement", tokens.index);
        }
        Ast.Expression expression = parseExpression();
        if (!match(";")) {
            throw new ParseException("Must have a semicolon at the end of a statement", tokens.index);
        }
        return new Ast.Statement.Declaration(name, type, Optional.of(expression));
    }

    /**
     * Parses the {@code assignment-statement} rule. This method should only be
     * called if the next tokens start an assignment statement, aka both an
     * {@code identifier} followed by {@code =}.
     */
    public Ast.Statement.Assignment parseAssignmentStatement() throws ParseException {
        String name = tokens.get(0).getLiteral();
        tokens.advance(); tokens.advance();
        Ast.Expression expression = parseExpression();
        if (!match(";")) {
            throw new ParseException("Must have a semicolon at the end of a statement", tokens.index);
        }
        return new Ast.Statement.Assignment(name, expression);
    }

    /**
     * Parses the {@code if-statement} rule. This method should only be called
     * if the next tokens start an if statement, aka {@code if}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression expression = parseExpression();
        if (!match("THEN")) {
            throw new ParseException("Must have 'THEN' after if expression", tokens.index);
        }
        List<Ast.Statement> then_statements = new ArrayList<>();
        List<Ast.Statement> else_statements = new ArrayList<>();
        while (!match("END")) {
            if (match("ELSE")) {
                while (!match("END")) {
                    else_statements.add(parseStatement());
                }
                break;
            }
            then_statements.add(parseStatement());
        }
        return new Ast.Statement.If(expression, then_statements, else_statements);
    }

    /**
     * Parses the {@code while-statement} rule. This method should only be
     * called if the next tokens start a while statement, aka {@code while}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        Ast.Expression expression = parseExpression();
        if (!match("DO")) {
            throw new ParseException("Must have 'DO' after while expression", tokens.index);
        }
        List<Ast.Statement> statements = new ArrayList<>();
        while (!match("END")) {
            statements.add(parseStatement());
        }
        return new Ast.Statement.While(expression, statements);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseEqualityExpression();
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseEqualityExpression() throws ParseException {
        Ast.Expression first_expr = parseAdditiveExpression();
        if (!peek("!=") && !peek("==")) {
            return first_expr;
        }
        String operator = tokens.get(0).getLiteral();
        tokens.advance();
        Ast.Expression second_expr = parseAdditiveExpression();
        return new Ast.Expression.Binary(operator, first_expr, second_expr);
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression first_expr = parseMultiplicativeExpression();
        if (!peek("+") && !peek("-")) {
            return first_expr;
        }
        String operator = tokens.get(0).getLiteral();
        tokens.advance();
        Ast.Expression second_expr = parseMultiplicativeExpression();
        return new Ast.Expression.Binary(operator, first_expr, second_expr);
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression first_expr = parsePrimaryExpression();
        if (!peek("*") && !peek("/")) {
            return first_expr;
        }
        String operator = tokens.get(0).getLiteral();
        tokens.advance();
        Ast.Expression second_expr = parsePrimaryExpression();
        return new Ast.Expression.Binary(operator, first_expr, second_expr);
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        if (match(Token.Type.INTEGER)) {
            return new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.DECIMAL)) {
            return new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        }
        else if (match(Token.Type.IDENTIFIER, "(")) {
            String name = tokens.get(-2).getLiteral();
            List<Ast.Expression> args = new ArrayList<>();
            while (!match(")")) {
                args.add(parseExpression());
                if (peek(",", Token.Type.OPERATOR)) {
                    throw new ParseException("Must have an expression after comma", tokens.index);
                }
                if (match(","))
                    continue;
                if (!peek(")")) {
                    throw new ParseException("Invalid token in function arguments", tokens.index);
                }
            }
            return new Ast.Expression.Function(name, args);
        } else if (match(Token.Type.IDENTIFIER)) {
            String identifier = tokens.get(-1).getLiteral();
            if (identifier.equals("TRUE")) {
                return new Ast.Expression.Literal(true);
            } else if (identifier.equals("FALSE")) {
                return new Ast.Expression.Literal(false);
            }
            return new Ast.Expression.Variable(identifier);
        } else if (match(Token.Type.STRING)) {
            String literal = tokens.get(-1).getLiteral();
            return new Ast.Expression.Literal(literal.substring(1, literal.length() - 1));
        } else if (match("(")) {
            Ast.Expression expr = parseExpression();
            if (!match(")")) {
                throw new ParseException("Syntax error: bad statement end, must be a closing parenthesis", tokens.index);
            }
            return new Ast.Expression.Group(expr);
        } else {
            throw new ParseException("Syntax error: bad token, expected the beginning of an expression", tokens.index);
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError();
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
