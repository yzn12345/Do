package com.zhennan.doo;

import java.util.ArrayList;
import java.util.List;

import static com.zhennan.doo.TokenType.*;

//compiling parser
class Parser {
	@SuppressWarnings("serial")
	private static class ParseError extends RuntimeException {
	}

	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}


	// parse result is a list of statement
	List<Stmt> parse() {
		List<Stmt> statements = new ArrayList<>();
		while (!isAtEnd()) {
			
			Stmt tem = declaration();
			
			statements.add(tem);
		}
	
		return statements;
	}

	private Stmt declaration() {
		try {
			if (match(CLASS)) {
				return classDeclaration();
			}
			if (match(FUN)) {
				return function("function");
			}
			if (match(VAR)) {
				return varDeclaration();
			} else {
				return statement();
			}
		} catch (ParseError error) {
			
			return null;
		}
	}

	
	//declare a class
	private Stmt classDeclaration() {
		Token name = consume(IDENTIFIER, "Expect class name.");
		consume(LEFT_BRACE, "Expect '{' before class body.");

		List<Stmt.Function> methods = new ArrayList<>();
		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			
			//difference between fun and class method is class method do not have "fun" modifier.
			methods.add(function("method"));
			//methods.add(declaration());
		}

		consume(RIGHT_BRACE, "Expect '}' after class body.");

		return new Stmt.Class(name, methods);
	}

	// define a function, no arguments but identifier (param name)
	private Stmt.Function function(String kind) {
		Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
		consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
		List<Token> parameters = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				if (parameters.size() >= 10) {
					error(peek(), "Can't have more than 10 parameters.");
				}

				parameters.add(consume(IDENTIFIER, "Expect parameter name."));
			} while (match(COMMA));
		}
		consume(RIGHT_PAREN, "Expect ')' after parameters.");
		consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
		List<Stmt> body = block();
		return new Stmt.Function(name, parameters, body);
	}

	// call with real arguments: which is expression
	private Expr finishCall(Expr callee) {
		List<Expr> arguments = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				if (arguments.size() >= 255) {
					error(peek(), "Can't have more than 255 arguments.");
				}
				arguments.add(expression());
			} while (match(COMMA));
		}

		Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

		return new Expr.Call(callee, paren, arguments);
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

	private Stmt returnStatement() {
		Token keyword = previous();
		Expr value = null;
		if (!check(SEMICOLON)) {
			value = expression();
		}

		consume(SEMICOLON, "Expect ';' after return value.");
		return new Stmt.Return(keyword, value);
	}

	private Stmt statement() {
		if (match(RETURN)) {
			return returnStatement();
		}
		if (match(PRINT)) {
			return printStatement();
		}
		if (match(IF)) {
			return ifStatement();
		}
		if (match(WHILE)) {
			return whileStatement();
		}
		if (match(LEFT_BRACE)) {
			return new Stmt.Block(block());
		}
		return expressionStatement();
	}

	private Stmt whileStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'while'.");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after condition.");
		Stmt body = statement();

		return new Stmt.While(condition, body);
	}

	private Stmt ifStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'if'.");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after if condition.");

		// match block
		Stmt thenBranch = statement();
		Stmt elseBranch = null;
		if (match(ELSE)) {
			// match block
			elseBranch = statement();
		}

		return new Stmt.If(condition, thenBranch, elseBranch);
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

		// Expr expr = equality();
		Expr expr = or();
		// here is different than binary operation. Because assignment
		// is right associative
		if (match(EQUAL)) {
			Token equals = previous();
			Expr value = assignment();

			if (expr instanceof Expr.Variable) {
				Token name = ((Expr.Variable) expr).name;
				return new Expr.Assign(name, value);
				
				//set a class method
			}else if (expr instanceof Expr.Get) {
		        Expr.Get get = (Expr.Get)expr;
		        return new Expr.Set(get.object, get.name, value);
			}
			

			error(equals, "Invalid assignment target.");
		}

		return expr;
	}

	private Expr or() {
		Expr expr = and();

		// while here meaning it's left associative
		while (match(OR)) {
			Token operator = previous();
			Expr right = and();
			expr = new Expr.Logical(expr, operator, right);
		}

		return expr;
	}

	private Expr and() {
		Expr expr = equality();
		// while here meaning it's left associative
		while (match(AND)) {
			Token operator = previous();
			Expr right = equality();
			expr = new Expr.Logical(expr, operator, right);
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

		return call();

	}

	private Expr call() {
		Expr expr = primary();

		while (true) {
			if (match(LEFT_PAREN)) {
				expr = finishCall(expr);
			} else if (match(DOT)) {
				Token name = consume(IDENTIFIER, "Expect property name after '.'.");
			
				expr = new Expr.Get(expr, name);
			} else {
				break;
			}
		}

		return expr;
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


}
