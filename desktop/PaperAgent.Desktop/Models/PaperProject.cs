namespace PaperAgent.Desktop.Models;

/// <summary>与后端 PaperProjectResponse 对应的客户端模型。</summary>
public sealed class PaperProject
{
    public string Id { get; init; } = string.Empty;

    public string Name { get; init; } = string.Empty;

    public string Description { get; init; } = string.Empty;

    public DateTimeOffset CreatedAt { get; init; }

    public string CreatedAtDisplay => CreatedAt.ToLocalTime().ToString("yyyy-MM-dd HH:mm");
}

