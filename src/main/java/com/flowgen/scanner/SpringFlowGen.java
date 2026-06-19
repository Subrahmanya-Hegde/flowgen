package com.flowgen.scanner;

import com.flowgen.generator.MermaidGenerator;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SpringFlowGen {

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> ENTRY_POINTS = new LinkedHashMap<>();
    static {
        ENTRY_POINTS.put("GetMapping",     "REST GET");
        ENTRY_POINTS.put("PostMapping",    "REST POST");
        ENTRY_POINTS.put("PutMapping",     "REST PUT");
        ENTRY_POINTS.put("DeleteMapping",  "REST DELETE");
        ENTRY_POINTS.put("PatchMapping",   "REST PATCH");
        ENTRY_POINTS.put("RequestMapping", "REST");
        ENTRY_POINTS.put("KafkaListener",  "Kafka Listener");
        ENTRY_POINTS.put("Scheduled",      "Scheduler");
        ENTRY_POINTS.put("RabbitListener", "RabbitMQ Listener");
        ENTRY_POINTS.put("SqsListener",    "SQS Listener");
        ENTRY_POINTS.put("JmsListener",    "JMS Listener");
        ENTRY_POINTS.put("EventListener",  "Event Listener");
        ENTRY_POINTS.put("TransactionalEventListener", "Tx Event Listener");
    }

    private final Path root;
    private final List<Flow> flows = new ArrayList<>();
    private final MermaidGenerator generator = new MermaidGenerator();

    private SpringFlowGen(String root) {
        this.root = Path.of(root);
    }

    public static SpringFlowGen scan(String sourceRoot) throws IOException {
        SpringFlowGen scanner = new SpringFlowGen(sourceRoot);
        scanner.doScan();
        return scanner;
    }

    public SpringFlowGen addOutbound(String pattern) {
        generator.addPattern(pattern);
        return this;
    }

    public SpringFlowGen outputAll(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Spring Boot Flow Diagrams\n\n");
        sb.append("_Auto-generated: ").append(now()).append("_  \n");
        sb.append("_Source: `").append(root).append("`_\n\n");
        sb.append("---\n\n");

        sb.append("## Index\n\n");
        sb.append("| # | Type | Class | Method | Endpoint |\n");
        sb.append("|---|------|-------|--------|----------|\n");
        for (int i = 0; i < flows.size(); i++) {
            Flow f = flows.get(i);
            sb.append("| ").append(i + 1)
              .append(" | ").append(f.type)
              .append(" | ").append(f.className)
              .append(" | ").append(f.methodName).append("()")
              .append(" | ").append(f.endpoint)
              .append(" |\n");
        }
        sb.append("\n---\n\n");

        for (Flow f : flows) {
            sb.append("## ").append(f.className).append(".").append(f.methodName).append("()\n\n");
            sb.append(flowBody(f)).append("\n\n---\n\n");
        }

        Files.createDirectories(Path.of(path).toAbsolutePath().getParent());
        Files.writeString(Path.of(path), sb.toString());
        System.out.println("📄 " + flows.size() + " flows → " + path);
        return this;
    }

    public SpringFlowGen outputEach(String dir) throws IOException {
        Path d = Path.of(dir);
        Files.createDirectories(d);

        for (Flow f : flows) {
            String name = f.className + "_" + f.methodName + ".md";
            String content = "# " + f.className + "." + f.methodName + "()\n\n"
                + flowBody(f) + "\n";
            Files.writeString(d.resolve(name), content);
        }
        System.out.println("📁 " + flows.size() + " files → " + dir);
        return this;
    }

    public SpringFlowGen print() {
        for (Flow f : flows) {
            System.out.println("════════════════════════════════════════════════");
            System.out.printf("  %s.%s()%n", f.className, f.methodName);
            System.out.printf("  Type:     %s%n", f.type);
            System.out.printf("  Endpoint: %s%n", f.endpoint);
            System.out.printf("  Outbound: %s%n", f.outbounds);
            System.out.println("════════════════════════════════════════════════");
            System.out.println(f.mermaid);
            System.out.println();
        }
        return this;
    }

    public List<Flow> getFlows() { return Collections.unmodifiableList(flows); }

    private void doScan() throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    try { parseFile(file); }
                    catch (Exception e) {
                        System.err.println("⚠️ Skip: " + file.getFileName() + " — " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        flows.sort(Comparator.comparing(f -> {
            if (f.type.startsWith("REST"))    return 0;
            if (f.type.contains("Kafka"))     return 1;
            if (f.type.contains("Scheduler")) return 2;
            return 3;
        }));

        System.out.println("✅ Found " + flows.size() + " Spring entry points");
    }

    private void parseFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);

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
                            file.toString()
                        ));
                    });
                }
            });
        });
    }

    private String flowBody(Flow f) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Type:** ").append(f.type).append("  \n");
        sb.append("**Endpoint:** `").append(f.endpoint).append("`  \n");
        if (!f.outbounds.isEmpty()) {
            sb.append("**Outbound:** ").append(String.join(", ", f.outbounds)).append("  \n");
        }
        sb.append("\n").append(f.mermaid);
        return sb.toString();
    }

    private String buildEndpoint(AnnotationExpr annot, String annotName,
                                 String classPath, String type) {
        switch (annotName) {
            case "GetMapping", "PostMapping", "PutMapping",
                 "DeleteMapping", "PatchMapping", "RequestMapping" -> {
                String methodPath = extractValue(annot);
                String full = (classPath != null ? classPath : "") +
                              (methodPath != null ? methodPath : "");
                return full.isEmpty() ? "/" : full;
            }
            case "KafkaListener"  -> { return resolveAnnotationValue(annot, "topics", "(topic)"); }
            case "RabbitListener" -> { return resolveAnnotationValue(annot, "queues", "(queue)"); }
            case "Scheduled" -> {
                String cron       = extractAttribute(annot, "cron");
                String fixedRate  = extractAttribute(annot, "fixedRate");
                String fixedDelay = extractAttribute(annot, "fixedDelay");
                if (cron != null)       return "cron: " + cron;
                if (fixedRate != null)  return "every " + fixedRate + "ms";
                if (fixedDelay != null) return "delay " + fixedDelay + "ms";
                return "(scheduled)";
            }
            case "SqsListener" -> {
                String value = extractValue(annot);
                return value != null ? value : "(sqs-queue)";
            }
            default -> {
                String value = extractValue(annot);
                return value != null ? value : "(" + annotName + ")";
            }
        }
    }

    private Optional<AnnotationExpr> findAnnotation(List<AnnotationExpr> annotations, String name) {
        return annotations.stream()
            .filter(a -> a.getNameAsString().equals(name))
            .findFirst();
    }

    private String extractPath(List<AnnotationExpr> annotations, String name) {
        return findAnnotation(annotations, name)
            .map(this::extractValue)
            .orElse(null);
    }

    private String extractValue(AnnotationExpr annot) {
        if (annot instanceof SingleMemberAnnotationExpr s) {
            return stripQuotes(s.getMemberValue().toString());
        }
        String v = extractAttribute(annot, "value");
        return v != null ? v : extractAttribute(annot, "path");
    }

    private String resolveAnnotationValue(AnnotationExpr annot, String attr, String fallback) {
        String v = extractAttribute(annot, attr);
        if (v == null) v = extractValue(annot);
        return v != null ? v : fallback;
    }

    private String extractAttribute(AnnotationExpr annot, String attrName) {
        if (annot instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                .filter(p -> p.getNameAsString().equals(attrName))
                .findFirst()
                .map(p -> stripQuotes(p.getValue().toString()))
                .orElse(null);
        }
        return null;
    }

    private String stripQuotes(String s) {
        if (s == null) return null;
        s = s.replaceAll("[\"{}]", "").trim();
        return s.isEmpty() ? null : s;
    }

    private String now() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }

    public record Flow(
        String className,
        String methodName,
        String type,
        String endpoint,
        List<String> outbounds,
        String mermaid,
        String filePath
    ) {}
}
