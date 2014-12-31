package edu.columbia.psl.cc.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.objectweb.asm.Type;

import edu.columbia.psl.cc.datastruct.BytecodeCategory;
import edu.columbia.psl.cc.pojo.ClassMethodInfo;
import edu.columbia.psl.cc.pojo.InstNode;

public class ClassInfoCollector {
		
	private static HashMap<String, ClassMethodInfo> classMethodInfoMap = new HashMap<String, ClassMethodInfo>();
	
	private static HashMap<String, Class> methodToClass = new HashMap<String, Class>();
	
	public static void initiateClassMethodInfo(String className, String methodName, String methodDesc, boolean isStatic) {
		String classMethodCacheKey = StringUtil.genClassCacheKey(className, methodName, methodDesc);
		
		if (classMethodInfoMap.containsKey(classMethodCacheKey)) 
			return ;
		
		ClassMethodInfo cmi = new ClassMethodInfo();
		
		//Set up arg len and arg size
		Type methodType = Type.getMethodType(methodDesc);
		Type[] args = methodType.getArgumentTypes();
		Type returnType = methodType.getReturnType();
		
		int argSize = 0;
		for (int i = 0; i < args.length; i++) {
			if (args[i].getSort() == Type.DOUBLE || args[i].getSort() == Type.LONG) {
				argSize += 2;
			} else {
				argSize++;
			}
		}
		
		int endIdx = -1;
		if (args.length > 0) {
			int startIdx = 0;
			if (!isStatic) {
				startIdx = 1;
			}
							
			endIdx = startIdx;
			for (int i = args.length - 1; i >= 0; i--) {
				Type t = args[i];
				if (t.getDescriptor().equals("D") || t.getDescriptor().equals("J")) {
					endIdx += 2;
				} else {
					endIdx += 1;
				}
			}
			endIdx--;
		} else {
			endIdx = 0;
		}
		
		cmi.args = args;
		cmi.returnType = returnType;
		cmi.argSize = argSize;
		cmi.endIdx = endIdx;
		classMethodInfoMap.put(classMethodCacheKey, cmi);
	}
	
	public static ClassMethodInfo retrieveClassMethodInfo(String className, String methodName, String methodDesc, int opcode) {
		String classMethodCacheKey = StringUtil.genClassCacheKey(className, methodName, methodDesc);
		
		if (classMethodInfoMap.containsKey(classMethodCacheKey)) {
			return classMethodInfoMap.get(classMethodCacheKey);
		} else {
			boolean isStatic = false;
			if (BytecodeCategory.staticMethod().contains(opcode)) {
				isStatic = true;
			}
			initiateClassMethodInfo(className, methodName, methodDesc, isStatic);
			return classMethodInfoMap.get(classMethodCacheKey);
		}
	}
	
	public static Class<?> retrieveCorrectClassByConstructor(String className, String constName, String constDescriptor) {
		try {
			className = className.replace("/", ".");
			Class<?> calledClass = Class.forName(className);
			if (calledClass != null) {
				for (Constructor<?> c: calledClass.getDeclaredConstructors()) {
					if (c.getName().equals(constName) 
							&& constDescriptor.equals(Type.getConstructorDescriptor(c))) {
						return calledClass;
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static Class<?> retrieveCorrectClassByMethod(String className, String methodName, String methodDescriptor, boolean direct) {
		String classMethodCacheKey = StringUtil.genClassCacheKey(className, methodName, methodDescriptor);
		if (methodToClass.containsKey(classMethodCacheKey)) {
			return methodToClass.get(classMethodCacheKey);
		}
		
		try { 
			className = className.replace("/", ".");
			Class<?> calledClass = Class.forName(className);
			
			if (direct) {
				//direct is for <init> and private method of INVOKESPECIAL
				methodToClass.put(classMethodCacheKey, calledClass);
				return calledClass;
			}
			
			Type targetMethodType = Type.getMethodType(methodDescriptor);
			Type[] targetArgs = targetMethodType.getArgumentTypes();
			Type targetReturn = targetMethodType.getReturnType();
			while (calledClass != null) {
				for (Method m: calledClass.getDeclaredMethods()) {
					/*if (m.isBridge() || m.isSynthetic())
						continue ;*/
					if (m.getName().equals(methodName)) {
						Type[] mArgs = Type.getArgumentTypes(m);
						Type mReturn = Type.getReturnType(m);
						
						/*if (!targetReturn.equals(mReturn))
							continue ;*/
						
						if (mArgs.length != targetArgs.length)
							continue ;
						
						int count = 0;
						for (int i =0; i < targetArgs.length; i++) {
							if (!targetArgs[i].equals(mArgs[i]))
								continue ;
							count++;
						}
						
						if (count == targetArgs.length) {
							methodToClass.put(classMethodCacheKey, calledClass);
							return calledClass;
						}
					}
				}
				calledClass = calledClass.getSuperclass();
			}
			
			//Something wrong if reaching here
			return calledClass;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	/**
	 * @param className
	 * @param name
	 * @return
	 */
	public static Class<?> retrieveCorrectClassByField(String className, String name) {
		try {
			className = className.replace("/", ".");
			Class<?> calledClass = Class.forName(className);
			while (calledClass != null) {
				for (Field f: calledClass.getDeclaredFields()) {
					//Name should be enough to find the correct field?
					if (f.getName().equals(name)) {
						return calledClass;
					}
				}
				calledClass = calledClass.getSuperclass();
			}
			return calledClass;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	public static void main(String[] args) throws ClassNotFoundException {
		Class<?> testClass = retrieveCorrectClassByMethod("org/apache/xerces/parsers/AbstractSAXParser", 
				"parse", 
				"(Lorg/apache/xerces/xni/parser/XMLInputSource;)V", false);
		System.out.println(testClass);
		//Type targetMethodType = Type.getMethodType("(Lorg/apache/xerces/xniparser/XMLInputSource;)V");
		//System.out.println(targetMethodType.getReturnType());
		//System.out.println(targetMethodType.getArgumentTypes().length);
		
		/*Class<?> theClazz = Class.forName("org.apache.xerces.parsers.AbstractSAXParser");
		for (Method m: theClazz.getDeclaredMethods()) {
			System.out.println(m.getName());
			System.out.println("Args: ");
			for (Class aClazz: m.getParameterTypes()) {
				System.out.println(aClazz);
			}
			System.out.println("Return type: " + m.getReturnType());
		}*/
		
	}

}
