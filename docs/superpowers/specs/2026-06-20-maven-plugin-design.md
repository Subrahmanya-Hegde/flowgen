# FlowGen Maven Plugin

## Goal

Convert FlowGen from a standalone `main()` app into a standard Maven plugin. Any Spring Boot project adds `<plugin>` to their pom.xml, runs `mvn flowgen:generate`, and gets Mermaid flowcharts for all entry points.

## Project Structure

```
flowgen/
├── pom.xml                    ← parent POM (multi-module)
├── flowgen-core/              ← scanner + generator library
│   ├── pom.xml
│   └── src/main/java/com/flowgen/
│       ├── generator/MermaidGenerator.java
│       └── scanner/SpringFlowGen.java
├── flowgen-maven-plugin/      ← Maven plugin Mojo
│   ├── pom.xml
│   └── src/main/java/com/flowgen/plugin/FlowGenMojo.java
└── flowgen-example/           ← example Spring project for testing
    ├── pom.xml
    └── src/main/java/com/flowgen/example/
        ├── OrderController.java
        └── PaymentEventListener.java
```

## Plugin Usage (consumer project)

```xml
<plugin>
    <groupId>com.flowgen</groupId>
    <artifactId>flowgen-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <outputDirectory>docs/flowgen</outputDirectory> <!-- optional, this is the default -->
    </configuration>
</plugin>
```

Run: `mvn flowgen:generate`

To auto-run during build, bind to a phase:
```xml
<executions>
    <execution>
        <goals><goal>generate</goal></goals>
        <phase>compile</phase>
    </execution>
</executions>
```

## Plugin Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `sourceDirectory` | `${project.build.sourceDirectory}` | Source root to scan |
| `outputDirectory` | `docs/flowgen` | Where to write generated files |

## Output

- `<outputDirectory>/index.md` — table of contents with links to individual files
- `<outputDirectory>/<Class>_<method>.md` — one file per entry point method

## Changes from Current Code

1. **Delete** `FlowGenRunner.java` — replaced by `FlowGenMojo`
2. **Move** `MermaidGenerator` and `SpringFlowGen` into `flowgen-core` module
3. **Split** `SpringFlowGen.outputAll()` into `outputIndex()` (TOC with links) and `outputEach()` (individual files, unchanged)
4. **Move** example classes to `flowgen-example` module with real Spring dependency
5. **Create** `FlowGenMojo` in `flowgen-maven-plugin` — wires `SpringFlowGen` with Maven project source directory

## FlowGenMojo

```java
@Mojo(name = "generate", defaultPhase = LifecyclePhase.NONE)
public class FlowGenMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.sourceDirectory}", readonly = true)
    private File sourceDirectory;

    @Parameter(property = "flowgen.outputDirectory", defaultValue = "docs/flowgen")
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        SpringFlowGen.scan(sourceDirectory.getAbsolutePath())
            .print()
            .outputIndex(outputDirectory)
            .outputEach(outputDirectory);
    }
}
```

## SpringFlowGen Changes

Replace `outputAll()` with `outputIndex()`:
- Generates `index.md` with a table linking to individual files
- Links use relative paths: `[OrderController.placeOrder()](OrderController_placeOrder.md)`
- No embedded Mermaid in index — just the TOC table

`outputEach()` stays as-is.
