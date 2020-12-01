package plc.compiler;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        writer.print("public final class Main {");
        newline(0);
        newline(++indent);
        writer.print("public static void main(String[] args) {");
        ++indent;
        boolean has_statements = ast.getStatements().size() > 0;
        for (Ast.Statement statement : ast.getStatements()) {
            newline(indent);
            visit(statement);
        }
        --indent;
        if (has_statements)
            newline(indent);
        writer.print("}");
        newline(--indent);
        newline(0);
        writer.print("}");
        newline(0);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        writer.print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        writer.print(ast.getType() + " " + ast.getName());
        if (ast.getValue().isPresent()) {
            writer.print(" = ");
            visit(ast.getValue().get());
        }
        writer.print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        writer.print(ast.getName() + " = ");
        visit(ast.getExpression());
        writer.print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        writer.print("if (");
        visit(ast.getCondition());
        writer.print(") {");
        if (ast.getThenStatements().size() != 0) {
            ++indent;
            for (Ast.Statement statement : ast.getThenStatements()) {
                newline(indent);
                visit(statement);
            }
            newline(--indent);
        }
        writer.print("}");
        if (!ast.getElseStatements().isEmpty()) {
            writer.print(" else {");
            ++indent;
            for (Ast.Statement statement : ast.getElseStatements()) {
                newline(indent);
                visit(statement);
            }
            newline(--indent);
            writer.print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        writer.print("while (");
        visit(ast.getCondition());
        writer.print(") {");
        ++indent;
        boolean has_statements = ast.getStatements().size() > 0;
        for (Ast.Statement statement : ast.getStatements()) {
            newline(indent);
            visit(statement);
        }
        --indent;
        if (has_statements)
            newline(indent);
        writer.print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getValue() instanceof String)
            writer.print("\"");
        writer.print(ast.getValue());
        if (ast.getValue() instanceof String)
            writer.print("\"");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        writer.print("(");
        visit(ast.getExpression());
        writer.print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        writer.print(" " + ast.getOperator() + " ");
        visit(ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Variable ast) {
        writer.print(ast.getName());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        writer.print(ast.getName() + "(");
        int i = 0;
        for (Ast.Expression expression : ast.getArguments()) {
            visit(expression);
            if (++i < ast.getArguments().size())
                writer.print(", ");
        }
        writer.print(")");
        return null;
    }

}
