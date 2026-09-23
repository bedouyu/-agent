namespace PaperAgent.Desktop.Models;

/// <summary>从 PDF 坐标反向定位到 LaTeX 源码的结果。</summary>
public sealed class SyncTexResult
{
    public string SourcePath { get; init; } = string.Empty;

    public int Line { get; init; }

    public int Column { get; init; }

    public string Context { get; init; } = string.Empty;
}
