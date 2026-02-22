package dev.tsj.compiler.backend.jvm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class JavaOverloadResolver {
    Resolution resolve(
            final List<Candidate> candidates,
            final List<Argument> arguments
    ) {
        final List<CandidateOutcome> outcomes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            outcomes.add(evaluate(candidate, arguments));
        }
        final List<CandidateOutcome> applicable = outcomes.stream()
                .filter(CandidateOutcome::applicable)
                .toList();
        if (applicable.isEmpty()) {
            return new Resolution(
                    Status.NO_APPLICABLE,
                    null,
                    noApplicableDiagnostic(arguments, outcomes),
                    List.copyOf(outcomes),
                    List.of()
            );
        }

        int bestScore = Integer.MAX_VALUE;
        for (CandidateOutcome outcome : applicable) {
            if (outcome.score() < bestScore) {
                bestScore = outcome.score();
            }
        }
        final int selectedBestScore = bestScore;
        final List<CandidateOutcome> bestOutcomes = applicable.stream()
                .filter(outcome -> outcome.score() == selectedBestScore)
                .toList();
        if (bestOutcomes.size() == 1) {
            return new Resolution(
                    Status.SELECTED,
                    bestOutcomes.getFirst().candidate().identity(),
                    "",
                    List.copyOf(outcomes),
                    List.of(bestOutcomes.getFirst().candidate().identity())
            );
        }

        final CandidateOutcome specificityWinner = selectSpecificityWinner(bestOutcomes, arguments.size());
        if (specificityWinner != null) {
            return new Resolution(
                    Status.SELECTED,
                    specificityWinner.candidate().identity(),
                    "",
                    List.copyOf(outcomes),
                    bestOutcomes.stream().map(outcome -> outcome.candidate().identity()).toList()
            );
        }

        return new Resolution(
                Status.AMBIGUOUS,
                null,
                ambiguousDiagnostic(arguments, bestOutcomes),
                List.copyOf(outcomes),
                bestOutcomes.stream().map(outcome -> outcome.candidate().identity()).toList()
        );
    }

    static List<Candidate> candidatesForClassMethod(
            final Class<?> ownerClass,
            final String methodName,
            final InvokeKind invokeKind
    ) {
        return candidatesForClassMethod(ownerClass, methodName, invokeKind, Map.of());
    }

    static List<Candidate> candidatesForClassMethod(
            final Class<?> ownerClass,
            final String methodName,
            final InvokeKind invokeKind,
            final Map<String, List<JavaNullabilityAnalyzer.NullabilityState>> parameterNullabilityByMethodKey
    ) {
        Objects.requireNonNull(ownerClass, "ownerClass");
        Objects.requireNonNull(methodName, "methodName");
        if (invokeKind != InvokeKind.STATIC_METHOD && invokeKind != InvokeKind.INSTANCE_METHOD) {
            throw new IllegalArgumentException("Method candidates require STATIC_METHOD or INSTANCE_METHOD invokeKind.");
        }
        final boolean expectStatic = invokeKind == InvokeKind.STATIC_METHOD;
        final List<Method> visible = new ArrayList<>();
        final List<Method> nonBridge = new ArrayList<>();
        for (Method method : ownerClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) != expectStatic) {
                continue;
            }
            visible.add(method);
            if (!method.isBridge() && !method.isSynthetic()) {
                nonBridge.add(method);
            }
        }
        final List<Method> selected = nonBridge.isEmpty() ? visible : nonBridge;
        selected.sort(java.util.Comparator
                .comparing(JavaOverloadResolver::methodDescriptor)
                .thenComparing(method -> method.getDeclaringClass().getName()));
        final List<Candidate> candidates = new ArrayList<>();
        for (Method method : selected) {
            final String descriptor = methodDescriptor(method);
            final List<String> parameterDescriptors = parseParameterDescriptors(descriptor);
            final List<JavaNullabilityAnalyzer.NullabilityState> parameterNullability = resolveParameterNullability(
                    method.getName() + descriptor,
                    parameterDescriptors.size(),
                    parameterNullabilityByMethodKey
            );
            candidates.add(new Candidate(
                    new MemberIdentity(ownerClass.getName(), method.getName(), descriptor, invokeKind),
                    method.isVarArgs(),
                    parameterDescriptors,
                    parameterNullability
            ));
        }
        return List.copyOf(candidates);
    }

    static List<Candidate> candidatesForConstructors(final Class<?> ownerClass) {
        Objects.requireNonNull(ownerClass, "ownerClass");
        final List<Constructor<?>> constructors = new ArrayList<>(List.of(ownerClass.getConstructors()));
        constructors.sort(java.util.Comparator
                .comparing(JavaOverloadResolver::constructorDescriptor)
                .thenComparing(constructor -> constructor.getDeclaringClass().getName()));
        final List<Candidate> candidates = new ArrayList<>();
        for (Constructor<?> constructor : constructors) {
            final String descriptor = constructorDescriptor(constructor);
            final List<String> parameterDescriptors = parseParameterDescriptors(descriptor);
            candidates.add(new Candidate(
                    new MemberIdentity(ownerClass.getName(), "<init>", descriptor, InvokeKind.CONSTRUCTOR),
                    constructor.isVarArgs(),
                    parameterDescriptors,
                    platformNullability(parameterDescriptors.size())
            ));
        }
        return List.copyOf(candidates);
    }

    private static List<JavaNullabilityAnalyzer.NullabilityState> platformNullability(final int parameterCount) {
        final List<JavaNullabilityAnalyzer.NullabilityState> states = new ArrayList<>(parameterCount);
        for (int index = 0; index < parameterCount; index++) {
            states.add(JavaNullabilityAnalyzer.NullabilityState.PLATFORM);
        }
        return List.copyOf(states);
    }

    private static List<JavaNullabilityAnalyzer.NullabilityState> resolveParameterNullability(
            final String methodKey,
            final int parameterCount,
            final Map<String, List<JavaNullabilityAnalyzer.NullabilityState>> parameterNullabilityByMethodKey
    ) {
        if (parameterNullabilityByMethodKey == null) {
            return platformNullability(parameterCount);
        }
        final List<JavaNullabilityAnalyzer.NullabilityState> analyzed =
                parameterNullabilityByMethodKey.get(methodKey);
        if (analyzed == null || analyzed.isEmpty()) {
            return platformNullability(parameterCount);
        }
        final List<JavaNullabilityAnalyzer.NullabilityState> resolved = new ArrayList<>(parameterCount);
        for (int index = 0; index < parameterCount; index++) {
            if (index < analyzed.size()) {
                resolved.add(analyzed.get(index));
            } else {
                resolved.add(JavaNullabilityAnalyzer.NullabilityState.PLATFORM);
            }
        }
        return List.copyOf(resolved);
    }

    private static CandidateOutcome evaluate(
            final Candidate candidate,
            final List<Argument> arguments
    ) {
        final List<String> parameterDescriptors = candidate.parameterDescriptors();
        if (!candidate.varArgs() && arguments.size() != parameterDescriptors.size()) {
            return CandidateOutcome.inapplicable(candidate, "arity mismatch");
        }
        if (candidate.varArgs() && arguments.size() < parameterDescriptors.size() - 1) {
            return CandidateOutcome.inapplicable(candidate, "arity mismatch for varargs");
        }

        int score = 0;
        final int fixedCount = candidate.varArgs() ? parameterDescriptors.size() - 1 : parameterDescriptors.size();
        for (int index = 0; index < fixedCount; index++) {
            final JavaNullabilityAnalyzer.NullabilityState nullability = candidate.parameterNullability().get(index);
            final Conversion conversion = conversionCost(arguments.get(index), parameterDescriptors.get(index), nullability);
            if (!conversion.applicable()) {
                return CandidateOutcome.inapplicable(
                        candidate,
                        "argument " + index + " incompatible with " + parameterDescriptors.get(index)
                                + ": " + conversion.reason()
                );
            }
            score += conversion.cost();
        }

        if (candidate.varArgs()) {
            final String varargArrayDescriptor = parameterDescriptors.getLast();
            if (!varargArrayDescriptor.startsWith("[")) {
                return CandidateOutcome.inapplicable(candidate, "invalid varargs descriptor: " + varargArrayDescriptor);
            }
            final String componentDescriptor = varargArrayDescriptor.substring(1);
            final JavaNullabilityAnalyzer.NullabilityState nullability =
                    candidate.parameterNullability().get(parameterDescriptors.size() - 1);
            for (int argIndex = fixedCount; argIndex < arguments.size(); argIndex++) {
                final Conversion conversion = conversionCost(arguments.get(argIndex), componentDescriptor, nullability);
                if (!conversion.applicable()) {
                    return CandidateOutcome.inapplicable(
                            candidate,
                            "vararg " + (argIndex - fixedCount) + " incompatible with " + componentDescriptor
                                    + ": " + conversion.reason()
                    );
                }
                score += conversion.cost() + 2;
            }
            score += 3;
        }
        return CandidateOutcome.applicable(candidate, score);
    }

    private static Conversion conversionCost(
            final Argument argument,
            final String parameterDescriptor,
            final JavaNullabilityAnalyzer.NullabilityState nullability
    ) {
        if (argument.nullLiteral() || argument.undefinedLiteral()) {
            if (isPrimitiveDescriptor(parameterDescriptor)) {
                return Conversion.inapplicable("null/undefined is incompatible with primitive parameter");
            }
            if (nullability == JavaNullabilityAnalyzer.NullabilityState.NON_NULL) {
                return Conversion.inapplicable("nullability incompatible (NON_NULL parameter)");
            }
            return Conversion.applicable(nullability == JavaNullabilityAnalyzer.NullabilityState.NULLABLE ? 0 : 1);
        }

        final String argumentDescriptor = argument.descriptor();
        if (argumentDescriptor.equals(parameterDescriptor)) {
            return Conversion.applicable(0);
        }

        if (isPrimitiveDescriptor(parameterDescriptor)) {
            return primitiveTargetConversion(argumentDescriptor, parameterDescriptor);
        }

        if (isWrapperDescriptor(parameterDescriptor)) {
            final String targetPrimitive = wrapperToPrimitive(parameterDescriptor);
            if (isPrimitiveDescriptor(argumentDescriptor)) {
                final Conversion primitive = primitiveWidening(argumentDescriptor, targetPrimitive);
                if (!primitive.applicable()) {
                    return primitive;
                }
                return Conversion.applicable(primitive.cost() + 1);
            }
            if (isWrapperDescriptor(argumentDescriptor)) {
                final String sourcePrimitive = wrapperToPrimitive(argumentDescriptor);
                final Conversion primitive = primitiveWidening(sourcePrimitive, targetPrimitive);
                if (!primitive.applicable()) {
                    return primitive;
                }
                return Conversion.applicable(primitive.cost() + 2);
            }
        }

        if (isPrimitiveDescriptor(argumentDescriptor)) {
            final String boxed = primitiveToWrapper(argumentDescriptor);
            if (boxed != null && referenceAssignable(boxed, parameterDescriptor)) {
                return Conversion.applicable(2 + assignabilityDistance(boxed, parameterDescriptor));
            }
            return Conversion.inapplicable("boxing conversion is not assignable");
        }

        if (!isReferenceDescriptor(argumentDescriptor) || !isReferenceDescriptor(parameterDescriptor)) {
            return Conversion.inapplicable("incompatible descriptor kinds");
        }
        if (!referenceAssignable(argumentDescriptor, parameterDescriptor)) {
            return Conversion.inapplicable("reference is not assignable");
        }
        return Conversion.applicable(assignabilityDistance(argumentDescriptor, parameterDescriptor));
    }

    private static Conversion primitiveTargetConversion(
            final String argumentDescriptor,
            final String parameterDescriptor
    ) {
        if (isPrimitiveDescriptor(argumentDescriptor)) {
            return primitiveWidening(argumentDescriptor, parameterDescriptor);
        }
        if (isWrapperDescriptor(argumentDescriptor)) {
            final String unboxed = wrapperToPrimitive(argumentDescriptor);
            final Conversion widened = primitiveWidening(unboxed, parameterDescriptor);
            if (!widened.applicable()) {
                return widened;
            }
            return Conversion.applicable(widened.cost() + 1);
        }
        return Conversion.inapplicable("reference is incompatible with primitive parameter");
    }

    private static Conversion primitiveWidening(final String sourceDescriptor, final String targetDescriptor) {
        if (sourceDescriptor.equals(targetDescriptor)) {
            return Conversion.applicable(0);
        }
        final Integer sourceRank = numericRank(sourceDescriptor);
        final Integer targetRank = numericRank(targetDescriptor);
        if (sourceRank == null || targetRank == null) {
            return Conversion.inapplicable("non-numeric primitive conversion");
        }
        if (sourceRank > targetRank) {
            return Conversion.inapplicable("numeric narrowing is not allowed");
        }
        if (isFloating(sourceDescriptor) && isIntegral(targetDescriptor)) {
            return Conversion.inapplicable("floating to integral narrowing is not allowed");
        }
        return Conversion.applicable(1 + (targetRank - sourceRank));
    }

    private static CandidateOutcome selectSpecificityWinner(
            final List<CandidateOutcome> bestOutcomes,
            final int argumentCount
    ) {
        CandidateOutcome winner = null;
        for (CandidateOutcome candidate : bestOutcomes) {
            boolean dominates = true;
            for (CandidateOutcome other : bestOutcomes) {
                if (candidate == other) {
                    continue;
                }
                if (!moreSpecific(candidate.candidate(), other.candidate(), argumentCount)) {
                    dominates = false;
                    break;
                }
            }
            if (!dominates) {
                continue;
            }
            if (winner != null) {
                return null;
            }
            winner = candidate;
        }
        return winner;
    }

    private static boolean moreSpecific(
            final Candidate left,
            final Candidate right,
            final int argumentCount
    ) {
        final List<String> leftParameters = expandedParameters(left, argumentCount);
        final List<String> rightParameters = expandedParameters(right, argumentCount);
        if (leftParameters.size() != rightParameters.size()) {
            return false;
        }
        boolean strict = false;
        for (int index = 0; index < leftParameters.size(); index++) {
            final String leftType = leftParameters.get(index);
            final String rightType = rightParameters.get(index);
            if (leftType.equals(rightType)) {
                continue;
            }
            if (typeMoreSpecific(leftType, rightType)) {
                strict = true;
                continue;
            }
            return false;
        }
        return strict;
    }

    private static List<String> expandedParameters(final Candidate candidate, final int argumentCount) {
        final List<String> parameters = new ArrayList<>(candidate.parameterDescriptors());
        if (!candidate.varArgs()) {
            return parameters;
        }
        final int fixedCount = parameters.size() - 1;
        final String arrayDescriptor = parameters.removeLast();
        final String component = arrayDescriptor.substring(1);
        for (int index = fixedCount; index < argumentCount; index++) {
            parameters.add(component);
        }
        return parameters;
    }

    private static boolean typeMoreSpecific(final String leftType, final String rightType) {
        if (isPrimitiveDescriptor(leftType) && isPrimitiveDescriptor(rightType)) {
            final Integer leftRank = numericRank(leftType);
            final Integer rightRank = numericRank(rightType);
            if (leftRank == null || rightRank == null) {
                return false;
            }
            return leftRank < rightRank;
        }
        if (isReferenceDescriptor(leftType) && isReferenceDescriptor(rightType)) {
            return referenceAssignable(leftType, rightType) && !referenceAssignable(rightType, leftType);
        }
        if (isPrimitiveDescriptor(leftType) && isWrapperDescriptor(rightType)) {
            final String unboxed = wrapperToPrimitive(rightType);
            return leftType.equals(unboxed);
        }
        return false;
    }

    private static String noApplicableDiagnostic(
            final List<Argument> arguments,
            final List<CandidateOutcome> outcomes
    ) {
        final List<String> lines = new ArrayList<>();
        for (CandidateOutcome outcome : outcomes) {
            lines.add(identitySummary(outcome.candidate().identity()) + " -> " + outcome.reason());
        }
        return "No applicable candidate for arguments "
                + argumentSummary(arguments)
                + "; candidates: "
                + String.join("; ", lines);
    }

    private static String ambiguousDiagnostic(
            final List<Argument> arguments,
            final List<CandidateOutcome> bestOutcomes
    ) {
        final List<String> candidates = new ArrayList<>();
        for (CandidateOutcome outcome : bestOutcomes) {
            candidates.add(identitySummary(outcome.candidate().identity()) + " score=" + outcome.score());
        }
        return "Ambiguous best candidates for arguments "
                + argumentSummary(arguments)
                + ": "
                + String.join(", ", candidates);
    }

    private static String argumentSummary(final List<Argument> arguments) {
        if (arguments.isEmpty()) {
            return "[]";
        }
        final List<String> descriptors = new ArrayList<>();
        for (Argument argument : arguments) {
            descriptors.add(argument.displayDescriptor());
        }
        return "[" + String.join(", ", descriptors) + "]";
    }

    private static String identitySummary(final MemberIdentity identity) {
        return identity.owner() + "#" + identity.name() + identity.descriptor() + "@" + identity.invokeKind();
    }

    private static boolean isPrimitiveDescriptor(final String descriptor) {
        return descriptor.length() == 1 && "BCDFIJSZV".contains(descriptor);
    }

    private static boolean isReferenceDescriptor(final String descriptor) {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    private static boolean isWrapperDescriptor(final String descriptor) {
        return switch (descriptor) {
            case "Ljava/lang/Byte;",
                    "Ljava/lang/Short;",
                    "Ljava/lang/Integer;",
                    "Ljava/lang/Long;",
                    "Ljava/lang/Float;",
                    "Ljava/lang/Double;",
                    "Ljava/lang/Boolean;",
                    "Ljava/lang/Character;" -> true;
            default -> false;
        };
    }

    private static String primitiveToWrapper(final String primitiveDescriptor) {
        return switch (primitiveDescriptor) {
            case "B" -> "Ljava/lang/Byte;";
            case "S" -> "Ljava/lang/Short;";
            case "I" -> "Ljava/lang/Integer;";
            case "J" -> "Ljava/lang/Long;";
            case "F" -> "Ljava/lang/Float;";
            case "D" -> "Ljava/lang/Double;";
            case "Z" -> "Ljava/lang/Boolean;";
            case "C" -> "Ljava/lang/Character;";
            default -> null;
        };
    }

    private static String wrapperToPrimitive(final String wrapperDescriptor) {
        return switch (wrapperDescriptor) {
            case "Ljava/lang/Byte;" -> "B";
            case "Ljava/lang/Short;" -> "S";
            case "Ljava/lang/Integer;" -> "I";
            case "Ljava/lang/Long;" -> "J";
            case "Ljava/lang/Float;" -> "F";
            case "Ljava/lang/Double;" -> "D";
            case "Ljava/lang/Boolean;" -> "Z";
            case "Ljava/lang/Character;" -> "C";
            default -> null;
        };
    }

    private static Integer numericRank(final String primitiveDescriptor) {
        return switch (primitiveDescriptor) {
            case "B" -> 0;
            case "S" -> 1;
            case "C" -> 1;
            case "I" -> 2;
            case "J" -> 3;
            case "F" -> 4;
            case "D" -> 5;
            default -> null;
        };
    }

    private static boolean isFloating(final String primitiveDescriptor) {
        return "F".equals(primitiveDescriptor) || "D".equals(primitiveDescriptor);
    }

    private static boolean isIntegral(final String primitiveDescriptor) {
        return "B".equals(primitiveDescriptor)
                || "S".equals(primitiveDescriptor)
                || "C".equals(primitiveDescriptor)
                || "I".equals(primitiveDescriptor)
                || "J".equals(primitiveDescriptor);
    }

    private static boolean referenceAssignable(final String fromDescriptor, final String toDescriptor) {
        if (fromDescriptor.equals(toDescriptor)) {
            return true;
        }
        if ("Ljava/lang/Object;".equals(toDescriptor)) {
            return true;
        }
        if (fromDescriptor.startsWith("[") && (
                "Ljava/lang/Object;".equals(toDescriptor)
                        || "Ljava/lang/Cloneable;".equals(toDescriptor)
                        || "Ljava/io/Serializable;".equals(toDescriptor)
        )) {
            return true;
        }
        final Class<?> fromClass = loadDescriptorClass(fromDescriptor);
        final Class<?> toClass = loadDescriptorClass(toDescriptor);
        if (fromClass == null || toClass == null) {
            return false;
        }
        return toClass.isAssignableFrom(fromClass);
    }

    private static int assignabilityDistance(final String fromDescriptor, final String toDescriptor) {
        if (fromDescriptor.equals(toDescriptor)) {
            return 0;
        }
        if ("Ljava/lang/Object;".equals(toDescriptor)) {
            return 8;
        }
        final Class<?> fromClass = loadDescriptorClass(fromDescriptor);
        final Class<?> toClass = loadDescriptorClass(toDescriptor);
        if (fromClass == null || toClass == null) {
            return 6;
        }
        final Deque<ClassDistance> queue = new ArrayDeque<>();
        final Set<Class<?>> visited = new LinkedHashSet<>();
        queue.addLast(new ClassDistance(fromClass, 0));
        while (!queue.isEmpty()) {
            final ClassDistance distance = queue.removeFirst();
            if (!visited.add(distance.type())) {
                continue;
            }
            if (distance.type().equals(toClass)) {
                return distance.distance();
            }
            final Class<?> superclass = distance.type().getSuperclass();
            if (superclass != null) {
                queue.addLast(new ClassDistance(superclass, distance.distance() + 1));
            }
            for (Class<?> interfaceType : distance.type().getInterfaces()) {
                queue.addLast(new ClassDistance(interfaceType, distance.distance() + 1));
            }
        }
        return 6;
    }

    private static Class<?> loadDescriptorClass(final String descriptor) {
        try {
            if (descriptor.startsWith("L")) {
                final String className = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                return Class.forName(className, false, JavaOverloadResolver.class.getClassLoader());
            }
            if (descriptor.startsWith("[")) {
                final String className = descriptor.replace('/', '.');
                return Class.forName(className, false, JavaOverloadResolver.class.getClassLoader());
            }
            return switch (descriptor) {
                case "Z" -> boolean.class;
                case "B" -> byte.class;
                case "S" -> short.class;
                case "C" -> char.class;
                case "I" -> int.class;
                case "J" -> long.class;
                case "F" -> float.class;
                case "D" -> double.class;
                case "V" -> void.class;
                default -> null;
            };
        } catch (final ClassNotFoundException classNotFoundException) {
            return null;
        }
    }

    static List<String> parseParameterDescriptors(final String methodDescriptor) {
        final List<String> parameters = new ArrayList<>();
        final int start = methodDescriptor.indexOf('(');
        final int end = methodDescriptor.indexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            return List.of();
        }
        int index = start + 1;
        while (index < end) {
            final int begin = index;
            char marker = methodDescriptor.charAt(index);
            while (marker == '[') {
                index++;
                marker = methodDescriptor.charAt(index);
            }
            if (marker == 'L') {
                final int semicolon = methodDescriptor.indexOf(';', index);
                if (semicolon < 0 || semicolon > end) {
                    break;
                }
                index = semicolon + 1;
            } else {
                index++;
            }
            parameters.add(methodDescriptor.substring(begin, index));
        }
        return List.copyOf(parameters);
    }

    static String constructorDescriptor(final Constructor<?> constructor) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            builder.append(descriptorFor(parameterType));
        }
        builder.append(")V");
        return builder.toString();
    }

    static String methodDescriptor(final Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(descriptorFor(parameterType));
        }
        builder.append(")");
        builder.append(descriptorFor(method.getReturnType()));
        return builder.toString();
    }

    static String descriptorFor(final Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) {
                return "V";
            }
            if (type == boolean.class) {
                return "Z";
            }
            if (type == byte.class) {
                return "B";
            }
            if (type == short.class) {
                return "S";
            }
            if (type == char.class) {
                return "C";
            }
            if (type == int.class) {
                return "I";
            }
            if (type == long.class) {
                return "J";
            }
            if (type == float.class) {
                return "F";
            }
            return "D";
        }
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    enum InvokeKind {
        CONSTRUCTOR,
        STATIC_METHOD,
        INSTANCE_METHOD,
        STATIC_FIELD_GET,
        STATIC_FIELD_SET,
        INSTANCE_FIELD_GET,
        INSTANCE_FIELD_SET
    }

    enum Status {
        SELECTED,
        NO_APPLICABLE,
        AMBIGUOUS
    }

    record MemberIdentity(
            String owner,
            String name,
            String descriptor,
            InvokeKind invokeKind
    ) {
        MemberIdentity {
            owner = Objects.requireNonNull(owner, "owner");
            name = Objects.requireNonNull(name, "name");
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            invokeKind = Objects.requireNonNull(invokeKind, "invokeKind");
        }
    }

    record Candidate(
            MemberIdentity identity,
            boolean varArgs,
            List<String> parameterDescriptors,
            List<JavaNullabilityAnalyzer.NullabilityState> parameterNullability
    ) {
        Candidate {
            identity = Objects.requireNonNull(identity, "identity");
            parameterDescriptors = List.copyOf(Objects.requireNonNull(parameterDescriptors, "parameterDescriptors"));
            parameterNullability = List.copyOf(Objects.requireNonNull(parameterNullability, "parameterNullability"));
            if (parameterDescriptors.size() != parameterNullability.size()) {
                throw new IllegalArgumentException("parameterDescriptors and parameterNullability must align.");
            }
        }
    }

    record Argument(
            String descriptor,
            boolean nullLiteral,
            boolean undefinedLiteral
    ) {
        Argument {
            if (!nullLiteral && !undefinedLiteral) {
                descriptor = Objects.requireNonNull(descriptor, "descriptor");
            }
        }

        static Argument descriptor(final String descriptor) {
            return new Argument(Objects.requireNonNull(descriptor, "descriptor"), false, false);
        }

        static Argument nullArgument() {
            return new Argument(null, true, false);
        }

        static Argument undefinedArgument() {
            return new Argument(null, false, true);
        }

        String displayDescriptor() {
            if (nullLiteral) {
                return "null";
            }
            if (undefinedLiteral) {
                return "undefined";
            }
            return descriptor;
        }
    }

    record CandidateOutcome(
            Candidate candidate,
            boolean applicable,
            int score,
            String reason
    ) {
        private static CandidateOutcome applicable(final Candidate candidate, final int score) {
            return new CandidateOutcome(candidate, true, score, "");
        }

        private static CandidateOutcome inapplicable(final Candidate candidate, final String reason) {
            return new CandidateOutcome(candidate, false, Integer.MAX_VALUE, reason);
        }
    }

    record Resolution(
            Status status,
            MemberIdentity selected,
            String diagnostic,
            List<CandidateOutcome> outcomes,
            List<MemberIdentity> bestCandidates
    ) {
    }

    private record Conversion(boolean applicable, int cost, String reason) {
        private static Conversion applicable(final int cost) {
            return new Conversion(true, cost, "");
        }

        private static Conversion inapplicable(final String reason) {
            return new Conversion(false, Integer.MAX_VALUE, reason);
        }
    }

    private record ClassDistance(Class<?> type, int distance) {
    }
}
