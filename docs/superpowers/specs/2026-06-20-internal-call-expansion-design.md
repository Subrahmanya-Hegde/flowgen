# Internal Call Expansion

## Goal

Follow internal method calls as Mermaid subgraphs within the flow diagram. Only expand calls to classes within the project's own package. External calls (third-party libs, Spring framework) stay as plain boxes. Outbound calls (DB, Kafka, REST infra) stay as orange outbound nodes.

## Call Classification

Every method call in the AST falls into one of three categories:

| Category | Condition | Rendering |
|----------|-----------|-----------|
| **Outbound** | Matches DEFAULT_PATTERNS (kafkaTemplate, repository.save, etc.) | 🟠 Orange double-bordered node |
| **Internal** | Scope class exists in the scanned source tree AND shares the project's root package | 🔷 Mermaid `subgraph` with the called method's body expanded inside |
| **External** | Everything else (Spring framework, third-party, unresolved) | Plain rect node (current behavior) |

## Root Package Detection

Detected automatically from the scanned source directory. The shortest common package prefix across all parsed `.java` files becomes the root package. Example: if we scan files in `com.hegde.project.controller` and `com.hegde.project.service`, the root package is `com.hegde.project`.

No plugin configuration needed — auto-detected.

## Architecture

### Phase 1: Pre-scan — build a method registry

Before generating any flowcharts, scan all `.java` files and build a `MethodRegistry`:

```java
public class MethodRegistry {
    // key: "ClassName.methodName", value: the MethodDeclaration AST node
    private final Map<String, MethodDeclaration> methods;
    // key: "ClassName", value: fully qualified package
    private final Map<String, String> classPackages;
    
    public Optional<MethodDeclaration> resolve(String className, String methodName);
    public boolean isInternal(String className, String rootPackage);
}
```

`SpringFlowGen.doScan()` changes from a single-pass scan to:
1. **Pass 1:** Parse all files, populate `MethodRegistry` with every class + method
2. **Pass 2:** Generate flowcharts for entry points (same as today), but pass the `MethodRegistry` to `MermaidGenerator`

### Phase 2: Generator uses the registry

`MermaidGenerator.generate()` receives the `MethodRegistry` and root package. In `visitExpr`, when it encounters a method call:

1. Check if outbound → orange node (unchanged)
2. Check if `registry.resolve(scope, methodName)` finds a match AND `registry.isInternal(scope, rootPackage)` → emit a `subgraph`
3. Otherwise → plain rect node (unchanged)

### Subgraph rendering

For an internal call like `validationService.validate(order)`:

```mermaid
subgraph sub_N5 ["validationService.validate()"]
    N6["check null"]
    N7{"isValid"}
    N6 --> N7
end
```

The generator walks the resolved `MethodDeclaration`'s body inside a `subgraph` block. Recursion happens naturally — if the resolved method itself calls another internal method, it emits a nested subgraph.

### Cycle detection

To prevent infinite recursion (A calls B calls A), maintain a `Set<String> callStack` during generation. If a method is already in the stack, render it as a plain node with a "🔄 recursive" label instead of expanding.

## Files Changed

| File | Change |
|------|--------|
| `flowgen-core/.../scanner/MethodRegistry.java` | **NEW** — stores parsed method declarations and resolves calls |
| `flowgen-core/.../scanner/SpringFlowGen.java` | **MODIFY** — two-pass scan, build registry, detect root package, pass registry to generator |
| `flowgen-core/.../generator/MermaidGenerator.java` | **MODIFY** — accept registry + root package, emit subgraphs for internal calls in `visitExpr` |

## Scope Limitations

- Resolution is by simple class name + method name. No type inference or overload resolution — if multiple classes share the same name, the first match wins.
- Private helper methods within the same class are expanded only if called via `this.method()` or `method()` (no scope). Fields like `this.someField.method()` resolve against field type if the field's type class is in the registry.
- For this first version, we resolve against the **scope expression's text** — `validationService.validate()` looks up class names matching `validationService` (case-insensitive on the simple name) or a field whose type matches a registered class.

## Not in Scope

- Cross-module call resolution (only scans one source directory)
- Interface/abstract method resolution (would need full type analysis)
- Lambda and method reference expansion
