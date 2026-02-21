package dev.tsj.compiler.backend.jvm;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JavaClassfileReader {
    private static final int CLASSFILE_MAGIC = 0xCAFEBABE;

    RawClassInfo read(final Path classFile) throws IOException {
        return read(Files.readAllBytes(classFile), classFile);
    }

    RawClassInfo read(final byte[] classBytes, final Path sourcePath) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            final int magic = input.readInt();
            if (magic != CLASSFILE_MAGIC) {
                throw new IOException("Invalid classfile magic: " + Integer.toHexString(magic));
            }
            final int minorVersion = input.readUnsignedShort();
            final int majorVersion = input.readUnsignedShort();
            final ConstantPool constantPool = ConstantPool.read(input);
            final int accessFlags = input.readUnsignedShort();
            final int thisClassIndex = input.readUnsignedShort();
            final int superClassIndex = input.readUnsignedShort();
            final int interfacesCount = input.readUnsignedShort();
            final List<String> interfaces = new ArrayList<>();
            for (int index = 0; index < interfacesCount; index++) {
                interfaces.add(constantPool.className(input.readUnsignedShort()));
            }

            final int fieldsCount = input.readUnsignedShort();
            final List<RawFieldInfo> fields = new ArrayList<>();
            for (int index = 0; index < fieldsCount; index++) {
                fields.add(readFieldInfo(input, constantPool));
            }

            final int methodsCount = input.readUnsignedShort();
            final List<RawMethodInfo> methods = new ArrayList<>();
            for (int index = 0; index < methodsCount; index++) {
                methods.add(readMethodInfo(input, constantPool));
            }

            String signature = null;
            final List<RawAnnotationInfo> visibleAnnotations = new ArrayList<>();
            final List<RawAnnotationInfo> invisibleAnnotations = new ArrayList<>();
            final List<RawTypeAnnotationInfo> visibleTypeAnnotations = new ArrayList<>();
            final List<RawTypeAnnotationInfo> invisibleTypeAnnotations = new ArrayList<>();
            final List<RawInnerClassInfo> innerClasses = new ArrayList<>();
            RawEnclosingMethodInfo enclosingMethod = null;
            String nestHost = null;
            final List<String> nestMembers = new ArrayList<>();
            final List<RawRecordComponentInfo> recordComponents = new ArrayList<>();
            final List<String> permittedSubclasses = new ArrayList<>();

            final int attributesCount = input.readUnsignedShort();
            for (int index = 0; index < attributesCount; index++) {
                final String attributeName = constantPool.utf8(input.readUnsignedShort());
                final int attributeLength = input.readInt();
                if ("Signature".equals(attributeName)) {
                    signature = constantPool.utf8(input.readUnsignedShort());
                    continue;
                }
                if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                    visibleAnnotations.addAll(readAnnotations(input, constantPool));
                    continue;
                }
                if ("RuntimeInvisibleAnnotations".equals(attributeName)) {
                    invisibleAnnotations.addAll(readAnnotations(input, constantPool));
                    continue;
                }
                if ("RuntimeVisibleTypeAnnotations".equals(attributeName)) {
                    visibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                    continue;
                }
                if ("RuntimeInvisibleTypeAnnotations".equals(attributeName)) {
                    invisibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                    continue;
                }
                if ("InnerClasses".equals(attributeName)) {
                    innerClasses.addAll(readInnerClasses(input, constantPool));
                    continue;
                }
                if ("EnclosingMethod".equals(attributeName)) {
                    enclosingMethod = readEnclosingMethod(input, constantPool);
                    continue;
                }
                if ("NestHost".equals(attributeName)) {
                    nestHost = constantPool.className(input.readUnsignedShort());
                    continue;
                }
                if ("NestMembers".equals(attributeName)) {
                    final int count = input.readUnsignedShort();
                    for (int memberIndex = 0; memberIndex < count; memberIndex++) {
                        nestMembers.add(constantPool.className(input.readUnsignedShort()));
                    }
                    continue;
                }
                if ("Record".equals(attributeName)) {
                    recordComponents.addAll(readRecordComponents(input, constantPool));
                    continue;
                }
                if ("PermittedSubclasses".equals(attributeName)) {
                    final int count = input.readUnsignedShort();
                    for (int classIndex = 0; classIndex < count; classIndex++) {
                        permittedSubclasses.add(constantPool.className(input.readUnsignedShort()));
                    }
                    continue;
                }
                skipFully(input, attributeLength);
            }

            return new RawClassInfo(
                    sourcePath.toAbsolutePath().normalize(),
                    minorVersion,
                    majorVersion,
                    accessFlags,
                    constantPool.className(thisClassIndex),
                    superClassIndex == 0 ? null : constantPool.className(superClassIndex),
                    List.copyOf(interfaces),
                    signature,
                    List.copyOf(visibleAnnotations),
                    List.copyOf(invisibleAnnotations),
                    List.copyOf(visibleTypeAnnotations),
                    List.copyOf(invisibleTypeAnnotations),
                    List.copyOf(fields),
                    List.copyOf(methods),
                    List.copyOf(innerClasses),
                    enclosingMethod,
                    nestHost,
                    List.copyOf(nestMembers),
                    List.copyOf(recordComponents),
                    List.copyOf(permittedSubclasses)
            );
        }
    }

    private static RawFieldInfo readFieldInfo(final DataInputStream input, final ConstantPool constantPool)
            throws IOException {
        final int accessFlags = input.readUnsignedShort();
        final String name = constantPool.utf8(input.readUnsignedShort());
        final String descriptor = constantPool.utf8(input.readUnsignedShort());
        String signature = null;
        final List<RawAnnotationInfo> visibleAnnotations = new ArrayList<>();
        final List<RawAnnotationInfo> invisibleAnnotations = new ArrayList<>();
        final List<RawTypeAnnotationInfo> visibleTypeAnnotations = new ArrayList<>();
        final List<RawTypeAnnotationInfo> invisibleTypeAnnotations = new ArrayList<>();

        final int attributesCount = input.readUnsignedShort();
        for (int index = 0; index < attributesCount; index++) {
            final String attributeName = constantPool.utf8(input.readUnsignedShort());
            final int attributeLength = input.readInt();
            if ("Signature".equals(attributeName)) {
                signature = constantPool.utf8(input.readUnsignedShort());
                continue;
            }
            if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                visibleAnnotations.addAll(readAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeInvisibleAnnotations".equals(attributeName)) {
                invisibleAnnotations.addAll(readAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeVisibleTypeAnnotations".equals(attributeName)) {
                visibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeInvisibleTypeAnnotations".equals(attributeName)) {
                invisibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                continue;
            }
            skipFully(input, attributeLength);
        }
        return new RawFieldInfo(
                name,
                descriptor,
                accessFlags,
                signature,
                List.copyOf(visibleAnnotations),
                List.copyOf(invisibleAnnotations),
                List.copyOf(visibleTypeAnnotations),
                List.copyOf(invisibleTypeAnnotations)
        );
    }

    private static RawMethodInfo readMethodInfo(final DataInputStream input, final ConstantPool constantPool)
            throws IOException {
        final int accessFlags = input.readUnsignedShort();
        final String name = constantPool.utf8(input.readUnsignedShort());
        final String descriptor = constantPool.utf8(input.readUnsignedShort());
        String signature = null;
        String annotationDefault = null;
        final List<String> exceptions = new ArrayList<>();
        final List<RawMethodParameterInfo> parameters = new ArrayList<>();
        final List<RawAnnotationInfo> visibleAnnotations = new ArrayList<>();
        final List<RawAnnotationInfo> invisibleAnnotations = new ArrayList<>();
        final List<RawTypeAnnotationInfo> visibleTypeAnnotations = new ArrayList<>();
        final List<RawTypeAnnotationInfo> invisibleTypeAnnotations = new ArrayList<>();
        final List<List<RawAnnotationInfo>> visibleParameterAnnotations = new ArrayList<>();
        final List<List<RawAnnotationInfo>> invisibleParameterAnnotations = new ArrayList<>();

        final int attributesCount = input.readUnsignedShort();
        for (int index = 0; index < attributesCount; index++) {
            final String attributeName = constantPool.utf8(input.readUnsignedShort());
            final int attributeLength = input.readInt();
            if ("Signature".equals(attributeName)) {
                signature = constantPool.utf8(input.readUnsignedShort());
                continue;
            }
            if ("Exceptions".equals(attributeName)) {
                final int count = input.readUnsignedShort();
                for (int exceptionIndex = 0; exceptionIndex < count; exceptionIndex++) {
                    exceptions.add(constantPool.className(input.readUnsignedShort()));
                }
                continue;
            }
            if ("MethodParameters".equals(attributeName)) {
                parameters.addAll(readMethodParameters(input, constantPool));
                continue;
            }
            if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                visibleAnnotations.addAll(readAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeInvisibleAnnotations".equals(attributeName)) {
                invisibleAnnotations.addAll(readAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeVisibleTypeAnnotations".equals(attributeName)) {
                visibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeInvisibleTypeAnnotations".equals(attributeName)) {
                invisibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeVisibleParameterAnnotations".equals(attributeName)) {
                visibleParameterAnnotations.addAll(readParameterAnnotations(input, constantPool));
                continue;
            }
            if ("RuntimeInvisibleParameterAnnotations".equals(attributeName)) {
                invisibleParameterAnnotations.addAll(readParameterAnnotations(input, constantPool));
                continue;
            }
            if ("AnnotationDefault".equals(attributeName)) {
                annotationDefault = readElementValue(input, constantPool);
                continue;
            }
            skipFully(input, attributeLength);
        }

        return new RawMethodInfo(
                name,
                descriptor,
                accessFlags,
                signature,
                List.copyOf(exceptions),
                List.copyOf(parameters),
                List.copyOf(visibleAnnotations),
                List.copyOf(invisibleAnnotations),
                List.copyOf(visibleTypeAnnotations),
                List.copyOf(invisibleTypeAnnotations),
                copyNestedAnnotationList(visibleParameterAnnotations),
                copyNestedAnnotationList(invisibleParameterAnnotations),
                annotationDefault
        );
    }

    private static List<List<RawAnnotationInfo>> copyNestedAnnotationList(
            final List<List<RawAnnotationInfo>> annotations
    ) {
        final List<List<RawAnnotationInfo>> copy = new ArrayList<>(annotations.size());
        for (List<RawAnnotationInfo> parameterAnnotations : annotations) {
            copy.add(List.copyOf(parameterAnnotations));
        }
        return List.copyOf(copy);
    }

    private static List<RawMethodParameterInfo> readMethodParameters(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int count = input.readUnsignedByte();
        final List<RawMethodParameterInfo> parameters = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            final int nameIndex = input.readUnsignedShort();
            final int accessFlags = input.readUnsignedShort();
            parameters.add(new RawMethodParameterInfo(
                    nameIndex == 0 ? null : constantPool.utf8(nameIndex),
                    accessFlags
            ));
        }
        return parameters;
    }

    private static List<List<RawAnnotationInfo>> readParameterAnnotations(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int parameterCount = input.readUnsignedByte();
        final List<List<RawAnnotationInfo>> annotations = new ArrayList<>(parameterCount);
        for (int parameterIndex = 0; parameterIndex < parameterCount; parameterIndex++) {
            final int annotationCount = input.readUnsignedShort();
            final List<RawAnnotationInfo> parameterAnnotations = new ArrayList<>(annotationCount);
            for (int annotationIndex = 0; annotationIndex < annotationCount; annotationIndex++) {
                parameterAnnotations.add(readAnnotation(input, constantPool));
            }
            annotations.add(parameterAnnotations);
        }
        return annotations;
    }

    private static List<RawAnnotationInfo> readAnnotations(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int annotationCount = input.readUnsignedShort();
        final List<RawAnnotationInfo> annotations = new ArrayList<>(annotationCount);
        for (int index = 0; index < annotationCount; index++) {
            annotations.add(readAnnotation(input, constantPool));
        }
        return annotations;
    }

    private static List<RawTypeAnnotationInfo> readTypeAnnotations(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int annotationCount = input.readUnsignedShort();
        final List<RawTypeAnnotationInfo> annotations = new ArrayList<>(annotationCount);
        for (int index = 0; index < annotationCount; index++) {
            final int targetType = input.readUnsignedByte();
            skipTypeAnnotationTarget(input, targetType);
            final int typePathLength = input.readUnsignedByte();
            for (int pathIndex = 0; pathIndex < typePathLength; pathIndex++) {
                input.readUnsignedByte();
                input.readUnsignedByte();
            }
            final RawAnnotationInfo annotation = readAnnotation(input, constantPool);
            annotations.add(new RawTypeAnnotationInfo(targetType, annotation));
        }
        return annotations;
    }

    private static void skipTypeAnnotationTarget(final DataInputStream input, final int targetType)
            throws IOException {
        switch (targetType) {
            case 0x00, 0x01 -> input.readUnsignedByte();
            case 0x10 -> input.readUnsignedShort();
            case 0x11, 0x12 -> {
                input.readUnsignedByte();
                input.readUnsignedByte();
            }
            case 0x13, 0x14, 0x15 -> {
                // empty_target
            }
            case 0x16 -> input.readUnsignedByte();
            case 0x17 -> input.readUnsignedShort();
            case 0x40, 0x41 -> {
                final int tableLength = input.readUnsignedShort();
                for (int index = 0; index < tableLength; index++) {
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                }
            }
            case 0x42 -> input.readUnsignedShort();
            case 0x43, 0x44, 0x45, 0x46 -> input.readUnsignedShort();
            case 0x47, 0x48, 0x49, 0x4A, 0x4B -> {
                input.readUnsignedShort();
                input.readUnsignedByte();
            }
            default -> throw new IOException("Unknown type annotation target type: " + targetType);
        }
    }

    private static RawAnnotationInfo readAnnotation(final DataInputStream input, final ConstantPool constantPool)
            throws IOException {
        final String descriptor = constantPool.utf8(input.readUnsignedShort());
        final int pairCount = input.readUnsignedShort();
        final Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < pairCount; index++) {
            final String key = constantPool.utf8(input.readUnsignedShort());
            values.put(key, readElementValue(input, constantPool));
        }
        return new RawAnnotationInfo(descriptor, Map.copyOf(values));
    }

    private static String readElementValue(final DataInputStream input, final ConstantPool constantPool)
            throws IOException {
        final char tag = (char) input.readUnsignedByte();
        return switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's' -> constantPool.constantToString(input.readUnsignedShort());
            case 'e' -> {
                final String enumType = constantPool.utf8(input.readUnsignedShort());
                final String enumName = constantPool.utf8(input.readUnsignedShort());
                yield enumType + "." + enumName;
            }
            case 'c' -> constantPool.utf8(input.readUnsignedShort());
            case '@' -> "@" + readAnnotation(input, constantPool).descriptor();
            case '[' -> {
                final int count = input.readUnsignedShort();
                final List<String> values = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    values.add(readElementValue(input, constantPool));
                }
                yield values.toString();
            }
            default -> throw new IOException("Unknown annotation element tag: " + tag);
        };
    }

    private static List<RawInnerClassInfo> readInnerClasses(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int count = input.readUnsignedShort();
        final List<RawInnerClassInfo> innerClasses = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            final int innerClassIndex = input.readUnsignedShort();
            final int outerClassIndex = input.readUnsignedShort();
            final int innerNameIndex = input.readUnsignedShort();
            final int accessFlags = input.readUnsignedShort();
            innerClasses.add(new RawInnerClassInfo(
                    innerClassIndex == 0 ? null : constantPool.className(innerClassIndex),
                    outerClassIndex == 0 ? null : constantPool.className(outerClassIndex),
                    innerNameIndex == 0 ? null : constantPool.utf8(innerNameIndex),
                    accessFlags
            ));
        }
        return innerClasses;
    }

    private static RawEnclosingMethodInfo readEnclosingMethod(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int classIndex = input.readUnsignedShort();
        final int methodIndex = input.readUnsignedShort();
        String methodName = null;
        String methodDescriptor = null;
        if (methodIndex != 0) {
            final NameAndType nameAndType = constantPool.nameAndType(methodIndex);
            methodName = nameAndType.name();
            methodDescriptor = nameAndType.descriptor();
        }
        return new RawEnclosingMethodInfo(constantPool.className(classIndex), methodName, methodDescriptor);
    }

    private static List<RawRecordComponentInfo> readRecordComponents(
            final DataInputStream input,
            final ConstantPool constantPool
    ) throws IOException {
        final int count = input.readUnsignedShort();
        final List<RawRecordComponentInfo> components = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            final String name = constantPool.utf8(input.readUnsignedShort());
            final String descriptor = constantPool.utf8(input.readUnsignedShort());
            String signature = null;
            final List<RawAnnotationInfo> visibleAnnotations = new ArrayList<>();
            final List<RawAnnotationInfo> invisibleAnnotations = new ArrayList<>();
            final List<RawTypeAnnotationInfo> visibleTypeAnnotations = new ArrayList<>();
            final List<RawTypeAnnotationInfo> invisibleTypeAnnotations = new ArrayList<>();
            final int attributesCount = input.readUnsignedShort();
            for (int componentAttribute = 0; componentAttribute < attributesCount; componentAttribute++) {
                final String attributeName = constantPool.utf8(input.readUnsignedShort());
                final int attributeLength = input.readInt();
                if ("Signature".equals(attributeName)) {
                    signature = constantPool.utf8(input.readUnsignedShort());
                    continue;
                }
                if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                    visibleAnnotations.addAll(readAnnotations(input, constantPool));
                    continue;
                }
                if ("RuntimeInvisibleAnnotations".equals(attributeName)) {
                    invisibleAnnotations.addAll(readAnnotations(input, constantPool));
                    continue;
                }
                if ("RuntimeVisibleTypeAnnotations".equals(attributeName)) {
                    visibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                    continue;
                }
                if ("RuntimeInvisibleTypeAnnotations".equals(attributeName)) {
                    invisibleTypeAnnotations.addAll(readTypeAnnotations(input, constantPool));
                    continue;
                }
                skipFully(input, attributeLength);
            }
            components.add(new RawRecordComponentInfo(
                    name,
                    descriptor,
                    signature,
                    List.copyOf(visibleAnnotations),
                    List.copyOf(invisibleAnnotations),
                    List.copyOf(visibleTypeAnnotations),
                    List.copyOf(invisibleTypeAnnotations)
            ));
        }
        return components;
    }

    private static void skipFully(final DataInputStream input, final int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            final int skipped = input.skipBytes(remaining);
            if (skipped <= 0) {
                throw new IOException("Unable to skip classfile attribute bytes.");
            }
            remaining -= skipped;
        }
    }

    record RawClassInfo(
            Path sourcePath,
            int minorVersion,
            int majorVersion,
            int accessFlags,
            String internalName,
            String superInternalName,
            List<String> interfaces,
            String signature,
            List<RawAnnotationInfo> runtimeVisibleAnnotations,
            List<RawAnnotationInfo> runtimeInvisibleAnnotations,
            List<RawTypeAnnotationInfo> runtimeVisibleTypeAnnotations,
            List<RawTypeAnnotationInfo> runtimeInvisibleTypeAnnotations,
            List<RawFieldInfo> fields,
            List<RawMethodInfo> methods,
            List<RawInnerClassInfo> innerClasses,
            RawEnclosingMethodInfo enclosingMethod,
            String nestHost,
            List<String> nestMembers,
            List<RawRecordComponentInfo> recordComponents,
            List<String> permittedSubclasses
    ) {
    }

    record RawFieldInfo(
            String name,
            String descriptor,
            int accessFlags,
            String signature,
            List<RawAnnotationInfo> runtimeVisibleAnnotations,
            List<RawAnnotationInfo> runtimeInvisibleAnnotations,
            List<RawTypeAnnotationInfo> runtimeVisibleTypeAnnotations,
            List<RawTypeAnnotationInfo> runtimeInvisibleTypeAnnotations
    ) {
    }

    record RawMethodInfo(
            String name,
            String descriptor,
            int accessFlags,
            String signature,
            List<String> exceptions,
            List<RawMethodParameterInfo> methodParameters,
            List<RawAnnotationInfo> runtimeVisibleAnnotations,
            List<RawAnnotationInfo> runtimeInvisibleAnnotations,
            List<RawTypeAnnotationInfo> runtimeVisibleTypeAnnotations,
            List<RawTypeAnnotationInfo> runtimeInvisibleTypeAnnotations,
            List<List<RawAnnotationInfo>> runtimeVisibleParameterAnnotations,
            List<List<RawAnnotationInfo>> runtimeInvisibleParameterAnnotations,
            String annotationDefault
    ) {
    }

    record RawMethodParameterInfo(String name, int accessFlags) {
    }

    record RawAnnotationInfo(String descriptor, Map<String, String> values) {
    }

    record RawTypeAnnotationInfo(int targetType, RawAnnotationInfo annotation) {
    }

    record RawInnerClassInfo(
            String innerClassInternalName,
            String outerClassInternalName,
            String innerSimpleName,
            int accessFlags
    ) {
    }

    record RawEnclosingMethodInfo(String ownerInternalName, String methodName, String methodDescriptor) {
    }

    record RawRecordComponentInfo(
            String name,
            String descriptor,
            String signature,
            List<RawAnnotationInfo> runtimeVisibleAnnotations,
            List<RawAnnotationInfo> runtimeInvisibleAnnotations,
            List<RawTypeAnnotationInfo> runtimeVisibleTypeAnnotations,
            List<RawTypeAnnotationInfo> runtimeInvisibleTypeAnnotations
    ) {
    }

    private record NameAndType(String name, String descriptor) {
    }

    private static final class ConstantPool {
        private final int[] tags;
        private final Object[] values;

        private ConstantPool(final int[] tags, final Object[] values) {
            this.tags = tags;
            this.values = values;
        }

        private static ConstantPool read(final DataInputStream input) throws IOException {
            final int count = input.readUnsignedShort();
            final int[] tags = new int[count];
            final Object[] values = new Object[count];
            for (int index = 1; index < count; index++) {
                final int tag = input.readUnsignedByte();
                tags[index] = tag;
                switch (tag) {
                    case 1 -> values[index] = input.readUTF();
                    case 3, 4 -> values[index] = input.readInt();
                    case 5, 6 -> {
                        values[index] = input.readLong();
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> values[index] = input.readUnsignedShort();
                    case 9, 10, 11, 12, 18 -> {
                        final int left = input.readUnsignedShort();
                        final int right = input.readUnsignedShort();
                        values[index] = new int[]{left, right};
                    }
                    case 15 -> {
                        final int kind = input.readUnsignedByte();
                        final int reference = input.readUnsignedShort();
                        values[index] = new int[]{kind, reference};
                    }
                    default -> throw new IOException("Unsupported constant pool tag: " + tag);
                }
            }
            return new ConstantPool(tags, values);
        }

        private String utf8(final int index) {
            if (index <= 0 || index >= values.length) {
                return null;
            }
            if (tags[index] != 1) {
                return null;
            }
            return (String) values[index];
        }

        private String className(final int classIndex) {
            if (classIndex <= 0 || classIndex >= values.length) {
                return null;
            }
            if (tags[classIndex] != 7) {
                return null;
            }
            final int nameIndex = (Integer) values[classIndex];
            return utf8(nameIndex);
        }

        private NameAndType nameAndType(final int index) {
            if (index <= 0 || index >= values.length) {
                return new NameAndType(null, null);
            }
            if (tags[index] != 12) {
                return new NameAndType(null, null);
            }
            final int[] pair = (int[]) values[index];
            return new NameAndType(utf8(pair[0]), utf8(pair[1]));
        }

        private String constantToString(final int index) {
            if (index <= 0 || index >= values.length) {
                return null;
            }
            return switch (tags[index]) {
                case 1 -> (String) values[index];
                case 3, 4, 5, 6 -> String.valueOf(values[index]);
                case 7 -> className(index);
                case 8 -> utf8((Integer) values[index]);
                default -> String.valueOf(values[index]);
            };
        }
    }
}
