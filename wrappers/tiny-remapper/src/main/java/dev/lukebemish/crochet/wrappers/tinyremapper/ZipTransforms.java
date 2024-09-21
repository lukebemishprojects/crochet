package dev.lukebemish.crochet.wrappers.tinyremapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class ZipTransforms implements OutputConsumerPath.ResourceRemapper {
    @Override
    public boolean canTransform(TinyRemapper remapper, Path relativePath) {
        return transforms.containsKey(relativePath.toString());
    }

    @Override
    public void transform(Path destinationDirectory, Path relativePath, InputStream input, TinyRemapper remapper) throws IOException {
        Path outputFile = destinationDirectory.resolve(relativePath.toString());
        Path outputDir = outputFile.getParent();
        Files.createDirectories(outputDir);
        var transform = transforms.get(relativePath.toString());
        byte[] transformed = transform.apply(input.readAllBytes());
        Files.write(outputFile, transformed);
    }

    interface IoUnaryOperator<T> {
        T apply(T t) throws IOException;
    }

    interface SerDe<T> {
        T deserialize(byte[] bytes) throws IOException;
        byte[] serialize(T t) throws IOException;
    }

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

    private final Map<String, IoUnaryOperator<byte[]>> transforms = new HashMap<>();

    private ZipTransforms() {}

    static ZipTransforms create() {
        return new ZipTransforms();
    }

    ZipTransforms with(Map<String, IoUnaryOperator<byte[]>> additional) {
        transforms.putAll(additional);
        return this;
    }

    <T> ZipTransforms withMapped(Map<String, IoUnaryOperator<T>> additional, SerDe<T> serDe) {
        Map<String, IoUnaryOperator<byte[]>> newTransforms = new HashMap<>();

        for (Map.Entry<String, IoUnaryOperator<T>> entry : additional.entrySet()) {
            if (entry.getValue() != null) {
                newTransforms.put(entry.getKey(), bytes ->
                    serDe.serialize(entry.getValue().apply(serDe.deserialize(bytes)))
                );
            }
        }

        this.transforms.putAll(newTransforms);
        return this;
    }

    ZipTransforms withJson(Map<String, IoUnaryOperator<JsonObject>> additional) {
        withMapped(additional, new SerDe<>() {
            @Override
            public JsonObject deserialize(byte[] bytes) {
                return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
            }

            @Override
            public byte[] serialize(JsonObject jsonObject) {
                return GSON.toJson(jsonObject).getBytes(StandardCharsets.UTF_8);
            }
        });
        return this;
    }

    int execute(Path zipPath) throws IOException {
        int replacedCount = 0;

        var tempFile = Files.createTempFile("crochet", ".zip");

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] data = zip.readAllBytes();
                String name = entry.getName();
                if (transforms.containsKey(name)) {
                    data = transforms.get(name).apply(data);
                    replacedCount++;
                }

                var outEntry = new ZipEntry(name);
                if (entry.getComment() != null) {
                    outEntry.setComment(entry.getComment());
                }
                if (entry.getCreationTime() != null) {
                    outEntry.setCreationTime(entry.getCreationTime());
                }
                if (entry.getExtra() != null) {
                    outEntry.setExtra(entry.getExtra());
                }
                if (entry.getLastAccessTime() != null) {
                    outEntry.setLastAccessTime(entry.getLastAccessTime());
                }
                if (entry.getLastModifiedTime() != null) {
                    outEntry.setLastModifiedTime(entry.getLastModifiedTime());
                }

                out.putNextEntry(outEntry);
                out.write(data);
                out.closeEntry();
            }
        }

        Files.move(tempFile, zipPath, StandardCopyOption.REPLACE_EXISTING);

        return replacedCount;
    }
}
