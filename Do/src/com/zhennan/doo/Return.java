package com.zhennan.doo;

class Return extends RuntimeException {
	final Object value;

	Return(Object value) {
		//disables some JVM machinery for runtime exception
		super(null, null, false, false);
		this.value = value;
	}
}