package com.flowgen.generator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.*;

public class MermaidGenerator {

    private static final String COLOR_GREEN  = "#4CAF50";
    private static final String COLOR_WHITE  = "#fff";
    private static final String COLOR_BLACK  = "#000";
    private static final String COLOR_ORANGE = "#FF5722";
    private static final String COLOR_YELLOW = "#FFC107";
    private static final String COLOR_PURPLE = "#9C27B0";
    private static final String COLOR_BLUE   = "#2196F3";
    private static final String COLOR_RED    = "#f44336";
    private static final String COLOR_GREY   = "#607D8B";

    private static final Set<String> DEFAULT_PATTERNS = Set.of(
        "kafkatemplate", "send", "publish",
        "repository", "save", "findby", "findall", "delete", "deleteall",
        "resttemplate", "exchange", "getforobject", "postforobject",
        "webclient", "retrieve", "bodyto",
        "dynamodb", "putitem", "getitem", "query", "scan",
        "s3client", "putobject", "getobject",
        "sqsclient", "sendmessage",
        "snsclient",
        "feignclient", "feign",
        "jdbctemplate", "update", "queryforlist",
        "redisTemplate", "opsForValue"
    );

    private final StringBuilder out = new StringBuilder();
    private int counter = 0;
    private final Set<String> outboundPatterns = new HashSet<>();
    private final Set<String> detectedOutbounds = new LinkedHashSet<>();

    public MermaidGenerator() {
        outboundPatterns.addAll(DEFAULT_PATTERNS);
    }

    public void addPattern(String pattern) {
        outboundPatterns.add(pattern.toLowerCase());
    }

    public String generate(MethodDeclaration method, String source) {
        out.setLength(0);
        counter = 0;
        detectedOutbounds.clear();

        out.append("```mermaid\n");
        out.append("flowchart TD\n");

        String startId = id();
        node(startId, "⬇️ " + source, "stadium");
        style(startId, COLOR_GREEN, COLOR_WHITE);

        String last = method.getBody()
            .map(body -> walk(body.getStatements(), startId))
            .orElse(startId);

        String endId = id();
        node(endId, "✅ End", "stadium");
        style(endId, COLOR_GREEN, COLOR_WHITE);
        if (last != null) edge(last, endId, null);

        if (!detectedOutbounds.isEmpty()) {
            out.append("\n    %% Auto-detected outbound systems:\n");
            for (String ob : detectedOutbounds) {
                out.append("    %% → ").append(ob).append("\n");
            }
        }

        out.append("```");
        return out.toString();
    }

    public Set<String> getDetectedOutbounds() {
        return detectedOutbounds;
    }

    private String walk(List<Statement> stmts, String prev) {
        String cur = prev;
        for (Statement s : stmts) {
            cur = visit(s, cur);
            if (cur == null) break;
        }
        return cur;
    }

    private String visit(Statement s, String prev) {
        if (s instanceof IfStmt st)          return visitIf(st, prev);
        if (s instanceof SwitchStmt st)      return visitSwitch(st, prev);
        if (s instanceof ForStmt st)         return visitFor(st, prev);
        if (s instanceof ForEachStmt st)     return visitForEach(st, prev);
        if (s instanceof WhileStmt st)       return visitWhile(st, prev);
        if (s instanceof DoStmt st)          return visitDo(st, prev);
        if (s instanceof TryStmt st)         return visitTry(st, prev);
        if (s instanceof ReturnStmt st)      return visitReturn(st, prev);
        if (s instanceof ThrowStmt st)       return visitThrow(st, prev);
        if (s instanceof BlockStmt st)       return walk(st.getStatements(), prev);
        if (s instanceof ExpressionStmt st)  return visitExpr(st, prev);
        return prev;
    }

    private String visitIf(IfStmt s, String prev) {
        String cid = id();
        condition(cid, cut(s.getCondition().toString(), 80));
        edge(prev, cid, null);

        String merge = id();
        out.append("    ").append(merge).append("((\" \"))\n");

        String yesLast = walkBranch(s.getThenStmt(), cid, "Yes");
        if (yesLast != null) edge(yesLast, merge, null);

        if (s.getElseStmt().isPresent()) {
            String noLast = walkBranch(s.getElseStmt().get(), cid, "No");
            if (noLast != null) edge(noLast, merge, null);
        } else {
            edge(cid, merge, "No");
        }

        return merge;
    }

    private String visitSwitch(SwitchStmt s, String prev) {
        String sid = id();
        condition(sid, "switch: " + cut(s.getSelector().toString(), 50));
        edge(prev, sid, null);

        String merge = id();
        out.append("    ").append(merge).append("((\" \"))\n");

        for (SwitchEntry entry : s.getEntries()) {
            String label = entry.getLabels().isEmpty() ? "default"
                : entry.getLabels().stream().map(Object::toString)
                    .reduce((a, b) -> a + ", " + b).orElse("case");

            List<Statement> body = entry.getStatements().stream()
                .filter(st -> !(st instanceof BreakStmt))
                .toList();

            if (!body.isEmpty()) {
                String last = walkBranch(body, sid, label);
                if (last != null) edge(last, merge, null);
            } else {
                edge(sid, merge, label);
            }
        }

        return merge;
    }

    private String visitFor(ForStmt s, String prev) {
        String lid = id();
        String cond = s.getCompare().map(c -> cut(c.toString(), 50)).orElse("loop");
        loop(lid, "for: " + cond);
        edge(prev, lid, null);

        String last = walk(block(s.getBody()), lid);
        if (last != null) edge(last, lid, null);

        String exit = id();
        out.append("    ").append(exit).append("((\" \"))\n");
        edge(lid, exit, "done");
        return exit;
    }

    private String visitForEach(ForEachStmt s, String prev) {
        String lid = id();
        String var = s.getVariable().getVariables().get(0).getNameAsString();
        loop(lid, "for each " + var + " in " + cut(s.getIterable().toString(), 40));
        edge(prev, lid, null);

        String last = walk(block(s.getBody()), lid);
        if (last != null) edge(last, lid, null);

        String exit = id();
        out.append("    ").append(exit).append("((\" \"))\n");
        edge(lid, exit, "done");
        return exit;
    }

    private String visitWhile(WhileStmt s, String prev) {
        String lid = id();
        loop(lid, "while: " + cut(s.getCondition().toString(), 50));
        edge(prev, lid, null);

        String last = walk(block(s.getBody()), lid);
        if (last != null) edge(last, lid, null);

        String exit = id();
        out.append("    ").append(exit).append("((\" \"))\n");
        edge(lid, exit, "false");
        return exit;
    }

    private String visitDo(DoStmt s, String prev) {
        String bodyId = id();
        node(bodyId, "do", "rect");
        edge(prev, bodyId, null);

        String last = walk(block(s.getBody()), bodyId);

        String cid = id();
        loop(cid, cut(s.getCondition().toString(), 50));
        if (last != null) edge(last, cid, null);
        edge(cid, bodyId, "true");

        String exit = id();
        out.append("    ").append(exit).append("((\" \"))\n");
        edge(cid, exit, "false");
        return exit;
    }

    private String visitTry(TryStmt s, String prev) {
        String tid = id();
        node(tid, "🛡️ try", "rect");
        style(tid, COLOR_BLUE, COLOR_WHITE);
        edge(prev, tid, null);

        String merge = id();
        out.append("    ").append(merge).append("((\" \"))\n");

        String tryLast = walk(s.getTryBlock().getStatements(), tid);
        if (tryLast != null) edge(tryLast, merge, null);

        for (CatchClause cc : s.getCatchClauses()) {
            String cid = id();
            node(cid, "🚨 catch: " + cc.getParameter().getType(), "rect");
            style(cid, COLOR_RED, COLOR_WHITE);
            out.append("    ").append(tid).append(" -.->|exception| ").append(cid).append("\n");

            String catchLast = walk(cc.getBody().getStatements(), cid);
            if (catchLast != null) edge(catchLast, merge, null);
        }

        if (s.getFinallyBlock().isPresent()) {
            String fid = id();
            node(fid, "🔒 finally", "rect");
            style(fid, COLOR_GREY, COLOR_WHITE);

            String newMerge = id();
            out.append("    ").append(newMerge).append("((\" \"))\n");
            edge(merge, fid, null);

            String fLast = walk(s.getFinallyBlock().get().getStatements(), fid);
            if (fLast != null) edge(fLast, newMerge, null);
            return newMerge;
        }

        return merge;
    }

    private String visitReturn(ReturnStmt s, String prev) {
        String rid = id();
        appendArrowNode(rid, returnText(s), COLOR_GREEN);
        edge(prev, rid, null);
        return null;
    }

    private String visitThrow(ThrowStmt s, String prev) {
        String tid = id();
        appendArrowNode(tid, throwText(s), COLOR_RED);
        edge(prev, tid, null);
        return null;
    }

    private String visitExpr(ExpressionStmt s, String prev) {
        Expression expr = s.getExpression();
        String nid = id();

        if (expr instanceof MethodCallExpr mc) {
            String callText = fmtCall(mc);
            if (isOutbound(mc)) {
                node(nid, "⬆️ " + callText, "double");
                style(nid, COLOR_ORANGE, COLOR_WHITE);
                detectedOutbounds.add(callText);
            } else {
                node(nid, callText, "rect");
            }
        } else {
            node(nid, cut(expr.toString(), 60), "rect");
        }

        edge(prev, nid, null);
        return nid;
    }

    private void node(String id, String text, String shape) {
        String t = esc(text);
        out.append("    ").append(id);
        switch (shape) {
            case "stadium" -> out.append("([\"").append(t).append("\"])\n");
            case "diamond" -> out.append("{\"").append(t).append("\"}\n");
            case "double"  -> out.append("[[\"").append(t).append("\"]]\n");
            default        -> out.append("[\"").append(t).append("\"]\n");
        }
    }

    private void condition(String id, String text) {
        node(id, text, "diamond");
        style(id, COLOR_YELLOW, COLOR_BLACK);
    }

    private void loop(String id, String text) {
        node(id, "🔁 " + text, "diamond");
        style(id, COLOR_PURPLE, COLOR_WHITE);
    }

    private void style(String id, String bg, String fg) {
        out.append("    style ").append(id)
           .append(" fill:").append(bg).append(",color:").append(fg).append("\n");
    }

    private void edge(String from, String to, String label) {
        if (from == null || to == null) return;
        if (label != null) {
            out.append("    ").append(from).append(" -->|").append(esc(label)).append("| ").append(to).append("\n");
        } else {
            out.append("    ").append(from).append(" --> ").append(to).append("\n");
        }
    }

    private void appendArrowNode(String nid, String text, String color) {
        out.append("    ").append(nid).append(">\"").append(esc(text)).append("\"]\n");
        style(nid, color, COLOR_WHITE);
    }

    private String walkBranch(Statement s, String from, String label) {
        return walkBranch(block(s), from, label);
    }

    private String walkBranch(List<Statement> stmts, String from, String label) {
        if (stmts.isEmpty()) return null;

        Statement first = stmts.get(0);
        String firstId = id();

        if (first instanceof IfStmt || first instanceof SwitchStmt
            || first instanceof ForStmt || first instanceof WhileStmt
            || first instanceof TryStmt) {
            out.append("    ").append(firstId).append("((\" \"))\n");
            edge(from, firstId, label);
            return walk(stmts, firstId);
        }

        if (first instanceof ReturnStmt rs) {
            appendArrowNode(firstId, returnText(rs), COLOR_GREEN);
            edge(from, firstId, label);
            if (stmts.size() > 1) return walk(stmts.subList(1, stmts.size()), firstId);
            return null;
        }

        if (first instanceof ThrowStmt ts) {
            appendArrowNode(firstId, throwText(ts), COLOR_RED);
            edge(from, firstId, label);
            return null;
        }

        boolean ob = first instanceof ExpressionStmt es
            && es.getExpression() instanceof MethodCallExpr mc
            && isOutbound(mc);
        String text = summarize(first);
        if (ob) {
            node(firstId, "⬆️ " + text, "double");
            style(firstId, COLOR_ORANGE, COLOR_WHITE);
            detectedOutbounds.add(text);
        } else {
            node(firstId, text, "rect");
        }

        edge(from, firstId, label);
        if (stmts.size() > 1) return walk(stmts.subList(1, stmts.size()), firstId);
        return firstId;
    }

    private String summarize(Statement s) {
        if (s instanceof ExpressionStmt es) {
            if (es.getExpression() instanceof MethodCallExpr mc) return fmtCall(mc);
            return cut(es.getExpression().toString(), 60);
        }
        return cut(s.toString().split("\n")[0], 60);
    }

    private String scopedName(MethodCallExpr mc) {
        return mc.getScope().map(s -> s.toString() + ".").orElse("") + mc.getNameAsString();
    }

    private String fmtCall(MethodCallExpr mc) {
        return scopedName(mc) + "(...)";
    }

    private boolean isOutbound(MethodCallExpr mc) {
        String full = scopedName(mc).toLowerCase();
        return outboundPatterns.stream().anyMatch(full::contains);
    }

    private List<Statement> block(Statement s) {
        if (s instanceof BlockStmt b) return b.getStatements();
        return List.of(s);
    }

    private String returnText(ReturnStmt rs) {
        return rs.getExpression()
            .map(e -> "↩️ return " + cut(e.toString(), 40))
            .orElse("↩️ return");
    }

    private String throwText(ThrowStmt ts) {
        return "💥 throw " + cut(ts.getExpression().toString(), 40);
    }

    private String id() { return "N" + ++counter; }

    private String normalize(String t) {
        return t.replace("\n", " ").replace("\r", "");
    }

    private String esc(String t) {
        return normalize(t).replace("\"", "'");
    }

    private String cut(String t, int max) {
        t = normalize(t).trim();
        return t.length() > max ? t.substring(0, max - 3) + "..." : t;
    }
}
