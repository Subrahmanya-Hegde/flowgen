# Internal Call Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand internal method calls as Mermaid subgraphs within flow diagrams, so users see the full logic of service/component methods owned by the project.

**Architecture:** Two-pass scan — Pass 1 builds a `MethodRegistry` of all classes and methods in the source tree, Pass 2 generates flowcharts using the registry to resolve internal calls into subgraphs. Root package is auto-detected as the shortest common package prefix.

**Tech Stack:** JavaParser AST, Mermaid subgraph syntax, JUnit 5

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `flowgen-core/src/main/java/com/flowgen/scanner/MethodRegistry.java` | **CREATE** | Stores parsed MethodDeclarations, resolves calls by className.methodName, checks if a class is internal |
| `flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java` | **MODIFY** | Two-pass scan, root package detection, passes registry to generator |
| `flowgen-core/src/main/java/com/flowgen/generator/MermaidGenerator.java` | **MODIFY** | Accepts registry + root package, emits subgraphs for internal calls, cycle detection |
| `flowgen-core/src/test/java/com/flowgen/scanner/MethodRegistryTest.java` | **CREATE** | Unit tests for registry resolution and internal check |
| `flowgen-core/src/test/java/com/flowgen/generator/MermaidGeneratorTest.java` | **CREATE** | Tests for subgraph emission and cycle detection |
| `flowgen-core/pom.xml` | **MODIFY** | Add JUnit 5 test dependency |
| `flowgen-example/src/main/java/com/flowgen/example/OrderService.java` | **CREATE** | Service class with real logic for end-to-end subgraph testing |

---

### Task 1: Add JUnit 5 dependency to flowgen-core

**Files:**
- Modify: `flowgen-core/pom.xml`

- [ ] **Step 1: Add JUnit 5 dependency**

Add the test dependency to `flowgen-core/pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.javaparser</groupId>
        <artifactId>javaparser-core</artifactId>
        <version>${javaparser.version}</version>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.4</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Create test directory structure**

```bash
mkdir -p flowgen-core/src/test/java/com/flowgen/scanner
mkdir -p flowgen-core/src/test/java/com/flowgen/generator
```

- [ ] **Step 3: Verify build**

```bash
mvn compile -f flowgen-core/pom.xml
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add flowgen-core/pom.xml
git commit -m "chore: add JUnit 5 dependency to flowgen-core"
```

---

### Task 2: Create MethodRegistry

**Files:**
- Create: `flowgen-core/src/main/java/com/flowgen/scanner/MethodRegistry.java`
- Create: `flowgen-core/src/test/java/com/flowgen/scanner/MethodRegistryTest.java`

- [ ] **Step 1: Write failing tests for MethodRegistry**

```java
package com.flowgen.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MethodRegistryTest {

    private MethodRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MethodRegistry();
    }

    @Test
    void registerAndResolveMethod() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example.service;\n" +
            "class OrderService {\n" +
            "  void validate() { }\n" +
            "}"
        );
        registry.register(cu);

        Optional<MethodDeclaration> result = registry.resolve("OrderService", "validate");
        assertTrue(result.isPresent());
        assertEquals("validate", result.get().getNameAsString());
    }

    @Test
    void resolveReturnsEmptyForUnknownClass() {
        Optional<MethodDeclaration> result = registry.resolve("Unknown", "method");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveReturnsEmptyForUnknownMethod() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example;\n" +
            "class Foo { void bar() { } }"
        );
        registry.register(cu);

        Optional<MethodDeclaration> result = registry.resolve("Foo", "missing");
        assertTrue(result.isEmpty());
    }

    @Test
    void isInternalReturnsTrueForSameRootPackage() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.hegde.project.service;\n" +
            "class OrderService { void run() { } }"
        );
        registry.register(cu);

        assertTrue(registry.isInternal("OrderService", "com.hegde.project"));
    }

    @Test
    void isInternalReturnsFalseForDifferentRootPackage() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.other.lib;\n" +
            "class Helper { void run() { } }"
        );
        registry.register(cu);

        assertFalse(registry.isInternal("Helper", "com.hegde.project"));
    }

    @Test
    void isInternalReturnsFalseForUnregisteredClass() {
        assertFalse(registry.isInternal("Unknown", "com.hegde.project"));
    }

    @Test
    void resolveCaseInsensitiveOnScope() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example;\n" +
            "class ValidationService { void check() { } }"
        );
        registry.register(cu);

        Optional<MethodDeclaration> result = registry.resolve("validationService", "check");
        assertTrue(result.isPresent());
    }

    @Test
    void registerMultipleClasses() {
        CompilationUnit cu1 = StaticJavaParser.parse(
            "package com.example;\n" +
            "class Foo { void a() { } }"
        );
        CompilationUnit cu2 = StaticJavaParser.parse(
            "package com.example;\n" +
            "class Bar { void b() { } }"
        );
        registry.register(cu1);
        registry.register(cu2);

        assertTrue(registry.resolve("Foo", "a").isPresent());
        assertTrue(registry.resolve("Bar", "b").isPresent());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -f flowgen-core/pom.xml -Dtest=MethodRegistryTest -pl flowgen-core
```

Expected: Compilation error — `MethodRegistry` does not exist.

- [ ] **Step 3: Implement MethodRegistry**

Create `flowgen-core/src/main/java/com/flowgen/scanner/MethodRegistry.java`:

```java
package com.flowgen.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.*;

public class MethodRegistry {

    private final Map<String, List<MethodDeclaration>> methods = new HashMap<>();
    private final Map<String, String> classPackages = new HashMap<>();

    public void register(CompilationUnit cu) {
        String pkg = cu.getPackageDeclaration()
            .map(p -> p.getNameAsString())
            .orElse("");

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String className = clazz.getNameAsString();
            classPackages.put(className.toLowerCase(), pkg);

            clazz.findAll(MethodDeclaration.class).forEach(method -> {
                String key = className.toLowerCase() + "." + method.getNameAsString();
                methods.computeIfAbsent(key, k -> new ArrayList<>()).add(method);
            });
        });
    }

    public Optional<MethodDeclaration> resolve(String className, String methodName) {
        String key = className.toLowerCase() + "." + methodName;
        List<MethodDeclaration> matches = methods.get(key);
        if (matches == null || matches.isEmpty()) return Optional.empty();
        return Optional.of(matches.get(0));
    }

    public boolean isInternal(String className, String rootPackage) {
        String pkg = classPackages.get(className.toLowerCase());
        if (pkg == null) return false;
        return pkg.startsWith(rootPackage);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -f flowgen-core/pom.xml -Dtest=MethodRegistryTest
```

Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add flowgen-core/src/main/java/com/flowgen/scanner/MethodRegistry.java \
       flowgen-core/src/test/java/com/flowgen/scanner/MethodRegistryTest.java
git commit -m "feat: add MethodRegistry for resolving internal method calls"
```

---

### Task 3: Add root package detection to SpringFlowGen

**Files:**
- Modify: `flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java`

- [ ] **Step 1: Add rootPackage detection method**

Add this method to `SpringFlowGen`:

```java
private String detectRootPackage(List<String> packages) {
    if (packages.isEmpty()) return "";
    if (packages.size() == 1) return packages.get(0);

    String[] reference = packages.get(0).split("\\.");
    int commonLength = reference.length;

    for (int i = 1; i < packages.size(); i++) {
        String[] parts = packages.get(i).split("\\.");
        int limit = Math.min(commonLength, parts.length);
        int match = 0;
        for (int j = 0; j < limit; j++) {
            if (!reference[j].equals(parts[j])) break;
            match++;
        }
        commonLength = match;
    }

    if (commonLength == 0) return "";
    StringJoiner joiner = new StringJoiner(".");
    for (int i = 0; i < commonLength; i++) {
        joiner.add(reference[i]);
    }
    return joiner.toString();
}
```

- [ ] **Step 2: Add MethodRegistry field and update constructor**

Add these fields to `SpringFlowGen`:

```java
private final MethodRegistry registry = new MethodRegistry();
private String rootPackage = "";
```

- [ ] **Step 3: Commit**

```bash
git add flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java
git commit -m "feat: add root package detection to SpringFlowGen"
```

---

### Task 4: Convert doScan to two-pass architecture

**Files:**
- Modify: `flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java`

- [ ] **Step 1: Refactor doScan to two passes**

Replace the current `doScan()` method with:

```java
private void doScan() throws IOException {
    List<CompilationUnit> compilationUnits = new ArrayList<>();
    List<String> packages = new ArrayList<>();

    // Pass 1: parse all files, build registry
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (file.toString().endsWith(".java")) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    compilationUnits.add(cu);
                    registry.register(cu);
                    cu.getPackageDeclaration()
                        .ifPresent(p -> packages.add(p.getNameAsString()));
                } catch (Exception e) {
                    System.err.println("⚠️ Skip: " + file.getFileName() + " — " + e.getMessage());
                }
            }
            return FileVisitResult.CONTINUE;
        }
    });

    rootPackage = detectRootPackage(packages);

    // Pass 2: generate flowcharts for entry points
    for (CompilationUnit cu : compilationUnits) {
        scanEntryPoints(cu);
    }

    flows.sort(Comparator.comparing(f -> {
        if (f.type.startsWith("REST"))    return 0;
        if (f.type.contains("Kafka"))     return 1;
        if (f.type.contains("Scheduler")) return 2;
        return 3;
    }));

    System.out.println("✅ Found " + flows.size() + " Spring entry points");
    System.out.println("📦 Root package: " + rootPackage);
}
```

- [ ] **Step 2: Extract scanEntryPoints from parseFile**

Rename `parseFile(Path)` logic into `scanEntryPoints(CompilationUnit)`:

```java
private void scanEntryPoints(CompilationUnit cu) {
    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
        String className = clazz.getNameAsString();
        String classPath = extractPath(clazz.getAnnotations(), "RequestMapping");

        clazz.findAll(MethodDeclaration.class).forEach(method -> {
            for (Map.Entry<String, String> entry : ENTRY_POINTS.entrySet()) {
                String annotName = entry.getKey();
                String type = entry.getValue();

                findAnnotation(method.getAnnotations(), annotName).ifPresent(annot -> {
                    String endpoint = buildEndpoint(annot, annotName, classPath, type);
                    String source = type + " — " + endpoint;
                    String mermaid = generator.generate(method, source);

                    flows.add(new Flow(
                        className,
                        method.getNameAsString(),
                        type,
                        endpoint,
                        new ArrayList<>(generator.getDetectedOutbounds()),
                        mermaid,
                        cu.getStorage().map(s -> s.getPath().toString()).orElse("")
                    ));
                });
            }
        });
    });
}
```

Remove the old `parseFile(Path)` method.

- [ ] **Step 3: Add CompilationUnit import if missing**

The existing file already imports `CompilationUnit`. Just verify.

- [ ] **Step 4: Verify build**

```bash
mvn compile -f flowgen-core/pom.xml
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Run end-to-end test**

```bash
mvn flowgen:generate -f flowgen-example/pom.xml
```

Expected: Same 5 flows generated as before. Output should additionally show `📦 Root package: com.flowgen.example`.

- [ ] **Step 6: Commit**

```bash
git add flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java
git commit -m "refactor: convert doScan to two-pass architecture with registry"
```

---

### Task 5: Pass registry to MermaidGenerator and emit subgraphs

**Files:**
- Modify: `flowgen-core/src/main/java/com/flowgen/generator/MermaidGenerator.java`
- Create: `flowgen-core/src/test/java/com/flowgen/generator/MermaidGeneratorTest.java`

- [ ] **Step 1: Write failing tests for subgraph expansion**

Create `flowgen-core/src/test/java/com/flowgen/generator/MermaidGeneratorTest.java`:

```java
package com.flowgen.generator;

import com.flowgen.scanner.MethodRegistry;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MermaidGeneratorTest {

    private MermaidGenerator generator;
    private MethodRegistry registry;
    private static final String ROOT_PACKAGE = "com.example";

    @BeforeEach
    void setUp() {
        generator = new MermaidGenerator();
        registry = new MethodRegistry();
    }

    @Test
    void internalCallRenderedAsSubgraph() {
        CompilationUnit serviceCu = StaticJavaParser.parse(
            "package com.example.service;\n" +
            "class ValidationService {\n" +
            "  void validate() {\n" +
            "    checkNull();\n" +
            "  }\n" +
            "  void checkNull() { }\n" +
            "}"
        );
        registry.register(serviceCu);

        CompilationUnit controllerCu = StaticJavaParser.parse(
            "package com.example.controller;\n" +
            "class MyController {\n" +
            "  ValidationService validationService;\n" +
            "  void handle() {\n" +
            "    validationService.validate();\n" +
            "  }\n" +
            "}"
        );
        MethodDeclaration handleMethod = controllerCu
            .findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals("handle"))
            .findFirst().orElseThrow();

        String result = generator.generate(handleMethod, "Test", registry, ROOT_PACKAGE);

        assertTrue(result.contains("subgraph"), "Should contain a subgraph for internal call");
        assertTrue(result.contains("validationService.validate()"));
    }

    @Test
    void externalCallRenderedAsPlainNode() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example;\n" +
            "class MyController {\n" +
            "  Object externalLib;\n" +
            "  void handle() {\n" +
            "    externalLib.doSomething();\n" +
            "  }\n" +
            "}"
        );
        MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);

        String result = generator.generate(method, "Test", registry, ROOT_PACKAGE);

        assertFalse(result.contains("subgraph"), "External call should not produce subgraph");
        assertTrue(result.contains("externalLib.doSomething(...)"));
    }

    @Test
    void outboundCallStillRenderedAsOrange() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example;\n" +
            "class MyController {\n" +
            "  Object kafkaTemplate;\n" +
            "  void handle() {\n" +
            "    kafkaTemplate.send(\"topic\");\n" +
            "  }\n" +
            "}"
        );
        MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);

        String result = generator.generate(method, "Test", registry, ROOT_PACKAGE);

        assertFalse(result.contains("subgraph"));
        assertTrue(result.contains("⬆️"));
    }

    @Test
    void recursiveCallRenderedWithLabel() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example;\n" +
            "class TreeWalker {\n" +
            "  TreeWalker treeWalker;\n" +
            "  void walk() {\n" +
            "    treeWalker.walk();\n" +
            "  }\n" +
            "}"
        );
        registry.register(cu);

        MethodDeclaration method = cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals("walk"))
            .findFirst().orElseThrow();

        String result = generator.generate(method, "Test", registry, ROOT_PACKAGE);

        assertTrue(result.contains("🔄"), "Recursive call should show cycle indicator");
        assertFalse(result.contains("subgraph"), "Recursive call should NOT expand as subgraph");
    }

    @Test
    void generateWithoutRegistryStillWorks() {
        CompilationUnit cu = StaticJavaParser.parse(
            "package com.example;\n" +
            "class Foo {\n" +
            "  void bar() {\n" +
            "    baz();\n" +
            "  }\n" +
            "}"
        );
        MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);

        String result = generator.generate(method, "Test");

        assertNotNull(result);
        assertTrue(result.contains("baz(...)"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -f flowgen-core/pom.xml -Dtest=MermaidGeneratorTest
```

Expected: Compilation error — `generate(method, source, registry, rootPackage)` overload does not exist.

- [ ] **Step 3: Add registry-aware generate method**

In `MermaidGenerator.java`, add fields and modify the generate method:

```java
private MethodRegistry registry;
private String rootPackage;
private Set<String> callStack;
```

Add a new overloaded `generate` method:

```java
public String generate(MethodDeclaration method, String source,
                       MethodRegistry registry, String rootPackage) {
    this.registry = registry;
    this.rootPackage = rootPackage;
    this.callStack = new HashSet<>();
    callStack.add(extractCallerKey(method));
    String result = generate(method, source);
    this.registry = null;
    this.rootPackage = null;
    this.callStack = null;
    return result;
}

private String extractCallerKey(MethodDeclaration method) {
    return method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
        .map(c -> c.getNameAsString() + "." + method.getNameAsString())
        .orElse(method.getNameAsString());
}
```

Add the import:
```java
import com.flowgen.scanner.MethodRegistry;
```

- [ ] **Step 4: Modify visitExpr to handle internal calls**

Replace the `visitExpr` method:

```java
private String visitExpr(ExpressionStmt s, String prev) {
    Expression expr = s.getExpression();
    String nid = id();

    if (expr instanceof MethodCallExpr mc) {
        String callText = fmtCall(mc);

        if (isOutbound(mc)) {
            node(nid, "⬆️ " + callText, "double");
            style(nid, COLOR_ORANGE, COLOR_WHITE);
            detectedOutbounds.add(callText);
        } else if (registry != null && isInternalCall(mc)) {
            return expandInternal(mc, prev);
        } else {
            node(nid, callText, "rect");
        }
    } else {
        node(nid, cut(expr.toString(), 60), "rect");
    }

    edge(prev, nid, null);
    return nid;
}
```

- [ ] **Step 5: Add internal call detection and subgraph expansion**

Add these methods to `MermaidGenerator`:

```java
private boolean isInternalCall(MethodCallExpr mc) {
    String scope = mc.getScope().map(Object::toString).orElse(null);
    if (scope == null) return false;

    String methodName = mc.getNameAsString();
    String callKey = resolveClassName(scope) + "." + methodName;

    if (callStack != null && callStack.contains(callKey)) return false;

    return registry.resolve(scope, methodName).isPresent()
        && registry.isInternal(scope, rootPackage);
}

private String resolveClassName(String scope) {
    return scope;
}

private String expandInternal(MethodCallExpr mc, String prev) {
    String scope = mc.getScope().map(Object::toString).orElse("");
    String methodName = mc.getNameAsString();
    String callKey = resolveClassName(scope) + "." + methodName;

    if (callStack != null && callStack.contains(callKey)) {
        String nid = id();
        node(nid, "🔄 " + fmtCall(mc) + " (recursive)", "rect");
        style(nid, COLOR_GREY, COLOR_WHITE);
        edge(prev, nid, null);
        return nid;
    }

    Optional<MethodDeclaration> resolved = registry.resolve(scope, methodName);
    if (resolved.isEmpty()) {
        String nid = id();
        node(nid, fmtCall(mc), "rect");
        edge(prev, nid, null);
        return nid;
    }

    MethodDeclaration target = resolved.get();
    String subId = "sub_" + id();

    out.append("    subgraph ").append(subId)
       .append(" [\"").append(esc(fmtCall(mc))).append("\"]\n");

    callStack.add(callKey);

    String innerLast = target.getBody()
        .map(body -> walk(body.getStatements(), null))
        .orElse(null);

    callStack.remove(callKey);

    out.append("    end\n");

    String entryId = subId + "_entry";
    edge(prev, subId, null);

    return innerLast != null ? innerLast : subId;
}
```

Add the import:
```java
import java.util.Optional;
```

- [ ] **Step 6: Update walkBranch for internal calls**

In the `walkBranch(List<Statement>, String, String)` method, add internal call handling after the outbound check. Replace the existing block that handles the first statement's outbound detection:

```java
boolean ob = first instanceof ExpressionStmt es
    && es.getExpression() instanceof MethodCallExpr mc
    && isOutbound(mc);

boolean internal = !ob && first instanceof ExpressionStmt es2
    && es2.getExpression() instanceof MethodCallExpr mc2
    && registry != null && isInternalCall(mc2);

if (internal) {
    MethodCallExpr mc2 = (MethodCallExpr) ((ExpressionStmt) first).getExpression();
    counter--;
    String subLast = expandInternal(mc2, null);
    String subId = "sub_" + findLastSubgraphId();
    edge(from, subId, label);
    if (stmts.size() > 1) {
        String continuation = walk(stmts.subList(1, stmts.size()), subLast != null ? subLast : from);
        return continuation;
    }
    return subLast;
}

String text = summarize(first);
if (ob) {
    node(firstId, "⬆️ " + text, "double");
    style(firstId, COLOR_ORANGE, COLOR_WHITE);
    detectedOutbounds.add(text);
} else {
    node(firstId, text, "rect");
}
```

**Note:** The walkBranch internal call handling is complex. A simpler approach is to let `walkBranch` delegate to `visit` for expression statements. Replace the outbound + text block at the end of `walkBranch` with:

```java
if (first instanceof ExpressionStmt) {
    counter--;
    String exprLast = visitExpr((ExpressionStmt) first, from);
    if (label != null && exprLast != null) {
        // label was already handled by edge(from, ..., label) in visitExpr? No.
        // We need to re-route: instead of visitExpr connecting from->nid,
        // we want from->nid with the label.
    }
}
```

**Simpler approach — keep walkBranch as-is for now.** Internal calls within branches will render as plain nodes in this first version. The `visitExpr` path handles the primary case (expression statements at the top level of a method body). This avoids complex rewiring in walkBranch.

Remove the walkBranch changes above. Leave `walkBranch` unchanged.

- [ ] **Step 7: Run tests**

```bash
mvn test -f flowgen-core/pom.xml -Dtest=MermaidGeneratorTest
```

Expected: All 5 tests PASS.

- [ ] **Step 8: Run all tests together**

```bash
mvn test -f flowgen-core/pom.xml
```

Expected: All tests PASS (MethodRegistryTest + MermaidGeneratorTest).

- [ ] **Step 9: Commit**

```bash
git add flowgen-core/src/main/java/com/flowgen/generator/MermaidGenerator.java \
       flowgen-core/src/test/java/com/flowgen/generator/MermaidGeneratorTest.java
git commit -m "feat: expand internal method calls as Mermaid subgraphs"
```

---

### Task 6: Wire registry into SpringFlowGen's generate calls

**Files:**
- Modify: `flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java`

- [ ] **Step 1: Update scanEntryPoints to pass registry**

In `scanEntryPoints`, change the generator call from:

```java
String mermaid = generator.generate(method, source);
```

to:

```java
String mermaid = generator.generate(method, source, registry, rootPackage);
```

- [ ] **Step 2: Verify build**

```bash
mvn compile -f flowgen-core/pom.xml
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java
git commit -m "feat: wire MethodRegistry into flowchart generation"
```

---

### Task 7: Add example service class and end-to-end test

**Files:**
- Create: `flowgen-example/src/main/java/com/flowgen/example/OrderService.java`

- [ ] **Step 1: Create OrderService with real logic**

Create `flowgen-example/src/main/java/com/flowgen/example/OrderService.java`:

```java
package com.flowgen.example;

public class OrderService {

    private Object orderRepository;
    private Object kafkaTemplate;

    public Object validateAndPlace(Object order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        String tier = resolveTier(order);

        if ("PREMIUM".equals(tier)) {
            applyDiscount(order);
        }

        orderRepository.equals(order);
        kafkaTemplate.equals("order.placed");

        return order;
    }

    private String resolveTier(Object order) {
        return "STANDARD";
    }

    private void applyDiscount(Object order) {
    }
}
```

- [ ] **Step 2: Update OrderController to use OrderService**

In `OrderController.java`, add an `OrderService` field and call it from `placeOrder`:

Add a call to the service at the start of `placeOrder`, after validation:

```java
orderService.validateAndPlace(order);
```

Add the field:

```java
private OrderService orderService;
```

- [ ] **Step 3: Build and run end-to-end**

```bash
mvn install -N -f pom.xml && mvn install -f flowgen-core/pom.xml && mvn install -f flowgen-maven-plugin/pom.xml
mvn flowgen:generate -f flowgen-example/pom.xml
```

Expected:
- 8 flows generated
- `placeOrder` flow should contain a `subgraph` block for `orderService.validateAndPlace()`
- The subgraph should show the if/throw/if-premium logic from OrderService

- [ ] **Step 4: Verify generated output contains subgraph**

```bash
grep -l "subgraph" docs/flowgen/OrderController/placeOrder.md
```

Expected: File found with subgraph content.

- [ ] **Step 5: Commit**

```bash
git add flowgen-example/src/main/java/com/flowgen/example/OrderService.java \
       flowgen-example/src/main/java/com/flowgen/example/OrderController.java
git commit -m "feat: add OrderService for internal call expansion demo"
```

---

### Task 8: Regenerate docs and final verification

**Files:**
- Modify: generated files in `docs/flowgen/`

- [ ] **Step 1: Clean and regenerate all docs**

```bash
rm -rf docs/flowgen
mvn flowgen:generate -f flowgen-example/pom.xml
```

- [ ] **Step 2: Verify index.md is correct**

```bash
cat docs/flowgen/index.md
```

Expected: 8 flows listed with links.

- [ ] **Step 3: Verify subgraph in placeOrder**

```bash
cat docs/flowgen/OrderController/placeOrder.md
```

Expected: Contains `subgraph` with `orderService.validateAndPlace()` and the internal logic expanded.

- [ ] **Step 4: Verify other flows still work (no subgraph needed)**

```bash
cat docs/flowgen/OrderController/getOrder.md
```

Expected: Normal flow, no subgraph (getOrder has no internal service calls).

- [ ] **Step 5: Run full test suite**

```bash
mvn test -f flowgen-core/pom.xml
```

Expected: All tests PASS.

- [ ] **Step 6: Commit generated docs**

```bash
git add docs/flowgen/
git commit -m "docs: regenerate flow diagrams with internal call expansion"
```
