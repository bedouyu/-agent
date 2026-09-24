namespace PaperAgent.Desktop.Models;

/// <summary>密钥配置状态；后端不会返回密钥本身。</summary>
public sealed class AiStatus
{
    public bool Configured { get; init; }

    public IReadOnlyList<string> Models { get; init; } = [];

    public int MaxSelectionCharacters { get; init; }
}
