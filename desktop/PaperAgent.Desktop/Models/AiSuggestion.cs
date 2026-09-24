namespace PaperAgent.Desktop.Models;

/// <summary>只读建议，不会直接写回论文源文件。</summary>
public sealed class AiSuggestion
{
    public string SourcePath { get; init; } = string.Empty;

    public string OriginalText { get; init; } = string.Empty;

    public string SuggestedTex { get; init; } = string.Empty;

    public string Explanation { get; init; } = string.Empty;

    public string Mode { get; init; } = string.Empty;

    public string Model { get; init; } = string.Empty;
}
