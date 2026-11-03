import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class ValidatePngAssets {
    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private ValidatePngAssets() {}

    public static void main(String[] args) throws Exception {
        Path repositoryRoot = args.length == 0
            ? Path.of(".").toAbsolutePath().normalize()
            : Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> assets = new ArrayList<>();
        for (String relativeRoot : List.of(
            "app/src/main/res",
            "desktopApp/src/main/resources",
            "web"
        )) {
            Path assetRoot = repositoryRoot.resolve(relativeRoot);
            if (!Files.isDirectory(assetRoot)) continue;
            try (var paths = Files.walk(assetRoot)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .forEach(assets::add);
            }
        }
        assets.sort(Comparator.comparing(Path::toString));
        if (assets.isEmpty()) {
            throw new IOException("No BLINK PNG assets were found under " + repositoryRoot);
        }
        for (Path asset : assets) validate(repositoryRoot, asset);
        System.out.println("Validated " + assets.size() + " BLINK PNG assets.");
    }

    private static void validate(Path repositoryRoot, Path file) throws IOException {
        String displayPath = repositoryRoot.relativize(file).toString().replace('\\', '/');
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            byte[] signature = readBytes(input, PNG_SIGNATURE.length, displayPath, "PNG signature");
            if (!Arrays.equals(signature, PNG_SIGNATURE)) {
                fail(displayPath, "signature is not valid");
            }

            int chunkIndex = 0;
            boolean foundEnd = false;
            while (!foundEnd) {
                int length;
                try {
                    length = input.readInt();
                } catch (EOFException error) {
                    throw invalid(displayPath, "missing IEND chunk", error);
                }
                if (length < 0 || length > Files.size(file)) {
                    fail(displayPath, "invalid chunk length " + length);
                }

                byte[] typeBytes = readBytes(input, 4, displayPath, "chunk type");
                byte[] data = readBytes(input, length, displayPath, "chunk data");
                long storedCrc;
                try {
                    storedCrc = Integer.toUnsignedLong(input.readInt());
                } catch (EOFException error) {
                    throw invalid(displayPath, "truncated chunk CRC", error);
                }
                String chunkType = new String(typeBytes, StandardCharsets.US_ASCII);
                if (chunkIndex == 0 && !"IHDR".equals(chunkType)) {
                    fail(displayPath, "first chunk is " + chunkType + " instead of IHDR");
                }

                CRC32 crc = new CRC32();
                crc.update(typeBytes);
                crc.update(data);
                if (crc.getValue() != storedCrc) {
                    fail(
                        displayPath,
                        chunkType + " CRC mismatch (stored=" + hex(storedCrc) +
                            " calculated=" + hex(crc.getValue()) + ")"
                    );
                }

                if ("IEND".equals(chunkType)) {
                    if (length != 0) fail(displayPath, "IEND chunk must be empty");
                    foundEnd = true;
                }
                chunkIndex += 1;
            }
            if (input.read() != -1) {
                fail(displayPath, "unexpected data after IEND");
            }
        }
        // CRCs only protect the chunk bytes. A CRC-correct PNG can still contain
        // an invalid compressed pixel stream, which crashes Android resource decoding.
        try {
            BufferedImage decoded = ImageIO.read(file.toFile());
            if (decoded == null || decoded.getWidth() < 1 || decoded.getHeight() < 1) {
                fail(displayPath, "pixel data could not be decoded");
            }
            decoded.getRGB(0, 0, decoded.getWidth(), decoded.getHeight(), null, 0, decoded.getWidth());
        } catch (IOException | RuntimeException error) {
            throw invalid(displayPath, "pixel data could not be decoded", error);
        }
    }

    private static byte[] readBytes(
        DataInputStream input,
        int length,
        String displayPath,
        String field
    ) throws IOException {
        byte[] value = new byte[length];
        try {
            input.readFully(value);
        } catch (EOFException error) {
            throw invalid(displayPath, "truncated " + field, error);
        }
        return value;
    }

    private static String hex(long value) {
        return String.format("%08x", value);
    }

    private static void fail(String displayPath, String detail) throws IOException {
        throw invalid(displayPath, detail, null);
    }

    private static IOException invalid(String displayPath, String detail, Throwable cause) {
        String message = "Invalid PNG asset " + displayPath + ": " + detail;
        return cause == null ? new IOException(message) : new IOException(message, cause);
    }
}
