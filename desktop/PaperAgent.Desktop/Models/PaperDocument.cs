namespace PaperAgent.Desktop.Models;

/// <summary>与后端 PaperDocumentResponse 对应的客户端模型。</summary>
public sealed class PaperDocument
{
    public string Id { get; init; } = string.Empty;

    public string ProjectId { get; init; } = string.Empty;

    public string OriginalFileName { get; init; } = string.Empty;

    public string ContentType { get; init; } = string.Empty;

    public long FileSize { get; init; }

    public DateTimeOffset UploadedAt { get; init; }

    public string UploadedAtDisplay => UploadedAt.ToLocalTime().ToString("yyyy-MM-dd HH:mm");

    public string FileSizeDisplay => FileSize switch
    {
        >= 1024 * 1024 => $"{FileSize / 1024d / 1024d:F1} MB",
        >= 1024 => $"{FileSize / 1024d:F1} KB",
        _ => $"{FileSize} B"
    };

    public string FileTypeDisplay => ContentType switch
    {
        "application/zip" => "LaTeX 工程",
        "text/x-tex" => "LaTeX",
        _ => "Word"
    };

    public bool IsLatex => ContentType is "application/zip" or "text/x-tex";
}
