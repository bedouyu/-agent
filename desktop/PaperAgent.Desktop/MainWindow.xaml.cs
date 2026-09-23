using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using PaperAgent.Desktop.Models;
using PaperAgent.Desktop.ViewModels;

namespace PaperAgent.Desktop;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;

    public MainWindow(MainViewModel viewModel)
    {
        InitializeComponent();
        _viewModel = viewModel;
        DataContext = viewModel;
        Loaded += OnLoaded;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        await _viewModel.LoadProjectsAsync();
    }

    private async void OnPdfPageClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is not Image image
            || image.DataContext is not PdfPagePreview page
            || image.ActualWidth <= 0
            || image.ActualHeight <= 0)
        {
            return;
        }

        // WPF 使用设备无关像素；按页面实际显示比例还原到 PDF 的 point 坐标。
        Point position = e.GetPosition(image);
        double xPoints = position.X / image.ActualWidth * page.WidthPixels * 72d / page.Dpi;
        double yPoints = position.Y / image.ActualHeight * page.HeightPixels * 72d / page.Dpi;

        SyncTexResult? result = await _viewModel.NavigateFromPdfAsync(page, xPoints, yPoints);
        if (result is null)
        {
            return;
        }

        SourceEditor.UpdateLayout();
        int lineIndex = Math.Clamp(result.Line - 1, 0, Math.Max(0, SourceEditor.LineCount - 1));
        int characterIndex = SourceEditor.GetCharacterIndexFromLineIndex(lineIndex);
        int lineLength = SourceEditor.GetLineLength(lineIndex);
        if (characterIndex >= 0)
        {
            SourceEditor.Focus();
            SourceEditor.Select(characterIndex, lineLength);
            SourceEditor.ScrollToLine(lineIndex);
        }
    }
}
