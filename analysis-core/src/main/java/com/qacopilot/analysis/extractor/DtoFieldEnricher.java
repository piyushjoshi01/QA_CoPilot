package com.qacopilot.analysis.extractor;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.RequestBodyContract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DtoFieldEnricher {

    private DtoFieldEnricher() {
    }

    public static RequestBodyContract enrich(
            RequestBodyContract body,
            MethodDeclaration method,
            SpringAnalysisOptions options
    ) {
        if (!body.isPresent() || !options.isSymbolSolverEnabled()) {
            return body;
        }
        if (!body.getFields().isEmpty()) {
            return body;
        }
        Optional<Parameter> param = requestBodyParameter(method);
        if (param.isEmpty()) {
            return body;
        }
        try {
            ResolvedType rt = param.get().getType().resolve();
            if (!rt.isReferenceType()) {
                return body;
            }
            Optional<ResolvedReferenceTypeDeclaration> decl = rt.asReferenceType().getTypeDeclaration();
            if (decl.isEmpty()) {
                return body;
            }
            String fqcn = decl.get().getQualifiedName();
            if (isTrivialType(fqcn)) {
                return body;
            }
            List<FieldContract> fields =
                    extractFields(decl.get(), 0, options.getMaxDtoDepth(), options.getMaxDtoFieldsPerType(), new HashSet<>());
            return RequestBodyContract.of(body.getDtoJavaType(), body.isRequired(), fields);
        } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalStateException ex) {
            return body;
        }
    }

    private static Optional<Parameter> requestBodyParameter(MethodDeclaration method) {
        for (Parameter p : method.getParameters()) {
            for (AnnotationExpr ann : p.getAnnotations()) {
                if ("RequestBody".equals(ann.getNameAsString())) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isTrivialType(String fqcn) {
        if (fqcn.startsWith("java.lang.") || fqcn.startsWith("jakarta.") && fqcn.contains(".servlet.")) {
            return true;
        }
        return "java.lang.String".equals(fqcn)
                || "java.util.Map".equals(fqcn)
                || fqcn.startsWith("org.springframework.");
    }

    private static List<FieldContract> extractFields(
            ResolvedReferenceTypeDeclaration decl,
            int depth,
            int maxDepth,
            int maxFields,
            Set<String> visitingFqcn
    ) {
        if (depth > maxDepth) {
            return List.of();
        }
        String fqcn = decl.getQualifiedName();
        if (!visitingFqcn.add(fqcn)) {
            return List.of();
        }
        List<FieldContract> out = new ArrayList<>();
        for (ResolvedFieldDeclaration f : decl.getAllFields()) {
            if (f.isStatic()) {
                continue;
            }
            if (out.size() >= maxFields) {
                break;
            }
            out.add(fieldContract(f));
        }
        return out;
    }

    private static FieldContract fieldContract(ResolvedFieldDeclaration f) {
        String name = f.getName();
        String jtype = f.getType().describe();
        List<String> validations = new ArrayList<>();
        boolean required = false;
        Optional<com.github.javaparser.ast.Node> ast = f.toAst();
        if (ast.isPresent()) {
            FieldDeclaration fd = null;
            com.github.javaparser.ast.Node n = ast.get();
            if (n instanceof FieldDeclaration fdecl) {
                fd = fdecl;
            } else if (n instanceof com.github.javaparser.ast.body.VariableDeclarator vd) {
                com.github.javaparser.ast.Node p = vd.getParentNode().orElse(null);
                if (p instanceof FieldDeclaration fdecl) {
                    fd = fdecl;
                }
            }
            if (fd != null) {
                for (AnnotationExpr ann : fd.getAnnotations()) {
                    String simple = ann.getNameAsString();
                    if (isNotNullish(simple)) {
                        required = true;
                    }
                    if (isBeanValidation(simple)) {
                        validations.add(annotationToSpec(ann));
                    }
                }
            }
        }
        return new FieldContract(name, jtype, required, validations);
    }

    private static boolean isNotNullish(String simple) {
        return "NotNull".equals(simple)
                || "NotBlank".equals(simple)
                || "NotEmpty".equals(simple);
    }

    private static boolean isBeanValidation(String simple) {
        return switch (simple) {
            case "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max", "Email", "Pattern", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero", "DecimalMin", "DecimalMax", "Past", "Future", "Valid" -> true;
            default -> simple.endsWith("Constraint") || simple.contains("Length");
        };
    }

    private static String annotationToSpec(AnnotationExpr ann) {
        String name = ann.getNameAsString();
        if (ann.isMarkerAnnotationExpr()) {
            return name;
        }
        if (ann.isSingleMemberAnnotationExpr()) {
            return name + "=" + ann.asSingleMemberAnnotationExpr().getMemberValue().toString();
        }
        if (ann.isNormalAnnotationExpr()) {
            StringBuilder sb = new StringBuilder(name).append('(');
            NormalAnnotationExpr n = ann.asNormalAnnotationExpr();
            List<String> parts = new ArrayList<>();
            for (MemberValuePair pair : n.getPairs()) {
                parts.add(pair.getNameAsString() + "=" + exprShort(pair.getValue()));
            }
            sb.append(String.join(",", parts));
            sb.append(')');
            return sb.toString();
        }
        return name;
    }

    private static String exprShort(Expression e) {
        String s = e.toString();
        if (s.length() > 80) {
            return s.substring(0, 77) + "...";
        }
        return s;
    }
}
