package com.paperagent.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用本机 TeX Live，完成 LaTeX 编译、PDF 页面渲染和 SyncTeX 反向搜索。
 *
 * <p>这里使用 {@link ProcessBuilder} 的参数列表，而不是拼接 Shell 命令，避免文件名中的空格、
 * 中文和特殊字符被错误解释。</p>
 */
@Service
public class LatexCompileService {

    private static final int MAX_LOG_CHARACTERS = 100_000;
    private static final Pattern PREVIEW_FILE = Pattern.compile("page-(\\d+)\\.png");

    private final DocumentStorageService storageService;
    private final Path texLiveBinDirectory;
    private final Duration compileTimeout;
    private final int previewDpi;
    private final Map<Path, Object> workspaceLocks = new ConcurrentHashMap<>();

    public LatexCompileService(
            DocumentStorageService storageService,
            @Value("${app.latex.bin-dir:D:/texlive/texlive/2026/bin/windows}") String texLiveBinDirectory,
            @Value("${app.latex.compile-timeout-seconds:180}") long compileTimeoutSeconds,
            @Value("${app.latex.preview-dpi:144}") int previewDpi
    ) {
        this.storageService = storageService;
        this.texLiveBinDirectory = Path.of(texLiveBinDirectory).toAbsolutePath().normalize();
        this.compileTimeout = Duration.ofSeconds(Math.max(10, compileTimeoutSeconds));
        this.previewDpi = Math.max(72, Math.min(300, previewDpi));
    }

    public LatexCompilationResponse compile(DocumentStorageService.LatexWorkspace workspace) {
        Object lock = workspaceLocks.computeIfAbsent(workspace.root(), ignored -> new Object());
        synchronized (lock) {
            Instant startedAt = Instant.now();
            Path compileLog = workspace.mainFile().getParent().resolve("paper-agent-compile.log");
            ProcessResult result = runTool(
                    "latexmk.exe",
                    List.of(
                            "-xelatex",
                            // 每次由用户点击编译时都至少完整执行一轮，不复用 ZIP 中可能携带的旧 PDF。
                            "-g",
                            "-synctex=1",
                            "-interaction=nonstopmode",
                            "-file-line-error",
                            "-halt-on-error",
                            "-latexoption=-no-shell-escape",
                            workspace.mainFile().getFileName().toString()
                    ),
                    workspace.mainFile().getParent(),
                    compileLog,
                    compileTimeout
            );

            boolean success = !result.timedOut()
                    && result.exitCode() == 0
                    && Files.isRegularFile(workspace.pdfFile());
            int pageCount = 0;
            String combinedLog = result.output();

            if (success) {
                try {
                    pageCount = renderPreview(workspace).pages().size();
                } catch (RuntimeException exception) {
                    success = false;
                    combinedLog = appendLog(combinedLog, "PDF 已生成，但预览生成失败：" + exception.getMessage());
                }
            }

            if (result.timedOut()) {
                combinedLog = appendLog(combinedLog, "编译超过 " + compileTimeout.toSeconds() + " 秒，已停止。");
            }

            String mainFile = toWorkspacePath(workspace, workspace.mainFile());
            return new LatexCompilationResponse(
                    success,
                    result.exitCode(),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    "latexmk + XeLaTeX",
                    mainFile,
                    Files.isRegularFile(workspace.pdfFile()),
                    pageCount,
                    combinedLog,
                    Instant.now()
            );
        }
    }

    public PdfPreviewResponse getPreview(DocumentStorageService.LatexWorkspace workspace) {
        if (!Files.isRegularFile(workspace.pdfFile())) {
            throw new DocumentValidationException("尚未生成 PDF，请先编译 LaTeX 工程");
        }

        List<PdfPageResponse> pages = listPreviewPages(workspace.previewDirectory());
        if (pages.isEmpty()) {
            return renderPreview(workspace);
        }
        return new PdfPreviewResponse(previewDpi, pages);
    }

    public Resource loadPreviewPage(
            DocumentStorageService.LatexWorkspace workspace,
            int pageNumber
    ) {
        if (pageNumber < 1) {
            throw new DocumentValidationException("PDF 页码必须从 1 开始");
        }

        Path image = workspace.previewDirectory().resolve(String.format(Locale.ROOT, "page-%03d.png", pageNumber));
        if (!Files.isRegularFile(image)) {
            throw new DocumentValidationException("请求的 PDF 预览页不存在");
        }
        return storageService.loadWorkspaceFile(workspace, image);
    }

    public Resource loadPdf(DocumentStorageService.LatexWorkspace workspace) {
        if (!Files.isRegularFile(workspace.pdfFile())) {
            throw new DocumentValidationException("尚未生成 PDF，请先编译 LaTeX 工程");
        }
        return storageService.loadWorkspaceFile(workspace, workspace.pdfFile());
    }

    public SourceFileResponse readSource(
            DocumentStorageService.LatexWorkspace workspace,
            String relativePath
    ) {
        Path source = storageService.resolveSourceFile(workspace, relativePath);
        try {
            return new SourceFileResponse(toWorkspacePath(workspace, source), Files.readString(source));
        } catch (IOException exception) {
            throw new DocumentStorageException("读取 LaTeX 源码失败", exception);
        }
    }

    public SyncTexResponse syncFromPdf(
            DocumentStorageService.LatexWorkspace workspace,
            int page,
            double xPoints,
            double yPoints
    ) {
        if (page < 1 || xPoints < 0 || yPoints < 0) {
            throw new DocumentValidationException("PDF 页码和坐标不正确");
        }
        if (!Files.isRegularFile(workspace.pdfFile())) {
            throw new DocumentValidationException("尚未生成 PDF，请先编译 LaTeX 工程");
        }

        String location = String.format(
                Locale.ROOT,
                "%d:%.3f:%.3f:%s",
                page,
                xPoints,
                yPoints,
                workspace.pdfFile()
        );
        Path syncLog = workspace.mainFile().getParent().resolve("paper-agent-synctex.log");
        ProcessResult result = runTool(
                "synctex.exe",
                List.of("edit", "-o", location),
                workspace.mainFile().getParent(),
                syncLog,
                Duration.ofSeconds(20)
        );

        if (result.timedOut() || result.exitCode() != 0) {
            throw new DocumentValidationException("SyncTeX 没有找到对应源码位置，请重新编译后再试");
        }
        return parseSyncTex(workspace, result.output());
    }

    public void openPdf(DocumentStorageService.LatexWorkspace workspace) {
        if (!Files.isRegularFile(workspace.pdfFile())) {
            throw new DocumentValidationException("尚未生成 PDF，请先编译 LaTeX 工程");
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(workspace.pdfFile().toFile());
                return;
            }
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", workspace.pdfFile().toString())
                    .start();
        } catch (IOException | UnsupportedOperationException exception) {
            throw new DocumentStorageException("无法用系统 PDF 阅读器打开文件", exception);
        }
    }

    SyncTexResponse parseSyncTex(
            DocumentStorageService.LatexWorkspace workspace,
            String output
    ) {
        String input = findValue(output, "Input:");
        int line = parsePositiveInteger(findValue(output, "Line:"), "SyncTeX 返回的行号无效");
        int column = parseNonNegativeInteger(findValue(output, "Column:"), 0);
        String context = findValue(output, "Context:");

        Path source = Path.of(input);
        if (!source.isAbsolute()) {
            source = workspace.mainFile().getParent().resolve(source);
        }
        source = source.toAbsolutePath().normalize();
        storageService.resolveSourceFile(workspace, toWorkspacePath(workspace, source));

        return new SyncTexResponse(
                toWorkspacePath(workspace, source),
                line,
                column,
                context
        );
    }

    private PdfPreviewResponse renderPreview(DocumentStorageService.LatexWorkspace workspace) {
        Path previewDirectory = workspace.previewDirectory();
        try {
            Files.createDirectories(previewDirectory);
            try (DirectoryStream<Path> pages = Files.newDirectoryStream(previewDirectory, "page-*.png")) {
                for (Path page : pages) {
                    Files.deleteIfExists(page);
                }
            }
        } catch (IOException exception) {
            throw new DocumentStorageException("清理旧 PDF 预览失败", exception);
        }

        Path previewLog = previewDirectory.resolve("paper-agent-preview.log");
        String outputPattern = previewDirectory.resolve("page-%03d.png").toString();
        ProcessResult result = runTool(
                "rungs.exe",
                List.of(
                        "-dSAFER",
                        "-dBATCH",
                        "-dNOPAUSE",
                        "-sDEVICE=png16m",
                        "-dTextAlphaBits=4",
                        "-dGraphicsAlphaBits=4",
                        "-r" + previewDpi,
                        "-sOutputFile=" + outputPattern,
                        workspace.pdfFile().toString()
                ),
                workspace.pdfFile().getParent(),
                previewLog,
                Duration.ofSeconds(120)
        );

        if (result.timedOut() || result.exitCode() != 0) {
            throw new DocumentStorageException("TeX Live 无法生成 PDF 页面预览：" + result.output(), null);
        }

        List<PdfPageResponse> pages = listPreviewPages(previewDirectory);
        if (pages.isEmpty()) {
            throw new DocumentStorageException("PDF 预览没有生成任何页面", null);
        }
        return new PdfPreviewResponse(previewDpi, pages);
    }

    private List<PdfPageResponse> listPreviewPages(Path previewDirectory) {
        if (!Files.isDirectory(previewDirectory)) {
            return List.of();
        }

        try (var files = Files.list(previewDirectory)) {
            List<Path> pageFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> PREVIEW_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(this::pageNumberFromFile))
                    .toList();

            List<PdfPageResponse> pages = new ArrayList<>(pageFiles.size());
            for (Path pageFile : pageFiles) {
                BufferedImage image = ImageIO.read(pageFile.toFile());
                if (image == null) {
                    throw new DocumentStorageException("无法读取 PDF 预览图片", null);
                }
                pages.add(new PdfPageResponse(
                        pageNumberFromFile(pageFile),
                        image.getWidth(),
                        image.getHeight()
                ));
            }
            return List.copyOf(pages);
        } catch (IOException exception) {
            throw new DocumentStorageException("读取 PDF 预览失败", exception);
        }
    }

    private int pageNumberFromFile(Path path) {
        Matcher matcher = PREVIEW_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private ProcessResult runTool(
            String executableName,
            List<String> arguments,
            Path workingDirectory,
            Path outputFile,
            Duration timeout
    ) {
        Path executable = texLiveBinDirectory.resolve(executableName).normalize();
        if (!executable.startsWith(texLiveBinDirectory) || !Files.isRegularFile(executable)) {
            throw new LatexToolUnavailableException(
                    "没有找到 " + executableName + "，请检查 TeX Live 路径：" + texLiveBinDirectory
            );
        }

        try {
            Files.createDirectories(outputFile.getParent());
            Files.deleteIfExists(outputFile);

            List<String> command = new ArrayList<>(arguments.size() + 1);
            command.add(executable.toString());
            command.addAll(arguments);

            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();

            boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            }

            String output = readLog(outputFile);
            return new ProcessResult(completed ? process.exitValue() : -1, !completed, output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DocumentStorageException("等待 LaTeX 工具时被中断", exception);
        } catch (IOException exception) {
            throw new DocumentStorageException("启动 LaTeX 工具失败：" + executableName, exception);
        }
    }

    private String readLog(Path logFile) throws IOException {
        byte[] bytes = Files.exists(logFile) ? Files.readAllBytes(logFile) : new byte[0];
        String output = new String(bytes, Charset.defaultCharset());
        if (output.length() <= MAX_LOG_CHARACTERS) {
            return output;
        }
        return "……日志过长，仅显示末尾……\n" + output.substring(output.length() - MAX_LOG_CHARACTERS);
    }

    private String appendLog(String original, String message) {
        return (original == null ? "" : original.stripTrailing()) + System.lineSeparator() + message;
    }

    private String findValue(String output, String prefix) {
        for (String line : output.split("\\R")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        throw new DocumentValidationException("SyncTeX 没有返回可用的源码位置");
    }

    private int parsePositiveInteger(String value, String message) {
        try {
            int number = Integer.parseInt(value);
            if (number < 1) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new DocumentValidationException(message);
        }
    }

    private int parseNonNegativeInteger(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String toWorkspacePath(DocumentStorageService.LatexWorkspace workspace, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspace.root())) {
            throw new DocumentValidationException("SyncTeX 返回了工作区之外的文件路径");
        }
        return workspace.root().relativize(normalized).toString().replace('\\', '/');
    }

    private record ProcessResult(int exitCode, boolean timedOut, String output) {
    }
}
