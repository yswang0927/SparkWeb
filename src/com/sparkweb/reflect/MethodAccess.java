package com.sparkweb.reflect;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_VARARGS;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_1;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public abstract class MethodAccess 
{
	private Class<?> declaringClass;
	private Method[] methods;
	private String[] methodNames;
	private Class<?>[][] parameterTypes;
	private String[][] parameterNames;
	private Class<?>[] returnTypes;
	private int[] modifiers;

	public abstract Object invoke (Object object, int methodIndex, Object... args);

	/** Invokes the method with the specified name and the specified param types. */
	public Object invoke (Object object, String methodName, Class<?>[] paramTypes, Object... args) {
		return invoke(object, getIndex(methodName, paramTypes), args);
	}

	/** Invokes the first method with the specified name and the specified number of arguments. */
	public Object invoke (Object object, String methodName, Object... args) {
		return invoke(object, getIndex(methodName, args == null ? 0 : args.length), args);
	}

	/** Returns the index of the first method with the specified name. */
	public int getIndex (String methodName) {
		for (int i = 0, n = methodNames.length; i < n; i++)
			if (methodNames[i].equals(methodName)) return i;
		throw new IllegalArgumentException("Unable to find non-private method: " + methodName);
	}

	/** Returns the index of the first method with the specified name and param types. */
	public int getIndex (String methodName, Class<?>... paramTypes) {
		for (int i = 0, n = methodNames.length; i < n; i++)
			if (methodNames[i].equals(methodName) && Arrays.equals(paramTypes, parameterTypes[i])) return i;
		throw new IllegalArgumentException("Unable to find non-private method: " + methodName + " " + Arrays.toString(paramTypes));
	}

	/** Returns the index of the first method with the specified name and the specified number of arguments. */
	public int getIndex (String methodName, int paramsCount) {
		for (int i = 0, n = methodNames.length; i < n; i++)
			if (methodNames[i].equals(methodName) && parameterTypes[i].length == paramsCount) return i;
		throw new IllegalArgumentException("Unable to find non-private method: " + methodName + " with " + paramsCount + " params.");
	}
	
	public Class<?> getDeclaringClass()
	{
		return this.declaringClass;
	}

	public Method[] getMethods()
	{
		return methods;
	}
	
	public String[] getMethodNames () {
		return methodNames;
	}

	public Class<?>[][] getParameterTypes () {
		return parameterTypes;
	}
	
	public String[][] getParameterNames () {
		return parameterNames;
	}

	public Class<?>[] getReturnTypes () {
		return returnTypes;
	}
	
	public int[] getModifiers() {
		return modifiers;
	}

	public static MethodAccess get (Class<?> type) {
		ArrayList<Method> methods = new ArrayList<Method>();
		boolean isInterface = type.isInterface();
		if (!isInterface) {
			Class<?> nextClass = type;
			while (nextClass != Object.class) {
				addDeclaredMethodsToList(nextClass, methods);
				nextClass = nextClass.getSuperclass();
			}
		} else {
			recursiveAddInterfaceMethodsToList(type, methods);
		}
		
		// yswang add to get method parameter names
		final Map<String, List<String>> methodsParamNames = new HashMap<String, List<String>>(methods.size());
		try
		{
			ClassReader reader = createClassReader(type);
			reader.accept(new EmptyVisitor() {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
				{
					try
					{
						final List<String> parameterNames;
						final boolean isInit;
						
						if(name.equals("<init>")) 
						{
							isInit = true;
							parameterNames = null;
						}
						else
						{
							isInit = false;
							parameterNames = new ArrayList<String>();
							methodsParamNames.put(name, parameterNames);
						}
						
						return new MethodVisitor(Opcodes.ASM4) {
		                    // assume static method until we get a first parameter name
		                    public void visitLocalVariable(String name, String description, String signature, Label start, Label end, int index) {
		                    	if(!isInit)
		                    	{
		                    		parameterNames.add(name);
		                    	}
		                    }
		                };
		                
					} catch(Exception e) {
						// ignore
					}
					
					return null;
				}
				
			}, 0);
		
		} catch(IOException e) {
			// ignore
		}
		// -------------------------------------------------

		int n = methods.size();
		String[] methodNames = new String[n];
		Class<?>[][] parameterTypes = new Class[n][];
		String[][] parameterNames = new String[n][];
		Class<?>[] returnTypes = new Class[n];
		// yswang add support store method modifiers
		int[] methodModifiers = new int[n];
		
		for (int i = 0; i < n; i++) {
			Method method = methods.get(i);
			methodNames[i] = method.getName();
			parameterTypes[i] = method.getParameterTypes();
			
			int paramsSize = method.getParameterTypes().length;
			parameterNames[i] = methodsParamNames.get(method.getName()).subList(0, paramsSize).toArray(new String[paramsSize]);
			
			returnTypes[i] = method.getReturnType();
			methodModifiers[i] = method.getModifiers();
		}
		
		methodsParamNames.clear();
		
		String className = type.getName();
		String accessClassName = className + "MethodAccess";
		if (accessClassName.startsWith("java.")) {
			accessClassName = "reflectasm." + accessClassName;
		}
		
		Class<?> accessClass;

		AccessClassLoader loader = AccessClassLoader.get(type);
		synchronized (loader) {
			try {
				accessClass = loader.loadClass(accessClassName);
			} catch (ClassNotFoundException ignored) {
				String accessClassNameInternal = accessClassName.replace('.', '/');
				String classNameInternal = className.replace('.', '/');

				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				MethodVisitor mv;
				cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, accessClassNameInternal, null, "com/sparkweb/reflect/MethodAccess",
					null);
				{
					mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
					mv.visitCode();
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESPECIAL, "com/sparkweb/reflect/MethodAccess", "<init>", "()V");
					mv.visitInsn(RETURN);
					mv.visitMaxs(0, 0);
					mv.visitEnd();
				}
				
				{
					mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
					mv.visitCode();

					if (!methods.isEmpty()) {
						mv.visitVarInsn(ALOAD, 1);
						mv.visitTypeInsn(CHECKCAST, classNameInternal);
						mv.visitVarInsn(ASTORE, 4);
						mv.visitVarInsn(ILOAD, 2);
						
						Label[] labels = new Label[n];
						for (int i = 0; i < n; i++) {
							labels[i] = new Label();
						}
						
						Label defaultLabel = new Label();
						mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

						StringBuilder buffer = new StringBuilder(128);
						for (int i = 0; i < n; i++) {
							mv.visitLabel(labels[i]);
							if (i == 0) {
								mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {classNameInternal}, 0, null);
							} else {
								mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
							}
							mv.visitVarInsn(ALOAD, 4);

							buffer.setLength(0);
							buffer.append('(');

							String methodName = methodNames[i];
							Class<?>[] paramTypes = parameterTypes[i];
							Class<?> returnType = returnTypes[i];
							int modifier = methodModifiers[i];
							
							for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
								mv.visitVarInsn(ALOAD, 3);
								mv.visitIntInsn(BIPUSH, paramIndex);
								mv.visitInsn(AALOAD);
								Type paramType = Type.getType(paramTypes[paramIndex]);
								
								switch (paramType.getSort()) {
								case Type.BOOLEAN:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");
									break;
								case Type.BYTE:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B");
									break;
								case Type.CHAR:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C");
									break;
								case Type.SHORT:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S");
									break;
								case Type.INT:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I");
									break;
								case Type.FLOAT:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F");
									break;
								case Type.LONG:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J");
									break;
								case Type.DOUBLE:
									mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
									mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D");
									break;
								case Type.ARRAY:
									mv.visitTypeInsn(CHECKCAST, paramType.getDescriptor());
									break;
								case Type.OBJECT:
									mv.visitTypeInsn(CHECKCAST, paramType.getInternalName());
									break;
								}
								
								buffer.append(paramType.getDescriptor());
							}

							buffer.append(')');
							buffer.append(Type.getDescriptor(returnType));
							
							//mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL, classNameInternal, methodName, buffer.toString());
							// yswang add to support static method invoke
							if(Modifier.isStatic(modifier))
							{
								mv.visitMethodInsn(INVOKESTATIC, classNameInternal, methodName, buffer.toString());
							} else {
								mv.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL, classNameInternal, methodName, buffer.toString());
							}
							
							switch (Type.getType(returnType).getSort()) {
							case Type.VOID:
								mv.visitInsn(ACONST_NULL);
								break;
							case Type.BOOLEAN:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
								break;
							case Type.BYTE:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
								break;
							case Type.CHAR:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
								break;
							case Type.SHORT:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
								break;
							case Type.INT:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
								break;
							case Type.FLOAT:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
								break;
							case Type.LONG:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
								break;
							case Type.DOUBLE:
								mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
								break;
							}

							mv.visitInsn(ARETURN);
						}

						mv.visitLabel(defaultLabel);
						mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
					}
					
					mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
					mv.visitInsn(DUP);
					mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
					mv.visitInsn(DUP);
					mv.visitLdcInsn("Method not found: ");
					mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
					mv.visitVarInsn(ILOAD, 2);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
					mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
					mv.visitInsn(ATHROW);
					mv.visitMaxs(0, 0);
					mv.visitEnd();
				}
				
				cw.visitEnd();
				byte[] data = cw.toByteArray();
				accessClass = loader.defineClass(accessClassName, data);
			}
		}
		
		try {
			MethodAccess access = (MethodAccess)accessClass.newInstance();
			access.methods = methods.toArray(new Method[methods.size()]);
			access.methodNames = methodNames;
			access.parameterTypes = parameterTypes;
			access.parameterNames = parameterNames;
			access.returnTypes = returnTypes;
			access.modifiers = methodModifiers;
			access.declaringClass = type;
			
			return access;
		} catch (Throwable t) {
			throw new RuntimeException("Error constructing method access class: " + accessClassName, t);
		}
	}

	private static void addDeclaredMethodsToList (Class<?> type, ArrayList<Method> methods) {
		Method[] declaredMethods = type.getDeclaredMethods();
		for (int i = 0, n = declaredMethods.length; i < n; i++) {
			Method method = declaredMethods[i];
			int modifiers = method.getModifiers();
			
			// yswang comment it to support static method
			//if (Modifier.isStatic(modifiers)) continue;
			if (Modifier.isPrivate(modifiers)) continue;
			
			methods.add(method);
		}
	}

	private static void recursiveAddInterfaceMethodsToList (Class<?> interfaceType, ArrayList<Method> methods) {
		addDeclaredMethodsToList(interfaceType, methods);
		for (Class<?> nextInterface : interfaceType.getInterfaces()) {
			recursiveAddInterfaceMethodsToList(nextInterface, methods);
		}
	}
	
	private static ClassReader createClassReader(Class<?> declaringClass) throws IOException {
        InputStream in = null;
        try {
            ClassLoader classLoader = declaringClass.getClassLoader();
            in = classLoader.getResourceAsStream(declaringClass.getName().replace('.', '/') + ".class");
            ClassReader reader = new ClassReader(in);
            return reader;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
