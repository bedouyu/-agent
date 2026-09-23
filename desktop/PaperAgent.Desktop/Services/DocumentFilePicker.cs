using Microsoft.Win32;

namespace PaperAgent.Desktop.Services;

/// <summary>把 Windows 文件选择框封装起来，让 ViewModel 不直接依赖界面控件。</summary>
public sealed class DocumentFilePicker
{
    public string? PickPaperSourceFile()
    {
        var dialog = new OpenFileDialog
        {
            Title = "选择论文源文件",
            Filter = "LaTeX 工程或论文 (*.zip;*.tex;*.docx)|*.zip;*.tex;*.docx|LaTeX 工程 (*.zip)|*.zip|LaTeX 源码 (*.tex)|*.tex|Word 文档 (*.docx)|*.docx",
            CheckFileExists = true,
            Multiselect = false
        };

        return dialog.ShowDialog() == true ? dialog.FileName : null;
    }
}
