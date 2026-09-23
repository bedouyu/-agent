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
    private PaperProject? _selectedProject;
    private int _documentLoadVersion;

    public MainViewModel(PaperProjectApiClient apiClient, DocumentFilePicker filePicker)
    {
        _apiClient = apiClient;
        _filePicker = filePicker;
        CreateProjectCommand = new AsyncRelayCommand(CreateProjectAsync, CanCreateProject);
        RefreshCommand = new AsyncRelayCommand(LoadProjectsAsync);
        UploadDocumentCommand = new AsyncRelayCommand(UploadDocumentAsync, CanUploadDocument);
    }

    public ObservableCollection<PaperProject> Projects { get; } = [];

    public ObservableCollection<PaperDocument> Documents { get; } = [];

    public AsyncRelayCommand CreateProjectCommand { get; }

    public AsyncRelayCommand RefreshCommand { get; }

    public AsyncRelayCommand UploadDocumentCommand { get; }

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

    private bool CanCreateProject()
    {
        return !string.IsNullOrWhiteSpace(NewProjectName);
    }

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

    private bool CanUploadDocument()
    {
        return SelectedProject is not null;
    }

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
            }

            StatusMessage = $"文档“{document.OriginalFileName}”上传成功。";
        }
        catch (Exception exception) when (
            exception is HttpRequestException or PaperAgentApiException or IOException)
        {
            StatusMessage = $"上传失败：{exception.Message}";
        }
    }

    private async Task LoadDocumentsAsync(PaperProject project, int loadVersion)
    {
        try
        {
            StatusMessage = $"正在读取项目“{project.Name}”的文档……";
            var documents = await _apiClient.GetDocumentsAsync(project.Id);

            // 用户可能在请求期间选择了另一个项目，旧结果不应覆盖新项目。
            if (loadVersion != _documentLoadVersion || SelectedProject?.Id != project.Id)
            {
                return;
            }

            Documents.Clear();
            foreach (var document in documents)
            {
                Documents.Add(document);
            }

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
}
