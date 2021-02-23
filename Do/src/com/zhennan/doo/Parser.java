package com.zhennan.doo;

import java.util.ArrayList;
import java.util.List;

import static com.zhennan.doo.TokenType.*;

class Parser {
	private static class ParseError extends RuntimeException {
	}

	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

//	Expr parse() {
//		try {
//			return expression();
//		} catch (ParseError error) {
//			return null;
//		}
//	}
	List<Stmt> parse() {
		List<Stmt> statements = new ArrayList<>();
		while (!isAtEnd()) {
			statements.add(declaration());
		}

		return statements;
	}

	private Stmt declaration() {
		try {
			if (match(VAR)) {
				return varDeclaration();
			} else {
				return statement();
			}
		} catch (ParseError error) {
			// Do.error(peek(), "Your code has syntax error while compiling");
			synchronize();
			return null;
		}
	}

	// declare a variable
	// var could be null if no = found
	private Stmt varDeclaration() {
		Token name = consume(IDENTIFIER, "Expect variable name.");

		Expr initializer = null;
		if (match(EQUAL)) {
			initializer = expression();
		}

		consume(SEMICOLON, "Expect ';' after variable declaration.");
		return new Stmt.Var(name, initializer);
	}

	private Stmt statement() {
		if (match(PRINT))
			return printStatement();
		if (match(LEFT_BRACE))
			return new Stmt.Block(block());
		return expressionStatement();
	}

	private List<Stmt> block() {
		List<Stmt> statements = new ArrayList<>();

		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			statements.add(declaration());
		}

		consume(RIGHT_BRACE, "Expect '}' after block.");
		return statements;
	}

	private Stmt expressionStatement() {
		Expr expr = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Stmt.Expression(expr);
	}

	private Stmt printStatement() {
		Expr value = expression();
		consume(SEMICOLON, "Expect ';' after value.");
		return new Stmt.Print(value);
	}

	private Expr expression() {
		return assignment();
	}

	private Expr assignment() {
		Expr expr = equality();

		// here is different than binary operation. Because assignment
		// is right associative
		if (match(EQUAL)) {
			Token equals = previous();
			Expr value = assignment();

			if (expr instanceof Expr.Variable) {
				Token name = ((Expr.Variable) expr).name;
				return new Expr.Assign(name, value);
			}

			error(equals, "Invalid assignment target.");
		}

		return expr;
	}
	// equality �� comparison ( ( "!=" | "==" ) comparison )*
	// Ex: in javascript, 3 != 3 != 3 == 3 is valid expression

	// Parsing a == b == c == d == e.
	// For each iteration, we create a new binary expression using the previous one
	// as the left operand.
	private Expr equality() {
		// System.out.println("equality");
		Expr expr = comparison();

		// while is left associative (3+3) + 3
		while (match(NOT_EQUAL, EQUAL_EQUAL)) {
			Token operator = previous();
			Expr right = comparison();
			expr = new Expr.Binary(expr, operator, right);
			// System.out.println("inside");
		}
		// System.out.println("return");
		return expr;

	}
	// comparison �� term ( ( ">" | ">=" | "<" | "<=" ) term )* ;

	private Expr comparison() {
		// System.out.println("compare");
		Expr expr = term();

		while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			Token operator = previous();
			Expr right = term();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}
	// expression �� equality ;
	// equality �� comparison ( ( "!=" | "==" ) comparison )* ;
	// comparison �� term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
	// term �� factor ( ( "-" | "+" ) factor )* ;
	// factor �� unary ( ( "/" | "*" ) unary )* ;
	// unary �� ( "!" | "-" ) unary
	// | primary ;
	// primary �� NUMBER | STRING | "true" | "false" | "nil"
	// | "(" expression ")" ;

	// term �� factor ( ( "-" | "+" ) factor )* ;
	private Expr term() {
		// System.out.println("term");
		Expr expr = factor();

		while (match(MINUS, PLUS)) {
			Token operator = previous();
			Expr right = factor();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	// factor �� unary ( ( "/" | "*" ) unary )* ;
	private Expr factor() {
		// System.out.println("factor");
		Expr expr = unary();

		while (match(SLASH, STAR)) {
			Token operator = previous();
			Expr right = unary();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	// unary �� ( "!" | "-" ) unary
	// | primary ;
	private Expr unary() {
		// System.out.println("unary");
		if (match(NOT, MINUS)) {
			Token operator = previous();
			Expr right = unary();
			return new Expr.Unary(operator, right);
		}

		return primary();
	}

	// primary �� NUMBER | STRING | "true" | "false" | "nil"
	// | "(" expression ")" ;
	private Expr primary() {
		// System.out.println("primary");
		if (match(FALSE))
			return new Expr.Literal(false);
		if (match(TRUE))
			return new Expr.Literal(true);
		if (match(NIL))
			return new Expr.Literal(null);
		if (match(IDENTIFIER)) {
			return new Expr.Variable(previous());
		}
		if (match(NUMBER, STRING)) {
			return new Expr.Literal(previous().literal);
		}

		if (match(LEFT_PAREN)) {
			Expr expr = expression();
			consume(RIGHT_PAREN, "Expect ')' after expression.");
			return new Expr.Grouping(expr);
		}
		throw error(peek(), "Expect expression. Your input syntax is incorrect");
	}

	// consume current type, if type incorrect, report error
	// then entering panic mode
	private Token consume(TokenType type, String message) {
		if (check(type))
			return advance();

		throw error(peek(), message);
	}

	private ParseError error(Token token, String message) {
		Do.error(token, message);
		return new ParseError();
	}

	// move forward and return current
	private Token advance() {
		if (!isAtEnd())
			current++;
		return previous();
	}

	// if any type is found, return true, consume current and move forward
	private boolean match(TokenType... types) {
		for (TokenType type : types) {
			if (check(type)) {
				advance();
				return true;
			}
		}

		return false;
	}

	// check current type without move forward
	private boolean check(TokenType type) {
		if (isAtEnd())
			return false;
		return peek().type == type;
	}

	private boolean isAtEnd() {
		return peek().type == EOF;
	}

	private Token peek() {
		return tokens.get(current);
	}

	private Token previous() {
		return tokens.get(current - 1);
	}

	int getCurrent() {
		return this.current;
	}

	// make the program go back to normal mode from panic mode
	private void synchronize() {
		advance();

		while (!isAtEnd()) {
			switch (peek().type) {
			case SEMICOLON:
//				System.out.println("semi in synchro");
				advance();
				return;
			case CLASS:
			case FUN:
			case VAR:
			case FOR:
			case IF:
			case WHILE:
			case PRINT:
			case RETURN:
				return;
			}

			advance();
		}
	}
}
