package com.qacopilot.analysis.extractor;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SpringdocAnnotationParser {

    private SpringdocAnnotationParser() {
    }

    public static SpringdocHints parse(MethodDeclaration method, ClassOrInterfaceDeclaration controller) {
        String summary = null;
        Set<String> tags = new LinkedHashSet<>();
        Set<Integer> codes = new LinkedHashSet<>();

        for (AnnotationExpr ann : controller.getAnnotations()) {
            if ("Tag".equals(ann.getNameAsString())) {
                readStringArrayOrSingle(ann).forEach(tags::add);
            }
        }

        for (AnnotationExpr ann : method.getAnnotations()) {
            if ("Operation".equals(ann.getNameAsString())) {
                Optional<String> s = readStringMember(ann, "summary");
                if (s.isPresent() && !s.get().isBlank()) {
                    summary = s.get();
                }
                readStringArrayMember(ann, "tags").forEach(tags::add);
            }
        }

        for (AnnotationExpr ann : method.findAll(AnnotationExpr.class)) {
            if ("ApiResponse".equals(ann.getNameAsString())) {
                readStringMember(ann, "responseCode")
                        .flatMap(SpringdocAnnotationParser::parseInt)
                        .ifPresent(codes::add);
            }
        }

        return new SpringdocHints(summary, new ArrayList<>(tags), new ArrayList<>(codes));
    }

    private static Optional<Integer> parseInt(String s) {
        try {
            return Optional.of(Integer.parseInt(s.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static List<String> readStringArrayOrSingle(AnnotationExpr ann) {
        List<String> out = new ArrayList<>();
        if (ann.isSingleMemberAnnotationExpr()) {
            collectStringExprs(ann.asSingleMemberAnnotationExpr().getMemberValue(), out);
        } else if (ann.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = ann.asNormalAnnotationExpr();
            for (MemberValuePair pair : normal.getPairs()) {
                if ("name".equals(pair.getNameAsString())) {
                    toStringValue(pair.getValue()).ifPresent(out::add);
                }
            }
        }
        return out;
    }

    private static List<String> readStringArrayMember(AnnotationExpr ann, String key) {
        List<String> out = new ArrayList<>();
        if (!ann.isNormalAnnotationExpr()) {
            return out;
        }
        for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
            if (key.equals(pair.getNameAsString())) {
                collectStringExprs(pair.getValue(), out);
            }
        }
        return out;
    }

    private static void collectStringExprs(Expression e, List<String> out) {
        if (e.isStringLiteralExpr()) {
            out.add(e.asStringLiteralExpr().asString());
        } else if (e.isArrayInitializerExpr()) {
            for (Expression v : e.asArrayInitializerExpr().getValues()) {
                collectStringExprs(v, out);
            }
        }
    }

    private static Optional<String> readStringMember(AnnotationExpr ann, String key) {
        if (ann.isSingleMemberAnnotationExpr() && "value".equals(key)) {
            return toStringValue(ann.asSingleMemberAnnotationExpr().getMemberValue());
        }
        if (!ann.isNormalAnnotationExpr()) {
            return Optional.empty();
        }
        for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
            if (key.equals(pair.getNameAsString())) {
                return toStringValue(pair.getValue());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> toStringValue(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            return Optional.of(expression.asStringLiteralExpr().asString());
        }
        if (expression.isArrayInitializerExpr()) {
            ArrayInitializerExpr arrayExpr = expression.asArrayInitializerExpr();
            NodeList<Expression> values = arrayExpr.getValues();
            if (values.size() == 1 && values.get(0).isStringLiteralExpr()) {
                return Optional.of(values.get(0).asStringLiteralExpr().asString());
            }
        }
        return Optional.empty();
    }
}
