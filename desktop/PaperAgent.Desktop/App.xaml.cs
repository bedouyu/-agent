using System.Net.Http;
using System.Windows;
using PaperAgent.Desktop.Services;
using PaperAgent.Desktop.ViewModels;

namespace PaperAgent.Desktop;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // 第一版手动组装依赖，更容易观察对象之间的关系。
        // 项目变大后可以再引入 Microsoft.Extensions.DependencyInjection。
        var httpClient = new HttpClient
        {
            BaseAddress = new Uri("http://127.0.0.1:18080/"),
            Timeout = TimeSpan.FromSeconds(10)
        };

        var apiClient = new PaperProjectApiClient(httpClient);
        var filePicker = new DocumentFilePicker();
        var viewModel = new MainViewModel(apiClient, filePicker);
        var window = new MainWindow(viewModel);
        window.Show();
    }
}
