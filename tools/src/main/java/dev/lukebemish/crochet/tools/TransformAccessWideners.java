package dev.lukebemish.crochet.tools;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.neoforged.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

@CommandLine.Command(
        name = "transform-access-wideners",
        description = "Remap and merge access widener files"
)
class TransformAccessWideners implements Runnable {
    @CommandLine.Option(
        names = {"-i", "--input"},
        description = "Input access widener file",
        arity = "0..*"
    )
    List<Path> inputs;

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Output access transformer file",
        required = true
    )
    Path outputFile;

    @CommandLine.Option(
        names = {"-m", "--mappings"},
        description = "Mappings file",
        required = true
    )
    Path mappingsFile;

    @CommandLine.Option(
        names = {"-t", "--target"},
        description = "File that access wideners target",
        required = true
    )
    File targetFile;

    @Override
    public void run() {
        try (var mappingsStream = Files.newInputStream(mappingsFile);
             var targetZip = new JarFile(targetFile)) {
            IMappingFile mappings = IMappingFile.load(mappingsStream);

            var lines = new ArrayList<String>();
            var atWriter = new AccessWidenerVisitor() {
                enum Visibility {
                    PUBLIC("public"),
                    PROTECTED("protected"),
                    PRIVATE("private"),
                    PACKAGE_PRIVATE("default");

                    private final String alias;

                    Visibility(String alias) {
                        this.alias = alias;
                    }
                }

                record MethodState(Visibility visibility, boolean isStatic, boolean isInterface) {}

                private @Nullable MethodState stateOfMethod(String owner, String targetName, String targetDescriptor) {
                    var entry = targetZip.getEntry(owner + ".class");
                    if (entry == null) {
                        return null;
                    }
                    AtomicReference<Visibility> visibility = new AtomicReference<>();
                    AtomicBoolean isStatic = new AtomicBoolean();
                    AtomicBoolean isInterface = new AtomicBoolean();
                    ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            isInterface.setPlain((access & Opcodes.ACC_INTERFACE) != 0);
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (name.equals(targetName) && descriptor.equals(targetDescriptor)) {
                                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                                    visibility.setPlain(Visibility.PUBLIC);
                                } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
                                    visibility.setPlain(Visibility.PROTECTED);
                                } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
                                    visibility.setPlain(Visibility.PRIVATE);
                                } else {
                                    visibility.setPlain(Visibility.PACKAGE_PRIVATE);
                                }
                                isStatic.setPlain((access & Opcodes.ACC_STATIC) != 0);
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                    };
                    try (var stream = targetZip.getInputStream(entry)) {
                        ClassReader reader = new ClassReader(stream);
                        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return new MethodState(visibility.getPlain(), isStatic.getPlain(), isInterface.getPlain());
                }

                @Override
                public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
                    lines.add(switch (access) {
                        case ACCESSIBLE -> "public "+name.replace('/', '.');
                        case EXTENDABLE -> "public-f "+name.replace('/', '.');
                        case MUTABLE -> throw new UnsupportedOperationException("`mutable` is not a known access type for classes`");
                    });
                }

                @Override
                public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
                    lines.add(switch (access) {
                        case ACCESSIBLE -> "public "+owner.replace('/', '.')+" "+name+" "+descriptor;
                        case EXTENDABLE -> throw new UnsupportedOperationException("`extendable` is not a known access type for fields`");
                        case MUTABLE -> "private-f "+owner.replace('/', '.')+" "+name;
                    });
                }

                @Override
                public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
                    lines.add(switch (access) {
                        case ACCESSIBLE -> {
                            var originalAccess = stateOfMethod(owner, name, descriptor);
                            yield ((originalAccess != null && originalAccess.visibility == Visibility.PRIVATE && !originalAccess.isStatic && !originalAccess.isInterface && !"<init>".equals(name)) ? "public+f " : "public ")+owner.replace('/', '.')+" "+name+descriptor;
                        }
                        case EXTENDABLE -> "protected-f "+owner.replace('/', '.')+" "+name+descriptor;
                        case MUTABLE -> throw new UnsupportedOperationException("`mutable` is not a known access type for methods`");
                    });
                }
            };

            AccessWidenerRemapper awRemapper = new AccessWidenerRemapper(
                atWriter,
                new Remapper() {
                    @Override
                    public String mapDesc(String descriptor) {
                        return mappings.remapDescriptor(descriptor);
                    }

                    @Override
                    public String mapFieldName(String owner, String name, String descriptor) {
                        var iClass = mappings.getClass(owner);
                        if (iClass == null) {
                            return name;
                        }
                        var iField = iClass.getField(name);
                        if (iField == null) {
                            return name;
                        }
                        return iField.getMapped();
                    }

                    @Override
                    public String mapMethodName(String owner, String name, String descriptor) {
                        var iClass = mappings.getClass(owner);
                        if (iClass == null) {
                            return name;
                        }
                        var iMethod = iClass.getMethod(name, descriptor);
                        if (iMethod == null) {
                            return name;
                        }
                        return iMethod.getMapped();
                    }

                    @Override
                    public String map(String internalName) {
                        return mappings.remapClass(internalName);
                    }
                },
                "intermediary",
                "named"
            );

            for (var input : inputs) {
                var bytes = Files.readAllBytes(input);
                var header = AccessWidenerReader.readHeader(bytes);
                AccessWidenerVisitor visitor;
                if (header.getNamespace().equals("intermediary")) {
                    visitor = awRemapper;
                } else {
                    visitor = atWriter;
                }
                AccessWidenerReader reader = new AccessWidenerReader(visitor);
                reader.read(bytes);
            }

            Files.write(outputFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
