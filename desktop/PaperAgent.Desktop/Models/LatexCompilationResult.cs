namespace PaperAgent.Desktop.Models;

/// <summary>后端一次 LaTeX 编译的完整结果。</summary>
public sealed class LatexCompilationResult
{
    public bool Success { get; init; }

    public int ExitCode { get; init; }

    public long DurationMillis { get; init; }

    public string Engine { get; init; } = string.Empty;

    public string MainFile { get; init; } = string.Empty;

    public bool PdfAvailable { get; init; }

    public int PageCount { get; init; }

    public string Log { get; init; } = string.Empty;

    public DateTimeOffset CompiledAt { get; init; }
}
