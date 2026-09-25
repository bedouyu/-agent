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
        await _viewModel.LoadAiStatusAsync();
    }

    private async void OnGenerateAiSuggestionClick(object sender, RoutedEventArgs e)
    {
        if (_viewModel.IsAiBusy)
        {
            return;
        }
        string selectedText = SourceEditor.SelectedText;
        if (string.IsNullOrWhiteSpace(selectedText))
        {
            MessageBox.Show(this, "请先在左侧源码中选中需要润色或检查格式的片段。", "选择源码");
            return;
        }
        if (selectedText.Length > _viewModel.AiMaxSelectionCharacters)
        {
            MessageBox.Show(this,
                $"每次最多发送 {_viewModel.AiMaxSelectionCharacters} 个字符，请缩小选区。",
                "选区过长");
            return;
        }

        string mode = (AiModePicker.SelectedItem as ComboBoxItem)?.Tag as string ?? "POLISH";
        MessageBoxResult confirmation = MessageBox.Show(
            this,
            $"将把选中的 {selectedText.Length} 个字符发送给 DeepSeek（{_viewModel.AiSelectedModel}）生成建议。" +
            "如果本地检查通过，还会发送原文与候选建议给审查 Agent 复核。" +
            "最多两次模型调用，可能产生 API 费用；不会自动修改论文文件。\n\n确认继续？",
            "确认发送论文片段",
            MessageBoxButton.YesNo,
            MessageBoxImage.Question);
        if (confirmation != MessageBoxResult.Yes)
        {
            return;
        }

        AiTab.IsSelected = true;
        await _viewModel.GenerateAiSuggestionAsync(selectedText, mode);
    }

    private void OnCopyAiSuggestionClick(object sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrEmpty(_viewModel.AiSuggestedText))
        {
            Clipboard.SetText(_viewModel.AiSuggestedText);
        }
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
