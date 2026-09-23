namespace PaperAgent.Desktop.Models;

/// <summary>应用内 PDF 阅读器中的一页。</summary>
public sealed class PdfPagePreview
{
    public int PageNumber { get; init; }

    public int WidthPixels { get; init; }

    public int HeightPixels { get; init; }

    public int Dpi { get; init; }

    public Uri ImageUri { get; init; } = new("about:blank");

    public string PageTitle => $"第 {PageNumber} 页";
}
