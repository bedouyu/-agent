using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using PaperAgent.Desktop.Models;
using PaperAgent.Desktop.Services;

namespace PaperAgent.Desktop.ViewModels;

public sealed class MainViewModel : ObservableObject
{
    private readonly PaperProjectApiClient _apiClient;
    private readonly DocumentFilePicker _filePicker;
    private string _newProjectName = string.Empty;
    private string _newProjectDescription = string.Empty;
    private string _statusMessage = "正在连接本机服务……";
    private string _sourceText = string.Empty;
    private string _sourcePath = "请先选择 LaTeX 文档";
    private string _compilationLog = "尚未编译。";
    private string _pdfPreviewHint = "编译成功后，这里会显示 PDF 页面。";
    private PaperProject? _selectedProject;
    private PaperDocument? _selectedDocument;
    private int _documentLoadVersion;
    private int _latexLoadVersion;

    public MainViewModel(PaperProjectApiClient apiClient, DocumentFilePicker filePicker)
    {
        _apiClient = apiClient;
        _filePicker = filePicker;
        CreateProjectCommand = new AsyncRelayCommand(CreateProjectAsync, CanCreateProject);
        RefreshCommand = new AsyncRelayCommand(LoadProjectsAsync);
        UploadDocumentCommand = new AsyncRelayCommand(UploadDocumentAsync, CanUploadDocument);
        CompileLatexCommand = new AsyncRelayCommand(CompileLatexAsync, CanUseLatexDocument);
        OpenPdfCommand = new AsyncRelayCommand(OpenPdfAsync, CanUseLatexDocument);
    }

    public ObservableCollection<PaperProject> Projects { get; } = [];

    public ObservableCollection<PaperDocument> Documents { get; } = [];

    public ObservableCollection<PdfPagePreview> PdfPages { get; } = [];

    public AsyncRelayCommand CreateProjectCommand { get; }

    public AsyncRelayCommand RefreshCommand { get; }

    public AsyncRelayCommand UploadDocumentCommand { get; }

    public AsyncRelayCommand CompileLatexCommand { get; }

    public AsyncRelayCommand OpenPdfCommand { get; }

    public PaperProject? SelectedProject
    {
        get => _selectedProject;
        set
        {
            if (!SetProperty(ref _selectedProject, value))
            {
                return;
            }

            UploadDocumentCommand.RaiseCanExecuteChanged();
            SelectedDocument = null;
            Documents.Clear();
            int loadVersion = ++_documentLoadVersion;

            if (value is null)
            {
                StatusMessage = "请选择一个项目以查看和上传论文。";
                return;
            }

            _ = LoadDocumentsAsync(value, loadVersion);
        }
    }

    public PaperDocument? SelectedDocument
    {
        get => _selectedDocument;
        set
        {
            if (!SetProperty(ref _selectedDocument, value))
            {
                return;
            }

            CompileLatexCommand.RaiseCanExecuteChanged();
            OpenPdfCommand.RaiseCanExecuteChanged();
            ClearLatexWorkspace();
            int loadVersion = ++_latexLoadVersion;

            if (value?.IsLatex == true && SelectedProject is not null)
            {
                _ = LoadLatexWorkspaceAsync(SelectedProject, value, loadVersion);
            }
        }
    }

    public string NewProjectName
    {
        get => _newProjectName;
        set
        {
            if (SetProperty(ref _newProjectName, value))
            {
                CreateProjectCommand.RaiseCanExecuteChanged();
            }
        }
    }

    public string NewProjectDescription
    {
        get => _newProjectDescription;
        set => SetProperty(ref _newProjectDescription, value);
    }

    public string SourceText
    {
        get => _sourceText;
        private set => SetProperty(ref _sourceText, value);
    }

    public string SourcePath
    {
        get => _sourcePath;
        private set => SetProperty(ref _sourcePath, value);
    }

    public string CompilationLog
    {
        get => _compilationLog;
        private set => SetProperty(ref _compilationLog, value);
    }

    public string PdfPreviewHint
    {
        get => _pdfPreviewHint;
        private set => SetProperty(ref _pdfPreviewHint, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        private set => SetProperty(ref _statusMessage, value);
    }

    public async Task LoadProjectsAsync()
    {
        try
        {
            StatusMessage = "正在读取项目……";
            string? selectedProjectId = SelectedProject?.Id;
            var projects = await _apiClient.GetProjectsAsync();

            Projects.Clear();
            foreach (var project in projects)
            {
                Projects.Add(project);
            }

            SelectedProject = Projects.FirstOrDefault(project => project.Id == selectedProjectId)
                              ?? Projects.FirstOrDefault();

            if (SelectedProject is null)
            {
                StatusMessage = "已连接后端，请先创建一个论文项目。";
            }
        }
        catch (HttpRequestException)
        {
            StatusMessage = "无法连接后端，请先运行 start-backend-local.cmd。";
        }
        catch (TaskCanceledException)
        {
            StatusMessage = "连接后端超时，请检查本机 Java 服务状态。";
        }
    }

    /// <summary>根据 PDF point 坐标定位源码，窗口随后负责滚动并选中对应行。</summary>
    public async Task<SyncTexResult?> NavigateFromPdfAsync(
        PdfPagePreview page,
        double xPoints,
        double yPoints)
    {
        PaperProject? project = SelectedProject;
        PaperDocument? document = SelectedDocument;
        if (project is null || document?.IsLatex != true)
        {
            return null;
        }

        try
        {
            StatusMessage = $"正在定位第 {page.PageNumber} 页对应的源码……";
            SyncTexResult result = await _apiClient.SyncFromPdfAsync(
                project.Id,
                document.Id,
                page.PageNumber,
                xPoints,
                yPoints);
            SourceFileContent source = await _apiClient.GetSourceAsync(
                project.Id,
                document.Id,
                result.SourcePath);

            SourcePath = source.Path;
            SourceText = source.Content;
            StatusMessage = $"已定位到 {result.SourcePath} 第 {result.Line} 行。";
            return result;
        }
        catch (Exception exception) when (exception is HttpRequestException or PaperAgentApiException)
        {
            StatusMessage = $"定位失败：{exception.Message}";
            return null;
        }
    }

    private bool CanCreateProject() => !string.IsNullOrWhiteSpace(NewProjectName);

    private async Task CreateProjectAsync()
    {
        try
        {
            StatusMessage = "正在创建项目……";
            var project = await _apiClient.CreateProjectAsync(
                NewProjectName.Trim(),
                NewProjectDescription.Trim());

            Projects.Insert(0, project);
            NewProjectName = string.Empty;
            NewProjectDescription = string.Empty;
            SelectedProject = project;
            StatusMessage = $"项目“{project.Name}”创建成功。";
        }
        catch (Exception exception) when (exception is HttpRequestException or PaperAgentApiException)
        {
            StatusMessage = $"创建失败：{exception.Message}";
        }
    }

    private bool CanUploadDocument() => SelectedProject is not null;

    private bool CanUseLatexDocument() => SelectedProject is not null && SelectedDocument?.IsLatex == true;

    private async Task UploadDocumentAsync()
    {
        PaperProject? project = SelectedProject;
        if (project is null)
        {
            return;
        }

        string? filePath = _filePicker.PickPaperSourceFile();
        if (filePath is null)
        {
            StatusMessage = "已取消选择文档。";
            return;
        }

        try
        {
            StatusMessage = $"正在上传 {Path.GetFileName(filePath)}……";
            PaperDocument document = await _apiClient.UploadDocumentAsync(project.Id, filePath);

            if (SelectedProject?.Id == project.Id)
            {
                Documents.Insert(0, document);
                SelectedDocument = document;
            }

            StatusMessage = $"文档“{document.OriginalFileName}”上传成功。";
        }
        catch (Exception exception) when (
            exception is HttpRequestException or PaperAgentApiException or IOException)
        {
            StatusMessage = $"上传失败：{exception.Message}";
        }
    }

    private async Task CompileLatexAsync()
    {
        PaperProject? project = SelectedProject;
        PaperDocument? document = SelectedDocument;
        if (project is null || document?.IsLatex != true)
        {
            return;
        }

        try
        {
            StatusMessage = "正在用 XeLaTeX 编译，请稍候……";
            CompilationLog = "正在编译……";
            LatexCompilationResult result = await _apiClient.CompileLatexAsync(project.Id, document.Id);
            CompilationLog = result.Log;

            if (!result.Success)
            {
                StatusMessage = $"编译失败（退出码 {result.ExitCode}），请查看“编译日志”。";
                return;
            }

            await LoadPreviewAsync(project, document);
            StatusMessage = $"编译成功：{result.PageCount} 页，用时 {result.DurationMillis / 1000d:F1} 秒。";
        }
        catch (TaskCanceledException)
        {
            StatusMessage = "编译等待超时，请查看 LaTeX 工程是否卡住。";
        }
        catch (Exception exception) when (exception is HttpRequestException or PaperAgentApiException)
        {
            StatusMessage = $"编译失败：{exception.Message}";
        }
    }

    private async Task OpenPdfAsync()
    {
        PaperProject? project = SelectedProject;
        PaperDocument? document = SelectedDocument;
        if (project is null || document?.IsLatex != true)
        {
            return;
        }

        try
        {
            await _apiClient.OpenPdfAsync(project.Id, document.Id);
            StatusMessage = "已交给系统默认 PDF 阅读器打开。";
        }
        catch (Exception exception) when (exception is HttpRequestException or PaperAgentApiException)
        {
            StatusMessage = $"打开 PDF 失败：{exception.Message}";
        }
    }

    private async Task LoadDocumentsAsync(PaperProject project, int loadVersion)
    {
        try
        {
            StatusMessage = $"正在读取项目“{project.Name}”的文档……";
            var documents = await _apiClient.GetDocumentsAsync(project.Id);

            if (loadVersion != _documentLoadVersion || SelectedProject?.Id != project.Id)
            {
                return;
            }

            Documents.Clear();
            foreach (var document in documents)
            {
                Documents.Add(document);
            }

            SelectedDocument = Documents.FirstOrDefault();
            StatusMessage = $"项目“{project.Name}”共有 {Documents.Count} 个论文源文件。";
        }
        catch (Exception exception) when (exception is HttpRequestException or PaperAgentApiException)
        {
            if (loadVersion == _documentLoadVersion)
            {
                StatusMessage = $"读取文档失败：{exception.Message}";
            }
        }
    }

    private async Task LoadLatexWorkspaceAsync(
        PaperProject project,
        PaperDocument document,
        int loadVersion)
    {
        try
        {
            SourceFileContent source = await _apiClient.GetSourceAsync(project.Id, document.Id);
            if (loadVersion != _latexLoadVersion || SelectedDocument?.Id != document.Id)
            {
                return;
            }

            SourcePath = source.Path;
            SourceText = source.Content;

            try
            {
                await LoadPreviewAsync(project, document);
                StatusMessage = "已载入源码和上次编译的 PDF；点击 PDF 可定位源码。";
            }
            catch (PaperAgentApiException)
            {
                PdfPreviewHint = "尚无 PDF 预览，请点击“编译 LaTeX”。";
            }
        }
        catch (Exception exception) when (exception is HttpRequestException or PaperAgentApiException)
        {
            if (loadVersion == _latexLoadVersion)
            {
                StatusMessage = $"读取 LaTeX 工作区失败：{exception.Message}";
            }
        }
    }

    private async Task LoadPreviewAsync(PaperProject project, PaperDocument document)
    {
        IReadOnlyList<PdfPagePreview> pages = await _apiClient.GetPdfPreviewAsync(project.Id, document.Id);
        if (SelectedProject?.Id != project.Id || SelectedDocument?.Id != document.Id)
        {
            return;
        }

        PdfPages.Clear();
        foreach (PdfPagePreview page in pages)
        {
            PdfPages.Add(page);
        }
        PdfPreviewHint = $"共 {pages.Count} 页。单击正文位置可跳转到对应 LaTeX 行。";
    }

    private void ClearLatexWorkspace()
    {
        PdfPages.Clear();
        SourceText = string.Empty;
        SourcePath = SelectedDocument?.IsLatex == true ? "正在读取源码……" : "请选择 LaTeX 文档";
        CompilationLog = "尚未在本次会话中编译。";
        PdfPreviewHint = SelectedDocument?.IsLatex == true
            ? "正在检查 PDF 预览……"
            : "编译成功后，这里会显示 PDF 页面。";
    }
}
