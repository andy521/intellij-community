// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethodObject.PsiReflectionAccessUtil;
import com.intellij.util.SmartList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ReflectionAccessMethodBuilder {
  private boolean myIsStatic = false;
  private String myReturnType = "void";
  private String myName;
  private MyMemberAccessor myMemberAccessor;
  private final List<ParameterInfo> myParameters = new SmartList<>();

  public ReflectionAccessMethodBuilder(@NotNull String name) {
    myName = name;
  }

  public PsiMethod build(@NotNull PsiElementFactory elementFactory,
                         @Nullable PsiElement context) {
    checkRequirements();
    String parameters = StreamEx.of(myParameters).map(p -> p.type + " " + p.name).joining(", ", "(", ")");
    String returnExpression = ("void".equals(myReturnType) ? "" : "return (" + myReturnType + ")") + myMemberAccessor.getAccessExpression();
    String methodBody = "    java.lang.Class<?> klass = " + myMemberAccessor.getClassLookupExpression() + ";\n" +
                        "    " + myMemberAccessor.getMemberType() + " member = null;\n" +
                        "    " + myMemberAccessor.getMemberLookupBlock() +
                        "    " + returnExpression + ";\n";
    List<String> possibleExceptions = myMemberAccessor.getPossibleExceptions();
    if (!possibleExceptions.isEmpty()) {
      methodBody = "try {\n" +
                   methodBody +
                   "}" +
                   createCatchBlocks(possibleExceptions);
    }

    String methodText =
      "public" + (myIsStatic ? " static " : " ") + myReturnType + " " + myName + parameters + " { \n" + methodBody + "}\n";

    return elementFactory.createMethodFromText(methodText, context);
  }

  private void checkRequirements() {
  }

  public ReflectionAccessMethodBuilder setName(@NotNull String name) {
    myName = name;
    return this;
  }

  public ReflectionAccessMethodBuilder accessedMethod(@NotNull String jvmClassName, @NotNull String methodName) {
    myMemberAccessor = new MyMethodAccessor(jvmClassName, methodName);
    return this;
  }

  public ReflectionAccessMethodBuilder accessedField(@NotNull String jvmClassName, @NotNull String fieldName) {
    myMemberAccessor = new MyFieldAccessor(jvmClassName, fieldName, FieldAccessType.GET);
    return this;
  }

  public ReflectionAccessMethodBuilder updatedField(@NotNull String jvmClassName, @NotNull String fieldName) {
    myMemberAccessor = new MyFieldAccessor(jvmClassName, fieldName, FieldAccessType.SET);
    return this;
  }

  public ReflectionAccessMethodBuilder accessedConstructor(@NotNull String jvmClassName) {
    myMemberAccessor = new MyConstructorAccessor(jvmClassName);
    return this;
  }

  public ReflectionAccessMethodBuilder setReturnType(@NotNull String returnType) {
    myReturnType = returnType;
    return this;
  }

  public ReflectionAccessMethodBuilder setStatic(boolean isStatic) {
    myIsStatic = isStatic;
    return this;
  }

  public ReflectionAccessMethodBuilder addParameter(@NotNull String jvmType, @NotNull String name) {
    myParameters.add(new ParameterInfo(jvmType.replace('$', '.'), name, jvmType));
    return this;
  }

  public ReflectionAccessMethodBuilder addParameters(@NotNull PsiParameterList parameterList) {
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String name = parameter.getName();
      PsiType type = parameter.getType();
      myParameters.add(new ParameterInfo(type.getCanonicalText(), name == null ? "arg" + i : name, extractJvmType(type)));
    }

    return this;
  }

  @NotNull
  private static String extractJvmType(@NotNull PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    String canonicalText = type.getCanonicalText();
    String jvmName = psiClass == null ? canonicalText : ClassUtil.getJVMClassName(psiClass);
    return jvmName == null ? canonicalText : jvmName;
  }

  private static String createCatchBlocks(@NotNull List<String> exceptions) {
    return StreamEx.of(exceptions).map(x -> "catch(" + x + " e) { throw new java.lang.RuntimeException(e); }").joining("\n");
  }

  private static class ParameterInfo {
    public final String type;
    public final String name;
    public final String jvmTypeName;

    public ParameterInfo(@NotNull String type, @NotNull String name) {
      this(type, name, type);
    }

    public ParameterInfo(@NotNull String type, @NotNull String name, @NotNull String jvmTypeName) {
      this.type = type;
      this.name = name;
      this.jvmTypeName = jvmTypeName;
    }
  }

  private interface MyMemberAccessor {
    String getMemberLookupBlock();

    String getClassLookupExpression();

    String getAccessExpression();

    String getMemberType();

    List<String> getPossibleExceptions();
  }


  private static class MyFieldAccessor implements MyMemberAccessor {
    private static final List<String> EXCEPTIONS = Collections.unmodifiableList(Arrays.asList("java.lang.NoSuchFieldException",
                                                                                              "java.lang.IllegalAccessException",
                                                                                              "java.lang.ClassNotFoundException"));
    private final String myFieldName;
    private final String myClassName;
    private final FieldAccessType myAccessType;

    public MyFieldAccessor(@NotNull String className,
                           @NotNull String fieldName,
                           @NotNull FieldAccessType accessType) {
      myFieldName = fieldName;
      myClassName = className;
      myAccessType = accessType;
    }

    @Override
    public String getClassLookupExpression() {
      String classForName = PsiReflectionAccessUtil.classForName(myClassName);
      // emulate applySideEffectAndReturnNull().staticField expression
      return "object == null ? " + classForName + " : object.getClass()";
    }

    @Override
    public String getMemberLookupBlock() {
      return "while (member == null) {\n" +
             "  try {\n" +
             "    member = klass.getDeclaredField(" + StringUtil.wrapWithDoubleQuote(myFieldName) + ");\n" +
             "  }\n" +
             "  catch(java.lang.NoSuchFieldException e) {\n" +
             "    klass = klass.getSuperclass();\n" +
             "    if (klass == null) throw e;\n" +
             "  }\n" +
             "}\n" +
             "member.setAccessible(true);";
    }

    @Override
    public String getAccessExpression() {
      return FieldAccessType.GET.equals(myAccessType) ? "member.get(object)" : "member.set(object, value)";
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Field";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return EXCEPTIONS;
    }
  }


  private class MyMethodAccessor implements MyMemberAccessor {
    private final String myClassName;
    private final String myMethodName;

    public MyMethodAccessor(@NotNull String className, @NotNull String methodName) {
      myClassName = className;
      myMethodName = methodName;
    }

    @Override
    public String getMemberLookupBlock() {
      String args = StreamEx.of(myParameters).skip(1).map(x -> PsiReflectionAccessUtil.classForName(x.jvmTypeName))
                            .prepend(StringUtil.wrapWithDoubleQuote(myMethodName))
                            .joining(", ", "(", ")");
      return "while (member == null) {\n" +
             "  try {\n" +
             "    member = klass.getDeclaredMethod" + args + ";\n" +
             "  }\n" +
             "  catch(java.lang.NoSuchMethodException e) {\n" +
             "    klass = klass.getSuperclass();\n" +
             "    if (klass == null) throw e;\n" +
             "  }\n" +
             "}\n" +
             "member.setAccessible(true);\n";
    }

    @Override
    public String getClassLookupExpression() {
      String classForName = PsiReflectionAccessUtil.classForName(myClassName);
      // emulate applySideEffectAndReturnNull().staticMethod() expression
      return "object == null ? " + classForName + " : object.getClass()";
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Method";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return Collections.unmodifiableList(Arrays.asList(
        "java.lang.NoSuchMethodException",
        "java.lang.IllegalAccessException",
        "java.lang.ClassNotFoundException",
        "java.lang.reflect.InvocationTargetException"));
    }

    @Override
    public String getAccessExpression() {
      return StreamEx.of(myParameters).map(x -> x.name).joining(", ", "member.invoke(", ")");
    }
  }


  private class MyConstructorAccessor implements MyMemberAccessor {
    private final String myClassName;

    public MyConstructorAccessor(@NotNull String className) {
      myClassName = className;
    }

    @Override
    public String getMemberLookupBlock() {
      String args = StreamEx.of(myParameters).map(x -> x.jvmTypeName).map(PsiReflectionAccessUtil::classForName).joining(", ", "(", ")");
      return "while (member == null) {\n" +
             "  try {\n" +
             "    member = klass.getDeclaredConstructor" + args + ";\n" +
             "  }\n" +
             "  catch(java.lang.NoSuchMethodException e) {\n" +
             "    klass = klass.getSuperclass();\n" +
             "    if (klass == null) throw e;\n" +
             "  }\n" +
             "}\n" +
             "member.setAccessible(true);\n";
    }

    @Override
    public String getClassLookupExpression() {
      return PsiReflectionAccessUtil.classForName(myClassName);
    }

    @Override
    public String getAccessExpression() {
      String args = StreamEx.of(myParameters).map(x -> x.name).joining(", ", "(", ")");
      return "member.newInstance" + args;
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Constructor<?>";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return Collections.unmodifiableList(Arrays.asList(
        "java.lang.NoSuchMethodException",
        "java.lang.IllegalAccessException",
        "java.lang.ClassNotFoundException",
        "java.lang.reflect.InvocationTargetException",
        "java.lang.InstantiationException"
      ));
    }
  }
}
