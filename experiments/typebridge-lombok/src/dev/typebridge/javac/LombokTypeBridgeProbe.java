package dev.typebridge.javac;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ApiGeneratorManager integration engine for the TypeBridge strong-type model.
 *
 * Unlike the first probe, this implementation contains no CustomerId/customerId
 * special case. It discovers @StrongType declarations during parsing, waits for
 * annotation processing (including Lombok and record completion), resolves the
 * actual constructor relation from javac symbols, discovers generated methods,
 * and inserts a unique raw -> strong wrapper only inside @AdaptationScope.
 *
 * Strong -> raw is intentionally never implicit.
 */
public final class LombokTypeBridgeProbe implements Plugin {
    @Override
    public String getName() {
        return "TypeBridgeLombokProbe";
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context context = ((BasicJavacTask) task).getContext();
        Engine engine = new Engine(
                TreeMaker.instance(context),
                Names.instance(context),
                JavacElements.instance(context)
        );

        task.addTaskListener(new TaskListener() {
            private boolean rewritten;

            @Override public void started(TaskEvent e) {}

            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.PARSE
                        && e.getCompilationUnit() instanceof JCTree.JCCompilationUnit unit) {
                    engine.index(unit);
                    return;
                }
                if (e.getKind() == TaskEvent.Kind.ANNOTATION_PROCESSING && !rewritten) {
                    rewritten = true;
                    engine.discoverGeneratedMembers();
                    engine.rewrite();
                }
            }
        });
    }

    private record Relation(String raw, String strong) {}
    private record MethodSig(String owner, String name, java.util.List<String> params, String returns) {}

    private static final class UnitInfo {
        final String pkg;
        final Map<String, String> imports = new HashMap<>();
        final Map<String, String> declared = new HashMap<>();

        UnitInfo(JCTree.JCCompilationUnit unit) {
            pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            for (JCTree def : unit.defs) {
                if (def instanceof JCTree.JCImport imp && !imp.staticImport) {
                    String q = imp.qualid.toString();
                    if (!q.endsWith(".*")) imports.put(q.substring(q.lastIndexOf('.') + 1), q);
                }
            }
            unit.accept(new com.sun.tools.javac.tree.TreeScanner() {
                String owner = pkg;
                @Override public void visitClassDef(JCTree.JCClassDecl tree) {
                    String q = owner.isBlank() ? tree.name.toString() : owner + "." + tree.name;
                    declared.putIfAbsent(tree.name.toString(), q);
                    String previous = owner;
                    owner = q;
                    super.visitClassDef(tree);
                    owner = previous;
                }
            });
        }

        String resolve(String raw) {
            if (raw == null) return null;
            raw = raw.trim();
            if (raw.isEmpty()) return raw;
            if (raw.endsWith("[]")) return resolve(raw.substring(0, raw.length() - 2)) + "[]";
            if (isPrimitive(raw)) return raw;
            if (raw.contains("<")) return raw;
            int dot = raw.indexOf('.');
            if (dot > 0) {
                String first = raw.substring(0, dot);
                String rest = raw.substring(dot);
                if (imports.containsKey(first)) return imports.get(first) + rest;
                if (declared.containsKey(first)) return declared.get(first) + rest;
                if (Character.isLowerCase(first.charAt(0))) return raw;
                return pkg.isBlank() ? raw : pkg + "." + raw;
            }
            if (imports.containsKey(raw)) return imports.get(raw);
            if (declared.containsKey(raw)) return declared.get(raw);
            if (isJavaLang(raw)) return "java.lang." + raw;
            return pkg.isBlank() ? raw : pkg + "." + raw;
        }
    }

    private static final class Engine {
        private final TreeMaker maker;
        private final Names names;
        private final JavacElements elements;
        private final Set<JCTree.JCCompilationUnit> units = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<JCTree.JCCompilationUnit, UnitInfo> infos = new IdentityHashMap<>();
        private final java.util.List<Relation> relations = new ArrayList<>();
        private final Set<MethodSig> methods = new java.util.LinkedHashSet<>();
        private final Set<String> sourceTypes = new java.util.LinkedHashSet<>();
        private final Set<String> strongTypes = new java.util.LinkedHashSet<>();

        Engine(TreeMaker maker, Names names, JavacElements elements) {
            this.maker = maker;
            this.names = names;
            this.elements = elements;
        }

        void index(JCTree.JCCompilationUnit unit) {
            if (!units.add(unit)) return;
            UnitInfo info = new UnitInfo(unit);
            infos.put(unit, info);

            unit.accept(new com.sun.tools.javac.tree.TreeScanner() {
                String owner = info.pkg;

                @Override
                public void visitClassDef(JCTree.JCClassDecl tree) {
                    String previous = owner;
                    owner = previous.isBlank() ? tree.name.toString() : previous + "." + tree.name;
                    sourceTypes.add(owner);
                    if (hasAnnotation(tree.mods.annotations, "StrongType")) strongTypes.add(owner);
                    super.visitClassDef(tree);
                    owner = previous;
                }
            });
        }

        void discoverGeneratedMembers() {
            methods.clear();
            relations.clear();

            for (String strongType : strongTypes) {
                TypeElement type = elements.getTypeElement(strongType);
                if (type == null) {
                    throw new IllegalStateException("@StrongType symbol unavailable after processing: " + strongType);
                }
                collectStrongRelation(type);
            }

            for (String sourceType : sourceTypes) {
                TypeElement type = elements.getTypeElement(sourceType);
                if (type != null) collectType(type);
            }
        }

        private void collectStrongRelation(TypeElement type) {
            java.util.List<ExecutableElement> unaryConstructors = type.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(e -> (ExecutableElement) e)
                    .filter(e -> e.getParameters().size() == 1)
                    .toList();
            if (unaryConstructors.size() != 1) {
                throw new IllegalStateException("@StrongType requires exactly one unary constructor: "
                        + type.getQualifiedName());
            }
            ExecutableElement constructor = unaryConstructors.get(0);
            String raw = canonical(constructor.getParameters().get(0).asType().toString());
            String strong = canonical(type.asType().toString());
            relations.add(new Relation(raw, strong));
        }

        private void collectType(TypeElement type) {
            String owner = canonical(type.asType().toString());
            for (Element member : type.getEnclosedElements()) {
                if (member.getKind() == ElementKind.METHOD) {
                    ExecutableElement method = (ExecutableElement) member;
                    java.util.List<String> params = method.getParameters().stream()
                            .map(p -> canonical(p.asType().toString()))
                            .toList();
                    methods.add(new MethodSig(owner, method.getSimpleName().toString(), params,
                            canonical(method.getReturnType().toString())));
                } else if (member.getKind().isClass() || member.getKind().isInterface()) {
                    collectType((TypeElement) member);
                }
            }
        }

        void rewrite() {
            for (JCTree.JCCompilationUnit unit : new ArrayList<>(units)) {
                UnitInfo info = infos.get(unit);
                unit.accept(new com.sun.tools.javac.tree.TreeTranslator() {
                    boolean inScope;
                    Map<String, String> locals = new LinkedHashMap<>();

                    @Override
                    public void visitClassDef(JCTree.JCClassDecl tree) {
                        boolean previous = inScope;
                        inScope = previous || hasAnnotation(tree.mods.annotations, "AdaptationScope");
                        super.visitClassDef(tree);
                        inScope = previous;
                    }

                    @Override
                    public void visitMethodDef(JCTree.JCMethodDecl tree) {
                        Map<String, String> previous = locals;
                        locals = new LinkedHashMap<>();
                        for (JCTree.JCVariableDecl param : tree.params) {
                            locals.put(param.name.toString(), canonical(info.resolve(param.vartype.toString())));
                        }
                        super.visitMethodDef(tree);
                        locals = previous;
                    }

                    @Override
                    public void visitVarDef(JCTree.JCVariableDecl tree) {
                        if (tree.vartype != null) locals.put(tree.name.toString(), canonical(info.resolve(tree.vartype.toString())));
                        super.visitVarDef(tree);
                    }

                    @Override
                    public void visitApply(JCTree.JCMethodInvocation tree) {
                        super.visitApply(tree);
                        if (!inScope || tree.args.size() != 1) {
                            result = tree;
                            return;
                        }

                        String name = calledName(tree.meth);
                        String receiver = canonical(receiverType(tree.meth, info, locals));
                        String source = canonical(expressionType(tree.args.get(0), info, locals));
                        if (name == null || receiver == null || source == null) {
                            result = tree;
                            return;
                        }

                        java.util.List<MethodSig> targets = methods.stream()
                                .filter(m -> same(m.owner(), receiver) && m.name().equals(name) && m.params().size() == 1)
                                .toList();
                        if (targets.isEmpty()) {
                            result = tree;
                            return;
                        }

                        if (targets.stream().anyMatch(m -> same(m.params().get(0), source))) {
                            result = tree;
                            return;
                        }

                        java.util.List<Relation> matches = new ArrayList<>();
                        for (MethodSig target : targets) {
                            String expected = target.params().get(0);
                            for (Relation relation : relations) {
                                if (same(relation.raw(), source) && same(relation.strong(), expected)) {
                                    matches.add(relation);
                                }
                            }
                        }
                        if (matches.size() != 1) {
                            result = tree;
                            return;
                        }

                        Relation relation = matches.get(0);
                        JCTree.JCExpression original = tree.args.get(0);
                        JCTree.JCExpression strongType = qualifiedType(relation.strong());
                        JCTree.JCExpression wrapped = maker.at(original.pos)
                                .NewClass(null, List.nil(), strongType, List.of(original), null);
                        tree.args = List.of(wrapped);
                        result = tree;
                    }
                });
            }
        }

        private String receiverType(JCTree.JCExpression method, UnitInfo info, Map<String, String> locals) {
            if (!(method instanceof JCTree.JCFieldAccess access)) return null;
            return expressionType(access.selected, info, locals);
        }

        private String expressionType(JCTree.JCExpression expression, UnitInfo info, Map<String, String> locals) {
            if (expression instanceof JCTree.JCIdent ident) {
                String local = locals.get(ident.name.toString());
                return local != null ? local : canonical(info.resolve(ident.name.toString()));
            }
            if (expression instanceof JCTree.JCNewClass created) return canonical(info.resolve(created.clazz.toString()));
            if (expression instanceof JCTree.JCTypeCast cast) return canonical(info.resolve(cast.clazz.toString()));
            if (expression instanceof JCTree.JCMethodInvocation call) {
                String called = calledName(call.meth);
                String receiver = canonical(receiverType(call.meth, info, locals));
                if (called == null) return null;

                java.util.List<MethodSig> candidates = methods.stream()
                        .filter(m -> m.name().equals(called))
                        .filter(m -> receiver == null || same(m.owner(), receiver))
                        .filter(m -> m.params().size() == call.args.size())
                        .toList();
                if (candidates.size() == 1) return candidates.get(0).returns();
            }
            if (expression instanceof JCTree.JCLiteral literal) {
                Object value = literal.getValue();
                if (value == null) return "<null>";
                if (value instanceof String) return "java.lang.String";
                if (value instanceof Integer) return "int";
                if (value instanceof Long) return "long";
                if (value instanceof Boolean) return "boolean";
                if (value instanceof Double) return "double";
                if (value instanceof Float) return "float";
            }
            return null;
        }

        private JCTree.JCExpression qualifiedType(String fqcn) {
            JCTree.JCExpression expression = null;
            for (String part : canonical(fqcn).split("\\.")) {
                Name name = names.fromString(part);
                expression = expression == null ? maker.Ident(name) : maker.Select(expression, name);
            }
            return expression;
        }
    }

    private static String calledName(JCTree.JCExpression expression) {
        if (expression instanceof JCTree.JCIdent ident) return ident.name.toString();
        if (expression instanceof JCTree.JCFieldAccess access) return access.name.toString();
        return null;
    }

    private static String canonical(String type) {
        if (type == null) return null;
        String t = type.trim().replace('$', '.');
        return t.replace(" ", "");
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && canonical(a).equals(canonical(b));
    }

    private static boolean hasAnnotation(List<JCTree.JCAnnotation> annotations, String simpleName) {
        for (AnnotationTree annotation : annotations) {
            String type = annotation.getAnnotationType().toString();
            if (type.equals(simpleName) || type.endsWith("." + simpleName)) return true;
        }
        return false;
    }

    private static boolean isPrimitive(String type) {
        return switch (type) {
            case "byte", "short", "int", "long", "float", "double", "char", "boolean" -> true;
            default -> false;
        };
    }

    private static boolean isJavaLang(String type) {
        return switch (type) {
            case "String", "Integer", "Long", "Short", "Byte", "Float", "Double", "Character", "Boolean", "Object", "Void", "Number" -> true;
            default -> false;
        };
    }
}
