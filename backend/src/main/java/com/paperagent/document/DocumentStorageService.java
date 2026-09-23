package com.paperagent.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.ZipException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 负责论文源文件的校验和本地存储。
 *
 * <p>数据库层不直接操作文件，便于以后切换到对象存储或网络磁盘。</p>
 */
@Service
public class DocumentStorageService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String LATEX_CONTENT_TYPE = "text/x-tex";
    public static final String LATEX_PROJECT_CONTENT_TYPE = "application/zip";
    private static final int MAX_ARCHIVE_ENTRIES = 2_000;
    private static final long MAX_EXTRACTED_BYTES = 200L * 1024 * 1024;

    private final Path dataRoot;

    public DocumentStorageService(@Value("${app.data-dir:./data}") String dataDirectory) {
        this.dataRoot = Path.of(dataDirectory).toAbsolutePath().normalize();
    }

    public StoredDocument store(String projectId, String documentId, MultipartFile file) {
        String originalFileName = cleanOriginalFileName(file.getOriginalFilename());
        SourceFileType sourceFileType = detectSourceFileType(file, originalFileName);

        Path documentDirectory = safeResolve(
                Path.of("documents", projectId, documentId).toString()
        );
        Path temporaryFile = documentDirectory.resolve("upload.tmp");
        Path targetFile = documentDirectory.resolve(sourceFileType.storedFileName());

        try {
            Files.createDirectories(documentDirectory);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            validateFileStructure(temporaryFile, sourceFileType);
            moveIntoPlace(temporaryFile, targetFile);
            if (sourceFileType == SourceFileType.LATEX_PROJECT) {
                extractLatexProject(targetFile, documentDirectory.resolve("workspace"));
            } else if (sourceFileType == SourceFileType.LATEX) {
                prepareSingleFileWorkspace(targetFile, documentDirectory.resolve("workspace"));
            }

            String relativePath = dataRoot.relativize(targetFile).toString();
            return new StoredDocument(
                    originalFileName,
                    relativePath,
                    file.getSize(),
                    sourceFileType.contentType()
            );
        } catch (DocumentValidationException exception) {
            deleteQuietly(temporaryFile);
            deleteDirectoryQuietly(documentDirectory);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporaryFile);
            deleteDirectoryQuietly(documentDirectory);
            throw new DocumentStorageException("保存论文源文件失败", exception);
        }
    }

    public Resource load(String relativePath) {
        Path file = safeResolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new DocumentStorageException("数据库记录存在，但本地文档文件不存在", null);
        }

        try {
            return new UrlResource(file.toUri());
        } catch (IOException exception) {
            throw new DocumentStorageException("读取 DOCX 文件失败", exception);
        }
    }

    /**
     * 找到一个可编译的 LaTeX 工作区。
     *
     * <p>ZIP 工程可能带一层中文目录，因此不能假定 main.tex 一定位于 workspace 根目录。</p>
     */
    public LatexWorkspace resolveLatexWorkspace(String relativePath, String contentType) {
        if (!LATEX_CONTENT_TYPE.equals(contentType)
                && !LATEX_PROJECT_CONTENT_TYPE.equals(contentType)) {
            throw new DocumentValidationException("所选文件不是 LaTeX 源码或 LaTeX 工程");
        }

        Path storedFile = safeResolve(relativePath);
        Path documentDirectory = storedFile.getParent();
        Path workspace = documentDirectory.resolve("workspace").normalize();

        try {
            if (LATEX_CONTENT_TYPE.equals(contentType)
                    && !Files.isRegularFile(workspace.resolve("main.tex"))) {
                prepareSingleFileWorkspace(storedFile, workspace);
            }

            Path mainFile = findMainTex(workspace);
            String pdfName = removeExtension(mainFile.getFileName().toString()) + ".pdf";
            return new LatexWorkspace(
                    workspace,
                    mainFile,
                    mainFile.getParent().resolve(pdfName),
                    documentDirectory.resolve("preview")
            );
        } catch (IOException exception) {
            throw new DocumentStorageException("准备 LaTeX 工作区失败", exception);
        }
    }

    public Resource loadWorkspaceFile(LatexWorkspace workspace, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Path documentDirectory = workspace.root().getParent().toAbsolutePath().normalize();
        if (!normalized.startsWith(documentDirectory) || !Files.isRegularFile(normalized)) {
            throw new DocumentValidationException("请求的工作区文件不存在或路径不安全");
        }

        try {
            return new UrlResource(normalized.toUri());
        } catch (IOException exception) {
            throw new DocumentStorageException("读取工作区文件失败", exception);
        }
    }

    public Path resolveSourceFile(LatexWorkspace workspace, String relativePath) {
        Path source = relativePath == null || relativePath.isBlank()
                ? workspace.mainFile()
                : workspace.root().resolve(relativePath).normalize();
        if (!source.startsWith(workspace.root())
                || !Files.isRegularFile(source)
                || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tex")) {
            throw new DocumentValidationException("LaTeX 源文件不存在或路径不安全");
        }
        return source;
    }

    public void deleteQuietly(String relativePath) {
        Path storedFile = safeResolve(relativePath);
        deleteDirectoryQuietly(storedFile.getParent());
    }

    private SourceFileType detectSourceFileType(MultipartFile file, String originalFileName) {
        if (file.isEmpty()) {
            throw new DocumentValidationException("请选择一个非空的论文源文件");
        }

        String lowerName = originalFileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".tex")) {
            return SourceFileType.LATEX;
        }
        if (lowerName.endsWith(".zip")) {
            return SourceFileType.LATEX_PROJECT;
        }
        if (lowerName.endsWith(".docx")) {
            return SourceFileType.DOCX;
        }

        throw new DocumentValidationException("当前支持 .zip LaTeX 工程、.tex 源码和 .docx Word 文档");
    }

    private void validateFileStructure(Path file, SourceFileType sourceFileType) {
        if (sourceFileType == SourceFileType.DOCX) {
            validateDocxStructure(file);
        } else if (sourceFileType == SourceFileType.LATEX_PROJECT) {
            validateLatexProject(file);
        } else {
            validateLatexSource(file);
        }
    }

    private void validateDocxStructure(Path file) {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            boolean hasContentTypes = zipFile.getEntry("[Content_Types].xml") != null;
            boolean hasMainDocument = zipFile.getEntry("word/document.xml") != null;
            if (!hasContentTypes || !hasMainDocument) {
                throw new DocumentValidationException("文件扩展名是 .docx，但内部结构不是有效的 Word 文档");
            }
        } catch (ZipException exception) {
            throw new DocumentValidationException("文件不是有效的 DOCX 压缩包");
        } catch (IOException exception) {
            throw new DocumentStorageException("检查 DOCX 文件结构失败", exception);
        }
    }

    private void validateLatexSource(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (byte value : bytes) {
                if (value == 0) {
                    throw new DocumentValidationException(".tex 文件包含二进制内容，不是有效的 LaTeX 源码");
                }
            }

            // 第一版统一要求 UTF-8，后续可以在项目设置中增加 GBK 等旧编码转换。
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException exception) {
            throw new DocumentValidationException(".tex 文件不是 UTF-8 编码，请先用编辑器转换为 UTF-8");
        } catch (IOException exception) {
            throw new DocumentStorageException("检查 LaTeX 源码失败", exception);
        }
    }

    private void validateLatexProject(Path file) {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            int entryCount = 0;
            boolean hasMainTex = false;
            long declaredSize = 0;

            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new DocumentValidationException("LaTeX 工程文件过多，最多允许 2000 个文件");
                }

                validateArchiveEntryName(entry.getName());
                String normalizedName = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (normalizedName.equals("main.tex") || normalizedName.endsWith("/main.tex")) {
                    hasMainTex = true;
                }

                if (entry.getSize() > 0) {
                    declaredSize += entry.getSize();
                    if (declaredSize > MAX_EXTRACTED_BYTES) {
                        throw new DocumentValidationException("LaTeX 工程解压后不能超过 200 MB");
                    }
                }
            }

            if (!hasMainTex) {
                throw new DocumentValidationException("LaTeX 工程 ZIP 中没有找到 main.tex");
            }
        } catch (ZipException exception) {
            throw new DocumentValidationException("文件不是有效的 ZIP 压缩包");
        } catch (IOException exception) {
            throw new DocumentStorageException("检查 LaTeX 工程失败", exception);
        }
    }

    private void extractLatexProject(Path archive, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        long extractedBytes = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                validateArchiveEntryName(entry.getName());
                Path target = workspace.resolve(entry.getName()).normalize();
                if (!target.startsWith(workspace)) {
                    throw new DocumentValidationException("ZIP 中包含不安全的文件路径");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        extractedBytes += read;
                        if (extractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new DocumentValidationException("LaTeX 工程解压后不能超过 200 MB");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void prepareSingleFileWorkspace(Path source, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        Files.copy(source, workspace.resolve("main.tex"), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path findMainTex(Path workspace) throws IOException {
        if (!Files.isDirectory(workspace)) {
            throw new DocumentValidationException("LaTeX 工作区不存在");
        }

        try (var files = Files.find(
                workspace,
                20,
                (path, attributes) -> attributes.isRegularFile()
                        && path.getFileName().toString().equalsIgnoreCase("main.tex")
        )) {
            return files.sorted(
                            Comparator.comparingInt((Path path) -> workspace.relativize(path).getNameCount())
                                    .thenComparing(Path::toString)
                    )
                    .findFirst()
                    .orElseThrow(() -> new DocumentValidationException("LaTeX 工作区中没有找到 main.tex"));
        }
    }

    private String removeExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void validateArchiveEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("../")
                || normalized.equals("..")) {
            throw new DocumentValidationException("ZIP 中包含不安全的文件路径");
        }
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String cleanOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "document.docx";
        }

        String normalized = originalFileName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private Path safeResolve(String relativePath) {
        Path resolved = dataRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new DocumentStorageException("检测到不安全的文件路径", null);
        }
        return resolved;
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 清理失败不覆盖真正的业务异常。
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !directory.startsWith(dataRoot.resolve("documents"))) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // 清理失败不覆盖真正的业务异常。
        }
    }

    private enum SourceFileType {
        LATEX("source.tex", LATEX_CONTENT_TYPE),
        LATEX_PROJECT("project.zip", LATEX_PROJECT_CONTENT_TYPE),
        DOCX("original.docx", DOCX_CONTENT_TYPE);

        private final String storedFileName;
        private final String contentType;

        SourceFileType(String storedFileName, String contentType) {
            this.storedFileName = storedFileName;
            this.contentType = contentType;
        }

        public String storedFileName() {
            return storedFileName;
        }

        public String contentType() {
            return contentType;
        }
    }

    public record LatexWorkspace(
            Path root,
            Path mainFile,
            Path pdfFile,
            Path previewDirectory
    ) {
    }
}
