using System.Net.Http;
using System.Net.Http.Json;
using System.Net.Http.Headers;
using System.IO;
using System.Text.Json;
using PaperAgent.Desktop.Models;

namespace PaperAgent.Desktop.Services;

/// <summary>
/// 集中封装后端 HTTP 接口。ViewModel 不需要关心 URL 和 JSON 细节。
/// </summary>
public sealed class PaperProjectApiClient
{
    private readonly HttpClient _httpClient;

    public PaperProjectApiClient(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<IReadOnlyList<PaperProject>> GetProjectsAsync(
        CancellationToken cancellationToken = default)
    {
        return await _httpClient.GetFromJsonAsync<List<PaperProject>>(
                   "api/projects",
                   cancellationToken)
               ?? [];
    }

    public async Task<PaperProject> CreateProjectAsync(
        string name,
        string description,
        CancellationToken cancellationToken = default)
    {
        var request = new CreateProjectRequest(name, description);
        using var response = await _httpClient.PostAsJsonAsync(
            "api/projects",
            request,
            cancellationToken);

        await EnsureSuccessAsync(response, cancellationToken);

        return await response.Content.ReadFromJsonAsync<PaperProject>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回创建后的项目。 ");
    }

    public async Task<IReadOnlyList<PaperDocument>> GetDocumentsAsync(
        string projectId,
        CancellationToken cancellationToken = default)
    {
        return await _httpClient.GetFromJsonAsync<List<PaperDocument>>(
                   $"api/projects/{projectId}/documents",
                   cancellationToken)
               ?? [];
    }

    public async Task<PaperDocument> UploadDocumentAsync(
        string projectId,
        string filePath,
        CancellationToken cancellationToken = default)
    {
        await using var fileStream = File.OpenRead(filePath);
        using var fileContent = new StreamContent(fileStream);
        string contentType = Path.GetExtension(filePath).ToLowerInvariant() switch
        {
            ".zip" => "application/zip",
            ".tex" => "text/x-tex",
            _ => "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        fileContent.Headers.ContentType = new MediaTypeHeaderValue(contentType);

        using var form = new MultipartFormDataContent();
        form.Add(fileContent, "file", Path.GetFileName(filePath));

        using var response = await _httpClient.PostAsync(
            $"api/projects/{projectId}/documents",
            form,
            cancellationToken);

        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<PaperDocument>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回上传后的文档。 ");
    }

    private static async Task EnsureSuccessAsync(
        HttpResponseMessage response,
        CancellationToken cancellationToken)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        ApiError? error = null;
        try
        {
            error = await response.Content.ReadFromJsonAsync<ApiError>(cancellationToken);
        }
        catch (JsonException)
        {
            // 即使服务器返回的不是 JSON，也给界面一个稳定的错误消息。
        }

        string message = error?.Message ?? $"后端返回错误：{(int)response.StatusCode}";
        throw new PaperAgentApiException(message);
    }

    private sealed record CreateProjectRequest(string Name, string Description);

    private sealed record ApiError(string Code, string Message);
}

public sealed class PaperAgentApiException(string message) : Exception(message);
