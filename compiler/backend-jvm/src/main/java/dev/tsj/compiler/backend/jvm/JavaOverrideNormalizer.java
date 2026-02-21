package dev.tsj.compiler.backend.jvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaOverrideNormalizer {
    NormalizationResult normalizeMethods(final List<JavaInheritanceResolver.ResolvedMember> members) {
        final List<JavaInheritanceResolver.ResolvedMember> methodCandidates = members.stream()
                .filter(member -> member.kind() == JavaInheritanceResolver.MemberKind.METHOD)
                .toList();
        final Map<OwnerMethodArityKey, String> bridgeOverrideKeys = buildBridgeOverrideKeys(methodCandidates);

        final Map<String, MethodAccumulator> normalizedByOverrideKey = new LinkedHashMap<>();
        for (JavaInheritanceResolver.ResolvedMember method : methodCandidates) {
            final String overrideKey = canonicalOverrideKey(method, bridgeOverrideKeys);
            final MethodAccumulator accumulator = normalizedByOverrideKey.get(overrideKey);
            if (accumulator == null) {
                normalizedByOverrideKey.put(
                        overrideKey,
                        new MethodAccumulator(method, overrideKey, new LinkedHashSet<>())
                );
                continue;
            }
            if (prefer(method, accumulator.selectedMethod())) {
                final Set<String> overridden = new LinkedHashSet<>(accumulator.overriddenDescriptors());
                if (!accumulator.selectedMethod().descriptor().equals(method.descriptor())) {
                    overridden.add(accumulator.selectedMethod().descriptor());
                }
                normalizedByOverrideKey.put(
                        overrideKey,
                        new MethodAccumulator(method, overrideKey, overridden)
                );
            } else if (!accumulator.selectedMethod().descriptor().equals(method.descriptor())) {
                final Set<String> overridden = new LinkedHashSet<>(accumulator.overriddenDescriptors());
                overridden.add(method.descriptor());
                normalizedByOverrideKey.put(
                        overrideKey,
                        new MethodAccumulator(accumulator.selectedMethod(), overrideKey, overridden)
                );
            }
        }

        final List<NormalizedMethod> normalized = new ArrayList<>();
        for (MethodAccumulator accumulator : normalizedByOverrideKey.values()) {
            final JavaInheritanceResolver.ResolvedMember selected = accumulator.selectedMethod();
            normalized.add(new NormalizedMethod(
                    selected.ownerInternalName(),
                    selected.name(),
                    selected.descriptor(),
                    accumulator.overrideKey(),
                    isBridge(selected.accessFlags()),
                    isSynthetic(selected.accessFlags()),
                    List.copyOf(accumulator.overriddenDescriptors())
            ));
        }
        return new NormalizationResult(List.copyOf(methodCandidates), List.copyOf(normalized));
    }

    private static Map<OwnerMethodArityKey, String> buildBridgeOverrideKeys(
            final List<JavaInheritanceResolver.ResolvedMember> methodCandidates
    ) {
        final Map<OwnerMethodArityKey, String> bridgeOverrideKeys = new LinkedHashMap<>();
        for (JavaInheritanceResolver.ResolvedMember method : methodCandidates) {
            if (!isBridge(method.accessFlags())) {
                continue;
            }
            final String overrideKey = buildOverrideKey(method.name(), method.descriptor());
            final int arity = descriptorArity(method.descriptor());
            bridgeOverrideKeys.putIfAbsent(
                    new OwnerMethodArityKey(method.ownerInternalName(), method.name(), arity),
                    overrideKey
            );
        }
        return bridgeOverrideKeys;
    }

    private static String canonicalOverrideKey(
            final JavaInheritanceResolver.ResolvedMember method,
            final Map<OwnerMethodArityKey, String> bridgeOverrideKeys
    ) {
        final int arity = descriptorArity(method.descriptor());
        final OwnerMethodArityKey ownerMethodArityKey =
                new OwnerMethodArityKey(method.ownerInternalName(), method.name(), arity);
        if (!isBridge(method.accessFlags()) && bridgeOverrideKeys.containsKey(ownerMethodArityKey)) {
            return bridgeOverrideKeys.get(ownerMethodArityKey);
        }
        return buildOverrideKey(method.name(), method.descriptor());
    }

    private static boolean prefer(
            final JavaInheritanceResolver.ResolvedMember candidate,
            final JavaInheritanceResolver.ResolvedMember selected
    ) {
        final int candidateScore = methodSpecificityScore(candidate);
        final int selectedScore = methodSpecificityScore(selected);
        if (candidateScore < selectedScore) {
            return true;
        }
        if (candidateScore > selectedScore) {
            return false;
        }
        return false;
    }

    private static int methodSpecificityScore(final JavaInheritanceResolver.ResolvedMember method) {
        int score = 0;
        if (isBridge(method.accessFlags())) {
            score += 4;
        }
        if (isSynthetic(method.accessFlags())) {
            score += 2;
        }
        if (method.inherited()) {
            score += 1;
        }
        return score;
    }

    private static String buildOverrideKey(final String methodName, final String descriptor) {
        final int endParameters = descriptor.indexOf(')');
        if (endParameters < 0) {
            return methodName + descriptor;
        }
        return methodName + descriptor.substring(0, endParameters + 1);
    }

    private static int descriptorArity(final String descriptor) {
        final int start = descriptor.indexOf('(');
        final int end = descriptor.indexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            return 0;
        }
        int index = start + 1;
        int count = 0;
        while (index < end) {
            char marker = descriptor.charAt(index);
            while (marker == '[') {
                index++;
                marker = descriptor.charAt(index);
            }
            if (marker == 'L') {
                index = descriptor.indexOf(';', index);
                if (index < 0 || index > end) {
                    return count;
                }
            }
            count++;
            index++;
        }
        return count;
    }

    private static boolean isBridge(final int accessFlags) {
        return (accessFlags & 0x0040) != 0;
    }

    private static boolean isSynthetic(final int accessFlags) {
        return (accessFlags & 0x1000) != 0;
    }

    record NormalizationResult(
            List<JavaInheritanceResolver.ResolvedMember> candidates,
            List<NormalizedMethod> normalizedMethods
    ) {
    }

    record NormalizedMethod(
            String ownerInternalName,
            String name,
            String descriptor,
            String overrideKey,
            boolean bridge,
            boolean synthetic,
            List<String> overriddenDescriptors
    ) {
    }

    private record OwnerMethodArityKey(String ownerInternalName, String name, int arity) {
    }

    private record MethodAccumulator(
            JavaInheritanceResolver.ResolvedMember selectedMethod,
            String overrideKey,
            Set<String> overriddenDescriptors
    ) {
    }
}
