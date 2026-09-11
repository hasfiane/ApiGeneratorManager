package dev.typebridge.javac;

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
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Narrow integration probe for ApiGeneratorManager.
 *
 * This is intentionally not the full TypeBridge engine. It validates the critical
 * compiler contract we need from the real project: after Lombok annotation processing,
 * a generated builder member is visible and an invalid UUID -> CustomerId call can be
 * elaborated before javac attribution rejects it.
 */
public final class LombokTypeBridgeProbe implements Plugin {
    @Override
    public String getName() {
        return "TypeBridgeLombokProbe";
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context context = ((BasicJavacTask) task).getContext();
        TreeMaker maker = TreeMaker.instance(context);
        Names names = Names.instance(context);
        JavacElements elements = JavacElements.instance(context);
        Set<JCTree.JCCompilationUnit> units = Collections.newSetFromMap(new IdentityHashMap<>());

        task.addTaskListener(new TaskListener() {
            private boolean rewritten;

            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.PARSE && e.getCompilationUnit() instanceof JCTree.JCCompilationUnit unit) {
                    units.add(unit);
                    return;
                }
                if (e.getKind() != TaskEvent.Kind.ANNOTATION_PROCESSING || rewritten) {
                    return;
                }
                rewritten = true;

                TypeElement builder = elements.getTypeElement("experiment.Order.OrderBuilder");
                if (builder == null) {
                    throw new IllegalStateException("Lombok builder type was not visible after annotation processing");
                }

                ExecutableElement setter = null;
                for (Element member : builder.getEnclosedElements()) {
                    if (member.getKind() == ElementKind.METHOD
                            && member.getSimpleName().contentEquals("customerId")) {
                        ExecutableElement method = (ExecutableElement) member;
                        if (method.getParameters().size() == 1) {
                            setter = method;
                            break;
                        }
                    }
                }
                if (setter == null) {
                    throw new IllegalStateException("Lombok customerId(CustomerId) builder method was not visible");
                }
                String parameterType = setter.getParameters().get(0).asType().toString();
                if (!parameterType.equals("experiment.CustomerId")) {
                    throw new IllegalStateException("Unexpected Lombok builder parameter type: " + parameterType);
                }

                for (JCTree.JCCompilationUnit unit : new ArrayList<>(units)) {
                    unit.accept(new com.sun.tools.javac.tree.TreeTranslator() {
                        @Override
                        public void visitApply(JCTree.JCMethodInvocation tree) {
                            super.visitApply(tree);
                            String called = calledName(tree.meth);
                            if (!"customerId".equals(called) || tree.args.size() != 1) {
                                result = tree;
                                return;
                            }
                            JCTree.JCExpression original = tree.args.get(0);
                            JCTree.JCExpression strongType = qualifiedType("experiment.CustomerId", maker, names);
                            JCTree.JCExpression wrapped = maker.at(original.pos)
                                    .NewClass(null, List.nil(), strongType, List.of(original), null);
                            tree.args = List.of(wrapped);
                            result = tree;
                        }
                    });
                }
            }
        });
    }

    private static String calledName(JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCIdent ident) {
            return ident.name.toString();
        }
        if (expr instanceof JCTree.JCFieldAccess access) {
            return access.name.toString();
        }
        return null;
    }

    private static JCTree.JCExpression qualifiedType(String fqcn, TreeMaker maker, Names names) {
        JCTree.JCExpression result = null;
        for (String part : fqcn.split("\\.")) {
            Name name = names.fromString(part);
            result = result == null ? maker.Ident(name) : maker.Select(result, name);
        }
        return result;
    }
}
