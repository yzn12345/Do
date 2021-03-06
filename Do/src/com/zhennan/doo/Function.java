package com.zhennan.doo;

import java.util.List;

class Function implements Callable {
	private final Stmt.Function declaration;
	private final Environment closure;
	
	
	 Function(Stmt.Function declaration, Environment closure) {
		this.closure = closure;
		this.declaration = declaration;
	}
	@Override
	  public int arity() {
	    return declaration.params.size();
	  }
	 @Override
	  public Object call(Interpreter interpreter,
	                     List<Object> arguments) {
		 //chain the environment for function closure
	    Environment environment = new Environment(closure);
	    for (int i = 0; i < declaration.params.size(); i++) {
	    	//connect parameter variables with their real arguments, put pairs into environment before execute function body
	      environment.define(declaration.params.get(i).lexeme,
	          arguments.get(i));
	    }
	    try {
	        interpreter.executeBlock(declaration.body, environment);
	        //catch return value
	      } catch (Return returnValue) {
	        return returnValue.value;
	      }
	    return null;
	  }
	 @Override
	  public String toString() {
	    return "Function name: " + declaration.name.lexeme + "";
	  }
}