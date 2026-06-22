# FlowGen Maven Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert FlowGen from a standalone main() into a multi-module Maven plugin that any Spring Boot project can use via `mvn flowgen:generate`.

**Architecture:** Three Maven modules — `flowgen-core` (scanner + generator library), `flowgen-maven-plugin` (Mojo wrapping core), `flowgen-example` (test project). The current root pom.xml becomes a parent POM with `<packaging>pom</packaging>` and `<modules>`.

**Tech Stack:** Java 17, Maven 3.9, JavaParser 3.26.4, Maven Plugin API 3.9.9, Maven Plugin Annotations 3.15.1

---

## File Map

```
flowgen/
├── pom.xml                                          ← MODIFY: convert to parent POM
├── flowgen-core/
│   ├── pom.xml                                      ← CREATE
│   └── src/main/java/com/flowgen/
│       ├── generator/MermaidGenerator.java           ← MOVE from src/main/java (unchanged)
│       └── scanner/SpringFlowGen.java               ← MOVE + MODIFY (outputAll → outputIndex)
├── flowgen-maven-plugin/
│   ├── pom.xml                                      ← CREATE
│   └── src/main/java/com/flowgen/plugin/
│       └── FlowGenMojo.java                         ← CREATE
├── flowgen-example/
│   ├── pom.xml                                      ← CREATE
│   └── src/main/java/com/flowgen/example/
│       ├── OrderController.java                  ← MOVE + MODIFY (use real Spring annotations)
│       └── PaymentEventListener.java              ← MOVE + MODIFY (use real Spring annotations)
└── src/main/java/com/flowgen/
    └── FlowGenRunner.java                           ← DELETE
```

---

### Task 1: Convert root pom.xml to parent POM

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Update root pom.xml to parent POM**

Replace the entire `pom.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.flowgen</groupId>
    <artifactId>flowgen</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <name>FlowGen</name>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javaparser.version>3.26.4</javaparser.version>
        <maven-plugin-api.version>3.9.9</maven-plugin-api.version>
        <maven-plugin-annotations.version>3.15.1</maven-plugin-annotations.version>
    </properties>

    <modules>
        <module>flowgen-core</module>
        <module>flowgen-maven-plugin</module>
        <module>flowgen-example</module>
    </modules>
</project>
```

- [ ] **Step 2: Verify the POM is valid XML**

Run: `mvn validate -N`
Expected: `BUILD SUCCESS` (the `-N` flag skips modules since they don't exist yet)

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: convert root pom.xml to parent POM"
```

---

### Task 2: Create flowgen-core module

**Files:**
- Create: `flowgen-core/pom.xml`
- Move: `src/main/java/com/flowgen/generator/MermaidGenerator.java` → `flowgen-core/src/main/java/com/flowgen/generator/MermaidGenerator.java`
- Move: `src/main/java/com/flowgen/scanner/SpringFlowGen.java` → `flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java`

- [ ] **Step 1: Create flowgen-core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flowgen</groupId>
        <artifactId>flowgen</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>flowgen-core</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.javaparser</groupId>
            <artifactId>javaparser-core</artifactId>
            <version>${javaparser.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Move source files into flowgen-core**

```bash
mkdir -p flowgen-core/src/main/java/com/flowgen/generator
mkdir -p flowgen-core/src/main/java/com/flowgen/scanner
mv src/main/java/com/flowgen/generator/MermaidGenerator.java flowgen-core/src/main/java/com/flowgen/generator/
mv src/main/java/com/flowgen/scanner/SpringFlowGen.java flowgen-core/src/main/java/com/flowgen/scanner/
```

- [ ] **Step 3: Verify flowgen-core compiles**

Run: `mvn compile -pl flowgen-core`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add flowgen-core/ src/
git commit -m "feat: create flowgen-core module with scanner and generator"
```

---

### Task 3: Replace outputAll with outputIndex in SpringFlowGen

**Files:**
- Modify: `flowgen-core/src/main/java/com/flowgen/scanner/SpringFlowGen.java`

- [ ] **Step 1: Replace `outputAll()` with `outputIndex()`**

Replace the `outputAll` method with:

```java
public SpringFlowGen outputIndex(String dir) throws IOException {
    Path d = Path.of(dir);
    Files.createDirectories(d);

    StringBuilder sb = new StringBuilder();
    sb.append("# Spring Boot Flow Diagrams\n\n");
    sb.append("_Auto-generated: ").append(now()).append("_\n\n");

    sb.append("| # | Type | Class | Method | Endpoint |\n");
    sb.append("|---|------|-------|--------|----------|\n");
    for (int i = 0; i < flows.size(); i++) {
        Flow f = flows.get(i);
        String fileName = f.className + "_" + f.methodName + ".md";
        sb.append("| ").append(i + 1)
          .append(" | ").append(f.type)
          .append(" | ").append(f.className)
          .append(" | [").append(f.methodName).append("()](").append(fileName).append(")")
          .append(" | `").append(f.endpoint).append("`")
          .append(" |\n");
    }

    Files.writeString(d.resolve("index.md"), sb.toString());
    System.out.println("📄 " + flows.size() + " flows → " + d.resolve("index.md"));
    return this;
}
```

- [ ] **Step 2: Verify flowgen-core compiles**

Run: `mvn compile -pl flowgen-core`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add flowgen-core/
git commit -m "feat: replace outputAll with outputIndex for TOC with links"
```

---

### Task 4: Create flowgen-maven-plugin module

**Files:**
- Create: `flowgen-maven-plugin/pom.xml`
- Create: `flowgen-maven-plugin/src/main/java/com/flowgen/plugin/FlowGenMojo.java`

- [ ] **Step 1: Create flowgen-maven-plugin/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flowgen</groupId>
        <artifactId>flowgen</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>flowgen-maven-plugin</artifactId>
    <packaging>maven-plugin</packaging>

    <dependencies>
        <dependency>
            <groupId>com.flowgen</groupId>
            <artifactId>flowgen-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.maven</groupId>
            <artifactId>maven-plugin-api</artifactId>
            <version>${maven-plugin-api.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.maven.plugin-tools</groupId>
            <artifactId>maven-plugin-annotations</artifactId>
            <version>${maven-plugin-annotations.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-plugin-plugin</artifactId>
                <version>${maven-plugin-annotations.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create FlowGenMojo.java**

Create `flowgen-maven-plugin/src/main/java/com/flowgen/plugin/FlowGenMojo.java`:

```java
package com.flowgen.plugin;

import com.flowgen.scanner.SpringFlowGen;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

@Mojo(name = "generate")
public class FlowGenMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.sourceDirectory}", readonly = true)
    private File sourceDirectory;

    @Parameter(property = "flowgen.outputDirectory", defaultValue = "docs/flowgen")
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDirectory.exists()) {
            getLog().warn("Source directory not found: " + sourceDirectory);
            return;
        }

        try {
            SpringFlowGen.scan(sourceDirectory.getAbsolutePath())
                .print()
                .outputIndex(outputDirectory)
                .outputEach(outputDirectory);
        } catch (Exception e) {
            throw new MojoExecutionException("FlowGen failed", e);
        }
    }
}
```

- [ ] **Step 3: Verify plugin compiles**

Run: `mvn compile -pl flowgen-core,flowgen-maven-plugin`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add flowgen-maven-plugin/
git commit -m "feat: create flowgen-maven-plugin with FlowGenMojo"
```

---

### Task 5: Create flowgen-example module

**Files:**
- Create: `flowgen-example/pom.xml`
- Move: `src/main/java/com/flowgen/example/OrderController.java` → `flowgen-example/src/main/java/com/flowgen/example/OrderController.java`
- Move: `src/main/java/com/flowgen/example/PaymentEventListener.java` → `flowgen-example/src/main/java/com/flowgen/example/PaymentEventListener.java`

- [ ] **Step 1: Create flowgen-example/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flowgen</groupId>
        <artifactId>flowgen</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>flowgen-example</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.4.3</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
            <version>3.3.4</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>com.flowgen</groupId>
                <artifactId>flowgen-maven-plugin</artifactId>
                <version>${project.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Move example files**

```bash
mkdir -p flowgen-example/src/main/java/com/flowgen/example
mv src/main/java/com/flowgen/example/OrderController.java flowgen-example/src/main/java/com/flowgen/example/
mv src/main/java/com/flowgen/example/PaymentEventListener.java flowgen-example/src/main/java/com/flowgen/example/
```

- [ ] **Step 3: Update OrderController.java to use real Spring annotations**

Remove the inline `@interface` stubs at the top of the file. Replace them with real imports:

```java
import org.springframework.web.bind.annotation.*;
```

Remove these lines:
```java
@interface RestController {}
@interface RequestMapping { String value() default ""; }
@interface PostMapping { String value() default ""; }
@interface GetMapping { String value() default ""; }
@interface DeleteMapping { String value() default ""; }
@interface KafkaListener { String topics() default ""; String groupId() default ""; }
@interface Scheduled { String cron() default ""; }
```

- [ ] **Step 4: Update PaymentEventListener.java to use real Spring annotations**

Add imports:
```java
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
```

- [ ] **Step 5: Verify example compiles**

Run: `mvn compile -pl flowgen-example`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add flowgen-example/ src/
git commit -m "feat: create flowgen-example module with real Spring annotations"
```

---

### Task 6: Delete FlowGenRunner and clean up old src directory

**Files:**
- Delete: `src/main/java/com/flowgen/FlowGenRunner.java`

- [ ] **Step 1: Delete FlowGenRunner and leftover src directory**

```bash
rm -rf src/main/java/com/flowgen/
```

If `src/main/java` is now empty:
```bash
rm -rf src/
```

- [ ] **Step 2: Verify full build**

Run: `mvn clean compile`
Expected: `BUILD SUCCESS` across all three modules

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: delete FlowGenRunner, replaced by FlowGenMojo"
```

---

### Task 7: End-to-end test — run the plugin on flowgen-example

- [ ] **Step 1: Install the plugin locally**

Run: `mvn clean install -pl flowgen-core,flowgen-maven-plugin`
Expected: `BUILD SUCCESS`, both artifacts installed to local `.m2`

- [ ] **Step 2: Run the plugin on the example project**

Run: `mvn flowgen:generate -pl flowgen-example`
Expected: Console prints detected flows, creates `docs/flowgen/index.md` and individual `.md` files

- [ ] **Step 3: Verify output files**

Check `docs/flowgen/index.md` exists and contains a table with links.
Check individual files like `docs/flowgen/OrderController_placeOrder.md` exist and contain Mermaid diagrams.

- [ ] **Step 4: Commit generated docs**

```bash
git add docs/flowgen/
git commit -m "docs: add generated flow diagrams from flowgen-example"
```
