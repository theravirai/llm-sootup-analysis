import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.PathBasedAnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.DefaultRuntimeAnalysisInputLocation;
import sootup.core.model.SourceType;
import sootup.java.core.views.JavaView;
import sootup.core.typehierarchy.TypeHierarchy;
import sootup.core.typehierarchy.ViewTypeHierarchy;
import sootup.java.core.types.JavaClassType;
import sootup.core.types.ClassType;
import sootup.java.core.JavaIdentifierFactory;
import sootup.core.signatures.MethodSignature;
import sootup.callgraph.CallGraph;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;
import sootup.spark.Spark;
import sootup.spark.SparkOptions;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.InvokableStmt;
import sootup.core.jimple.common.expr.JNewExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.java.core.JavaSootClass;
import sootup.core.model.SootMethod;
import sootup.core.frontend.SootClassSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;

public class SootUpAnalyzer {
    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }
        
        String targetDir = args[0];
        String mode = args[1];
        
        List<AnalysisInputLocation> inputLocations = new ArrayList<>();
        inputLocations.add(new DefaultRuntimeAnalysisInputLocation());
        for (String path : targetDir.split(",")) {
            inputLocations.add(PathBasedAnalysisInputLocation.create(java.nio.file.Paths.get(path), SourceType.Application));
        }
        JavaView view = new JavaView(inputLocations);
        JavaIdentifierFactory factory = view.getIdentifierFactory();
        
        try {
            if (mode.equalsIgnoreCase("hierarchy")) {
                runHierarchy(view, factory, args[2]);
            } else if (mode.equalsIgnoreCase("callgraph")) {
                runCallGraph(view, factory, args);
            } else if (mode.equalsIgnoreCase("find-allocations")) {
                String targetClass = args[2];
                String scopeClass = args.length > 3 ? args[3] : null;
                runFindAllocations(view, factory, targetClass, scopeClass);
            } else if (mode.equalsIgnoreCase("find-invocations")) {
                String targetClass = args[2];
                String targetMethod = args[3];
                String scopeClass = args.length > 4 ? args[4] : null;
                runFindInvocations(view, factory, targetClass, targetMethod, scopeClass);
            } else if (mode.equalsIgnoreCase("inspect-method")) {
                String targetClass = args[2];
                String targetMethod = args[3];
                runInspectMethod(view, factory, targetClass, targetMethod);
            } else {
                System.err.println("Error: Unknown mode '" + mode + "'");
                printUsage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java SootUpAnalyzer <target-dir> hierarchy <class-name>");
        System.err.println("  java SootUpAnalyzer <target-dir> callgraph <cha|rta|spark> <entry-class> <entry-method> <return-type> [param-types...]");
        System.err.println("  java SootUpAnalyzer <target-dir> find-allocations <class-name> [scope-class]");
        System.err.println("  java SootUpAnalyzer <target-dir> find-invocations <class-name> <method-name> [scope-class]");
        System.err.println("  java SootUpAnalyzer <target-dir> inspect-method <class-name> <method-name>");
    }

    private static void runHierarchy(JavaView view, JavaIdentifierFactory factory, String targetClassName) {
        JavaClassType classType = factory.getClassType(targetClassName);
        System.out.println("=== CLASS HIERARCHY FACT ===");
        System.out.println("Target: " + targetClassName);
        
        if (!view.getClass(classType).isPresent()) {
            System.out.println("Status: Class not found in view.");
            return;
        }
        
        TypeHierarchy th = new ViewTypeHierarchy(view);
        System.out.println("\nSuperclasses:");
        th.superClassesOf(classType).forEach(sc -> System.out.println(" - " + sc.getFullyQualifiedName()));
        
        System.out.println("\nSubclasses:");
        th.subclassesOf(classType).forEach(sc -> System.out.println(" - " + sc.getFullyQualifiedName()));
        
        System.out.println("\nImplemented Interfaces:");
        th.implementedInterfacesOf(classType).forEach(sc -> System.out.println(" - " + sc.getFullyQualifiedName()));
    }

    private static void runCallGraph(JavaView view, JavaIdentifierFactory factory, String[] args) {
        if (args.length < 6) {
            System.err.println("Error: callgraph mode requires algorithm, entry-class, entry-method, and return-type.");
            return;
        }
        String algorithm = args[2];
        String entryClass = args[3];
        String entryMethod = args[4];
        String returnType = args[5];
        
        List<String> paramTypes = new ArrayList<>();
        for (int i = 6; i < args.length; i++) paramTypes.add(args[i]);
        
        JavaClassType classType = factory.getClassType(entryClass);
        MethodSignature entrySignature = factory.getMethodSignature(classType, entryMethod, returnType, paramTypes);
        
        CallGraph cg;
        if (algorithm.equalsIgnoreCase("cha")) {
            cg = new ClassHierarchyAnalysisAlgorithm(view).initialize(Collections.singletonList(entrySignature));
        } else if (algorithm.equalsIgnoreCase("rta")) {
            cg = new RapidTypeAnalysisAlgorithm(view).initialize(Collections.singletonList(entrySignature));
        } else if (algorithm.equalsIgnoreCase("spark")) {
            SparkOptions options = SparkOptions.builder().onFlyCallGraph(true).build();
            Spark spark = Spark.builder().view(view).entryPoints(Collections.singletonList(entrySignature)).sparkOptions(options).build();
            spark.solve();
            cg = spark.getCallGraph();
        } else {
            System.err.println("Unknown algorithm: " + algorithm);
            return;
        }
        
        System.out.println("=== CALL GRAPH FACT ===");
        System.out.println("Target: " + entrySignature);
        System.out.println("Algorithm: " + algorithm.toUpperCase());
        System.out.println("Total Reachable Methods: " + cg.getMethodSignatures().size());
        
        System.out.println("\nCalls FROM target:");
        cg.callTargetsFrom(entrySignature).forEach(callee -> System.out.println(" -> " + callee));
        
        System.out.println("\nCalls TO target:");
        cg.callSourcesTo(entrySignature).forEach(caller -> System.out.println(" <- " + caller));
    }

    private static void runFindAllocations(JavaView view, JavaIdentifierFactory factory, String targetClass, String scopeClass) {
        System.out.println("=== ALLOCATION SITE FACT ===");
        System.out.println("Target Class: " + targetClass);
        if (scopeClass != null) System.out.println("Scope Class: " + scopeClass);
        
        List<JavaSootClass> classesToScan = getClassesToScan(view, factory, scopeClass);
        
        for (JavaSootClass sc : classesToScan) {
            for (SootMethod method : sc.getMethods()) {
                if (!method.hasBody()) continue;
                for (Stmt stmt : method.getBody().getStmts()) {
                    if (stmt instanceof JAssignStmt) {
                        JAssignStmt assign = (JAssignStmt) stmt;
                        if (assign.getRightOp() instanceof JNewExpr) {
                            JNewExpr newExpr = (JNewExpr) assign.getRightOp();
                            String typeStr = newExpr.getType().toString();
                            boolean match = typeStr.equals(targetClass);
                            if (!match && newExpr.getType() instanceof ClassType) {
                                match = ((ClassType) newExpr.getType()).getClassName().equals(targetClass)
                                     || ((ClassType) newExpr.getType()).getFullyQualifiedName().equals(targetClass);
                            }
                            if (match) {
                                printMatch(method, stmt, "Allocates: " + typeStr);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void runFindInvocations(JavaView view, JavaIdentifierFactory factory, String targetClass, String targetMethod, String scopeClass) {
        System.out.println("=== INVOCATION SITE FACT ===");
        System.out.println("Target Method: " + targetClass + "." + targetMethod);
        if (scopeClass != null) System.out.println("Scope Class: " + scopeClass);
        
        List<JavaSootClass> classesToScan = getClassesToScan(view, factory, scopeClass);
        
        for (JavaSootClass sc : classesToScan) {
            for (SootMethod method : sc.getMethods()) {
                if (!method.hasBody()) continue;
                for (Stmt stmt : method.getBody().getStmts()) {
                    AbstractInvokeExpr invokeExpr = null;
                    if (stmt.isInvokableStmt()) {
                        Optional<AbstractInvokeExpr> opt = stmt.asInvokableStmt().getInvokeExpr();
                        if (opt.isPresent()) invokeExpr = opt.get();
                    } else if (stmt.isJAssignStmt()) {
                        JAssignStmt assign = stmt.asJAssignStmt();
                        if (assign.getRightOp() instanceof AbstractInvokeExpr) {
                            invokeExpr = (AbstractInvokeExpr) assign.getRightOp();
                        }
                    }
                    if (invokeExpr != null) {
                        MethodSignature sig = invokeExpr.getMethodSignature();
                        String fqName = sig.getDeclClassType().getFullyQualifiedName();
                        String simpleName = sig.getDeclClassType().getClassName();
                        if ((fqName.equals(targetClass) || simpleName.equals(targetClass)) && sig.getName().equals(targetMethod)) {
                            printMatch(method, stmt, "Invokes: " + sig);
                        }
                    }
                }
            }
        }
    }

    private static void runInspectMethod(JavaView view, JavaIdentifierFactory factory, String targetClass, String targetMethod) {
        JavaClassType classType = factory.getClassType(targetClass);
        Optional<JavaSootClass> scOpt = view.getClass(classType);
        
        if (!scOpt.isPresent()) {
            System.out.println("Error: Class " + targetClass + " not found.");
            return;
        }
        
        JavaSootClass sc = scOpt.get();
        Set<? extends SootMethod> methods = sc.getMethodsByName(targetMethod);
        if (methods.isEmpty()) {
            System.out.println("Error: Method " + targetMethod + " not found in " + targetClass);
            return;
        }
        
        for (SootMethod method : methods) {
            System.out.println("=== METHOD INSPECTION FACT ===");
            System.out.println("Signature: " + method.getSignature());
            System.out.println("Declaring Class: " + sc.getName());
            
            if (sc.hasOuterClass()) {
                System.out.println("Outer Class: " + sc.getOuterClass().get().getClassName());
            }
            
            SootClassSource source = sc.getClassSource();
            if (source != null) {
                System.out.println("Source File: " + source.getSourcePath());
            }
            
            System.out.println("\nJimple Body:");
            if (method.hasBody()) {
                for (Stmt stmt : method.getBody().getStmts()) {
                    int line = stmt.getPositionInfo().getStmtPosition().getFirstLine();
                    String linePrefix = line > 0 ? "[Line " + line + "] " : "[Line ?] ";
                    System.out.println(linePrefix + stmt.toString());
                }
            } else {
                System.out.println("<No body available>");
            }
            System.out.println();
        }
    }

    private static List<JavaSootClass> getClassesToScan(JavaView view, JavaIdentifierFactory factory, String scopeClass) {
        List<JavaSootClass> classes = new ArrayList<>();
        if (scopeClass != null) {
            view.getClass(factory.getClassType(scopeClass)).ifPresent(classes::add);
        } else {
            // Note: In a real large codebase, iterating all classes in view might be slow.
            // But for SWE-bench localized testing it's acceptable.
            view.getClasses()
                .filter(c -> c instanceof JavaSootClass)
                .map(c -> (JavaSootClass) c)
                .forEach(classes::add);
        }
        return classes;
    }
    
    private static void printMatch(SootMethod enclosingMethod, Stmt stmt, String detail) {
        System.out.println("\nMatch Found:");
        System.out.println(" - " + detail);
        System.out.println(" - Enclosing Method: " + enclosingMethod.getSignature());
        
        int line = stmt.getPositionInfo().getStmtPosition().getFirstLine();
        if (line > 0) {
            System.out.println(" - Source Line: " + line);
        }
        System.out.println(" - Jimple Stmt: " + stmt.toString());
    }
}
