package cc.testbase;

import java.io.File;

public class DummySet {

	public static void setParent(TemplateParent tp) {
		tp.pVar = 5;
	}
	
	public void stupidMethod() {
		try {
			String nullString = null;
			String.class.getName();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		DummySet dSet = new DummySet();
		dSet.stupidMethod();
	}

}