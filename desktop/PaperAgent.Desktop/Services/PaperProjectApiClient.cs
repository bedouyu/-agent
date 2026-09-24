using System.Net.Http;
using System.Net.Http.Json;
using System.Net.Http.Headers;
using System.IO;
using System.Text.Json;
using System.Globalization;
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

    public async Task<LatexCompilationResult> CompileLatexAsync(
        string projectId,
        string documentId,
        CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.PostAsync(
            $"api/projects/{projectId}/documents/{documentId}/compile",
            content: null,
            cancellationToken);

        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<LatexCompilationResult>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回 LaTeX 编译结果。");
    }

    public async Task<IReadOnlyList<PdfPagePreview>> GetPdfPreviewAsync(
        string projectId,
        string documentId,
        CancellationToken cancellationToken = default)
    {
        string endpoint = $"api/projects/{projectId}/documents/{documentId}/preview";
        using var response = await _httpClient.GetAsync(endpoint, cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);

        PdfPreviewManifest manifest =
            await response.Content.ReadFromJsonAsync<PdfPreviewManifest>(cancellationToken)
            ?? throw new InvalidOperationException("后端没有返回 PDF 页面清单。");

        long cacheVersion = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        return manifest.Pages.Select(page => new PdfPagePreview
        {
            PageNumber = page.PageNumber,
            WidthPixels = page.WidthPixels,
            HeightPixels = page.HeightPixels,
            Dpi = manifest.Dpi,
            ImageUri = new Uri(
                _httpClient.BaseAddress!,
                $"api/projects/{projectId}/documents/{documentId}/preview/{page.PageNumber}?v={cacheVersion}")
        }).ToList();
    }

    public async Task<SourceFileContent> GetSourceAsync(
        string projectId,
        string documentId,
        string? path = null,
        CancellationToken cancellationToken = default)
    {
        string query = string.IsNullOrWhiteSpace(path)
            ? string.Empty
            : $"?path={Uri.EscapeDataString(path)}";
        using var response = await _httpClient.GetAsync(
            $"api/projects/{projectId}/documents/{documentId}/source{query}",
            cancellationToken);

        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<SourceFileContent>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回 LaTeX 源码。");
    }

    public async Task<SyncTexResult> SyncFromPdfAsync(
        string projectId,
        string documentId,
        int page,
        double xPoints,
        double yPoints,
        CancellationToken cancellationToken = default)
    {
        string x = xPoints.ToString("0.###", CultureInfo.InvariantCulture);
        string y = yPoints.ToString("0.###", CultureInfo.InvariantCulture);
        string endpoint =
            $"api/projects/{projectId}/documents/{documentId}/synctex?page={page}&x={x}&y={y}";
        using var response = await _httpClient.GetAsync(endpoint, cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);

        return await response.Content.ReadFromJsonAsync<SyncTexResult>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回 SyncTeX 定位结果。");
    }

    public async Task OpenPdfAsync(
        string projectId,
        string documentId,
        CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.PostAsync(
            $"api/projects/{projectId}/documents/{documentId}/open-pdf",
            content: null,
            cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
    }

    public async Task<AiStatus> GetAiStatusAsync(CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.GetAsync("api/ai/status", cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<AiStatus>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回 AI 配置状态。");
    }

    public async Task<AiSuggestion> SuggestLatexAsync(
        string projectId,
        string documentId,
        string sourcePath,
        string selectedText,
        string mode,
        string model,
        CancellationToken cancellationToken = default)
    {
        var request = new AiSuggestionRequest(sourcePath, selectedText, mode, model);
        using var response = await _httpClient.PostAsJsonAsync(
            $"api/ai/projects/{projectId}/documents/{documentId}/suggest",
            request,
            cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<AiSuggestion>(cancellationToken)
               ?? throw new InvalidOperationException("后端没有返回润色建议。");
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

    private sealed record AiSuggestionRequest(
        string SourcePath,
        string SelectedText,
        string Mode,
        string Model);

    private sealed record PdfPreviewManifest(int Dpi, List<PdfPageInfo> Pages);

    private sealed record PdfPageInfo(int PageNumber, int WidthPixels, int HeightPixels);

    private sealed record ApiError(string Code, string Message);
}

public sealed class PaperAgentApiException(string message) : Exception(message);
