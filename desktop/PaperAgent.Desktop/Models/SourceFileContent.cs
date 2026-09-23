namespace PaperAgent.Desktop.Models;

/// <summary>后端返回的 LaTeX 源文件。</summary>
public sealed class SourceFileContent
{
    public string Path { get; init; } = string.Empty;

    public string Content { get; init; } = string.Empty;
}
